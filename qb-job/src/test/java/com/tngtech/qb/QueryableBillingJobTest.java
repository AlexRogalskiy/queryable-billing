package com.tngtech.qb;

import com.jgrier.flinkstuff.data.DataPoint;
import org.apache.flink.streaming.util.StreamingMultipleProgramsTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@RunWith(MockitoJUnitRunner.class)
public class QueryableBillingJobTest extends StreamingMultipleProgramsTestBase {

  @Spy private QueryableBillingJob job;

  @Test
  public void jobRuns() throws Exception {
    Mockito.when(job.getSource()).thenReturn(job.env.fromCollection(createTestData()));
    job.run();
  }

  private Collection<DataPoint<Long>> createTestData() {
    return LongStream.range(1, 1000)
        .mapToObj(i -> new DataPoint<Long>().withNewValue(i))
        .collect(Collectors.toList());
  }
}
