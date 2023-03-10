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
@BenchmarkMode(Mode.AverageTime)
public class Merge {

  @Param DataGenerator generator;

  @Param({"NANOSECONDS", "MICROSECONDS", "MILLISECONDS"})
  TimeUnit unit;

  @Param DDSketchOption sketchOption;

  @Param("100000")
  int count;

  @Param({"0.01"})
  double relativeAccuracy;

  DDSketch left;
  DDSketch right;

  @Setup(Level.Trial)
  public void init() {
    this.left = sketchOption.create(relativeAccuracy);
    this.right = sketchOption.create(relativeAccuracy);
    for (int i = 0; i < count; ++i) {
      left.accept(unit.toNanos(Math.round(generator.nextValue())));
      right.accept(unit.toNanos(Math.round(generator.nextValue())));
    }
  }

  @Benchmark
  public Object merge() {
    DDSketch target = sketchOption.create(relativeAccuracy);
    target.mergeWith(left);
    target.mergeWith(right);
    return target;
  }
}
