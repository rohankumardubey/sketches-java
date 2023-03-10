/* Unless explicitly stated otherwise all files in this repository are licensed under the Apache License 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2021 Datadog, Inc.
 */

package com.datadoghq.sketch.ddsketch.benchmarks;

import com.datadoghq.sketch.ddsketch.DDSketch;
import com.datadoghq.sketch.ddsketch.DDSketchOption;
import com.datadoghq.sketch.ddsketch.DataGenerator;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class AcceptValue {

  @Param DataGenerator generator;

  @Param({"NANOSECONDS", "MICROSECONDS", "MILLISECONDS"})
  TimeUnit unit;

  @Param DDSketchOption sketchOption;

  @Param("20")
  int logCount;

  @Param({"0.01"})
  double relativeAccuracy;

  DDSketch sketch;
  private long[] data;
  int position = 0;

  @Setup(Level.Trial)
  public void init() {
    this.sketch = sketchOption.create(relativeAccuracy);
    this.data = new long[1 << logCount];
    for (int i = 0; i < data.length; ++i) {
      data[i] = unit.toNanos(Math.round(generator.nextValue()));
    }
  }

  @Benchmark
  public Object accept() {
    sketch.accept(nextValue());
    // blackhole the sketch to avoid elimination of accept
    return sketch;
  }

  private long nextValue() {
    return data[(position++) & (data.length - 1)];
  }
}
