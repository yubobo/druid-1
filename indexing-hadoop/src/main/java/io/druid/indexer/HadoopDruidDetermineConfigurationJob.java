/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.indexer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.metamx.common.logger.Logger;
import io.druid.timeline.partition.HashBasedNumberedShardSpec;
import io.druid.timeline.partition.NoneShardSpec;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;
import org.joda.time.Interval;

import java.util.List;
import java.util.Map;

/**
 */
public class HadoopDruidDetermineConfigurationJob implements Jobby
{
  private static final Logger log = new Logger(HadoopDruidDetermineConfigurationJob.class);
  private final HadoopDruidIndexerConfig config;

  @Inject
  public HadoopDruidDetermineConfigurationJob(
      HadoopDruidIndexerConfig config
  )
  {
    this.config = config;
  }

  @Override
  public boolean run()
  {
    List<Jobby> jobs = Lists.newArrayList();

    JobHelper.ensurePaths(config);

    if (config.isDeterminingPartitions()) {
      jobs.add(config.getPartitionsSpec().getPartitionJob(config));
    } else {
      int shardsPerInterval = config.getPartitionsSpec().getNumShards();
      Map<DateTime, List<HadoopyShardSpec>> shardSpecs = Maps.newTreeMap(DateTimeComparator.getInstance());
      int shardCount = 0;
      for (Interval segmentGranularity : config.getSegmentGranularIntervals().get()) {
        DateTime bucket = segmentGranularity.getStart();
        if (shardsPerInterval > 0) {
          List<HadoopyShardSpec> specs = Lists.newArrayListWithCapacity(shardsPerInterval);
          for (int i = 0; i < shardsPerInterval; i++) {
            specs.add(
                new HadoopyShardSpec(
                    new HashBasedNumberedShardSpec(i, shardsPerInterval, HadoopDruidIndexerConfig.JSON_MAPPER),
                    shardCount++
                )
            );
          }
          shardSpecs.put(bucket, specs);
          log.info("DateTime[%s], spec[%s]", bucket, specs);
        } else {
          final HadoopyShardSpec spec = new HadoopyShardSpec(new NoneShardSpec(), shardCount++);
          shardSpecs.put(bucket, Lists.newArrayList(spec));
          log.info("DateTime[%s], spec[%s]", bucket, spec);
        }
      }
      config.setShardSpecs(shardSpecs);
    }

    return JobHelper.runJobs(jobs, config);

  }

}
