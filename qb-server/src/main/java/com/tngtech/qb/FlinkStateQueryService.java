package com.tngtech.qb;

import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.JobID;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.runtime.highavailability.HighAvailabilityServices;
import org.apache.flink.runtime.highavailability.HighAvailabilityServicesUtils;
import org.apache.flink.runtime.query.QueryableStateClient;
import org.apache.flink.runtime.query.netty.message.KvStateRequestSerializer;
import org.apache.flink.runtime.state.VoidNamespace;
import org.apache.flink.runtime.state.VoidNamespaceSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.tngtech.qb.Constants.PER_CUSTOMER_STATE_NAME;
import static com.tngtech.qb.Constants.PER_EVENT_TYPE_STATE_NAME;

@Service
@Profile("flink")
public class FlinkStateQueryService implements StateQueryService {
  private final QueryableStateClient client;
  private final JobID jobId;

  public FlinkStateQueryService(
      @Value("${flink.jobIdHex}") String jobIdHex,
      @Value("${flink.configDir}") String flinkConfigDir)
      throws Exception {
    jobId = JobID.fromHexString(jobIdHex);
    final Configuration config = GlobalConfiguration.loadConfiguration(flinkConfigDir);
    final HighAvailabilityServices highAvailabilityServices =
        HighAvailabilityServicesUtils.createHighAvailabilityServices(
            config,
            Executors.newSingleThreadScheduledExecutor(),
            HighAvailabilityServicesUtils.AddressResolution.TRY_ADDRESS_RESOLUTION);
    client = new QueryableStateClient(config, highAvailabilityServices);
  }

  @Override
  public MonthlySubtotalByCategory queryCustomer(final String customer) throws Exception {
    return querySubTotal(customer, PER_CUSTOMER_STATE_NAME);
  }

  @Override
  public MonthlySubtotalByCategory queryType(final String type) throws Exception {
    return querySubTotal(type, PER_EVENT_TYPE_STATE_NAME);
  }

  private MonthlySubtotalByCategory querySubTotal(final String key, final String stateName)
      throws Exception {
    final Future<byte[]> stateFuture =
        client.getKvState(jobId, stateName, key.hashCode(), serializeKey(key));
    final byte[] serializedResult =
        Await.result(stateFuture, new FiniteDuration(10, TimeUnit.SECONDS));
    return deserializeValue(serializedResult);
  }

  private byte[] serializeKey(String key) throws IOException {
    TypeSerializer<String> keySerializer =
        TypeInformation.of(new TypeHint<String>() {}).createSerializer(null);
    return KvStateRequestSerializer.serializeKeyAndNamespace(
        key, keySerializer, VoidNamespace.INSTANCE, VoidNamespaceSerializer.INSTANCE);
  }

  private MonthlySubtotalByCategory deserializeValue(byte[] serializedResult) throws IOException {
    return KvStateRequestSerializer.deserializeValue(
        serializedResult,
        TypeInformation.of(new TypeHint<MonthlySubtotalByCategory>() {})
            .createSerializer(new ExecutionConfig()));
  }
}
