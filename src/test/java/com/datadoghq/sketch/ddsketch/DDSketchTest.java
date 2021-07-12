/* Unless explicitly stated otherwise all files in this repository are licensed under the Apache License 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2021 Datadog, Inc.
 */

package com.datadoghq.sketch.ddsketch;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.datadoghq.sketch.QuantileSketchTest;
import com.datadoghq.sketch.ddsketch.encoding.ByteArrayInput;
import com.datadoghq.sketch.ddsketch.encoding.GrowingByteArrayOutput;
import com.datadoghq.sketch.ddsketch.encoding.Input;
import com.datadoghq.sketch.ddsketch.mapping.BitwiseLinearlyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.mapping.CubicallyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.mapping.IndexMapping;
import com.datadoghq.sketch.ddsketch.mapping.LogarithmicMapping;
import com.datadoghq.sketch.ddsketch.mapping.QuadraticallyInterpolatedMapping;
import com.datadoghq.sketch.ddsketch.store.Store;
import com.datadoghq.sketch.ddsketch.store.StoreTestCase;
import com.datadoghq.sketch.ddsketch.store.UnboundedSizeDenseStore;
import com.datadoghq.sketch.util.accuracy.AccuracyTester;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class DDSketchTest extends QuantileSketchTest<DDSketch> {

  abstract double relativeAccuracy();

  IndexMapping mapping() {
    return new LogarithmicMapping(relativeAccuracy());
  }

  Supplier<Store> storeSupplier() {
    return UnboundedSizeDenseStore::new;
  }

  @Override
  public DDSketch newSketch() {
    return new DDSketch(mapping(), storeSupplier());
  }

  @Override
  protected void assertQuantileAccurate(
      boolean merged, double[] sortedValues, double quantile, double actualQuantileValue) {

    final double lowerQuantileValue =
        sortedValues[(int) Math.floor(quantile * (sortedValues.length - 1))];
    final double upperQuantileValue =
        sortedValues[(int) Math.ceil(quantile * (sortedValues.length - 1))];

    assertAccurate(lowerQuantileValue, upperQuantileValue, actualQuantileValue);
  }

  @Override
  protected void assertMinAccurate(double[] sortedValues, double actualMinValue) {
    assertAccurate(sortedValues[0], actualMinValue);
  }

  @Override
  protected void assertMaxAccurate(double[] sortedValues, double actualMaxValue) {
    assertAccurate(sortedValues[sortedValues.length - 1], actualMaxValue);
  }

  @Override
  protected void assertSumAccurate(double[] sortedValues, double actualSumValue) {
    // The sum is accurate if the values that have been added to the sketch have same sign.
    if (sortedValues[0] >= 0 || sortedValues[sortedValues.length - 1] <= 0) {
      assertAccurate(Arrays.stream(sortedValues).sum(), actualSumValue);
    }
  }

  @Override
  protected void assertAverageAccurate(double[] sortedValues, double actualAverageValue) {
    // The average is accurate if the values that have been added to the sketch have same sign.
    if (sortedValues[0] >= 0 || sortedValues[sortedValues.length - 1] <= 0) {
      assertAccurate(Arrays.stream(sortedValues).average().getAsDouble(), actualAverageValue);
    }
  }

  private void assertAccurate(double minExpected, double maxExpected, double actual) {
    final double relaxedMinExpected =
        minExpected > 0
            ? minExpected * (1 - relativeAccuracy())
            : minExpected * (1 + relativeAccuracy());
    final double relaxedMaxExpected =
        maxExpected > 0
            ? maxExpected * (1 + relativeAccuracy())
            : maxExpected * (1 - relativeAccuracy());

    if (actual < relaxedMinExpected - AccuracyTester.FLOATING_POINT_ACCEPTABLE_ERROR
        || actual > relaxedMaxExpected + AccuracyTester.FLOATING_POINT_ACCEPTABLE_ERROR) {
      fail();
    }
  }

  private void assertAccurate(double expected, double actual) {
    assertAccurate(expected, expected, actual);
  }

  @Test
  void testNegativeConstants() {
    testAdding(0);
    testAdding(-1);
    testAdding(-1, -1, -1);
    testAdding(-10, -10, -10);
    testAdding(IntStream.range(0, 10000).mapToDouble(i -> -2).toArray());
    testAdding(-10, -10, -11, -11, -11);
  }

  @Test
  void testNegativeAndPositiveConstants() {
    testAdding(0);
    testAdding(-1, 1);
    testAdding(-1, -1, -1, 1, 1, 1);
    testAdding(-10, -10, -10, 10, 10, 10);
    testAdding(IntStream.range(0, 20000).mapToDouble(i -> i % 2 == 0 ? 2 : -2).toArray());
    testAdding(-10, -10, -11, -11, -11, 10, 10, 11, 11, 11);
  }

  @Test
  void testWithZeros() {

    testAdding(IntStream.range(0, 100).mapToDouble(i -> 0).toArray());

    testAdding(
        DoubleStream.concat(
                IntStream.range(0, 10).mapToDouble(i -> 0),
                IntStream.range(-100, 100).mapToDouble(i -> i))
            .toArray());

    testAdding(
        DoubleStream.concat(
                IntStream.range(-100, 100).mapToDouble(i -> i),
                IntStream.range(0, 10).mapToDouble(i -> 0))
            .toArray());
  }

  @Test
  void testWithoutZeros() {
    testAdding(
        DoubleStream.concat(
                IntStream.range(-100, -1).mapToDouble(i -> i),
                IntStream.range(1, 100).mapToDouble(i -> i))
            .toArray());
  }

  @Test
  void testNegativeNumbersIncreasingLinearly() {
    testAdding(IntStream.range(-10000, 0).mapToDouble(v -> v).toArray());
  }

  @Test
  void testNegativeAndPositiveNumbersIncreasingLinearly() {
    testAdding(IntStream.range(-10000, 10000).mapToDouble(v -> v).toArray());
  }

  @Test
  void testNegativeNumbersDecreasingLinearly() {
    testAdding(IntStream.range(0, 10000).mapToDouble(v -> -v).toArray());
  }

  @Test
  void testNegativeAndPositiveNumbersDecreasingLinearly() {
    testAdding(IntStream.range(0, 20000).mapToDouble(v -> 10000 - v).toArray());
  }

  @Test
  void testNegativeNumbersIncreasingExponentially() {
    testAdding(IntStream.range(0, 100).mapToDouble(i -> -Math.exp(i)).toArray());
  }

  @Test
  void testNegativeAndPositiveNumbersIncreasingExponentially() {
    testAdding(
        DoubleStream.concat(
                IntStream.range(0, 100).mapToDouble(i -> -Math.exp(i)),
                IntStream.range(0, 100).mapToDouble(Math::exp))
            .toArray());
  }

  @Test
  void testNegativeNumbersDecreasingExponentially() {
    testAdding(IntStream.range(0, 100).mapToDouble(i -> -Math.exp(-i)).toArray());
  }

  @Test
  void testNegativeAndPositiveNumbersDecreasingExponentially() {
    testAdding(
        DoubleStream.concat(
                IntStream.range(0, 100).mapToDouble(i -> -Math.exp(-i)),
                IntStream.range(0, 100).mapToDouble(i -> Math.exp(-i)))
            .toArray());
  }

  @Override
  protected void test(boolean merged, double[] values, DDSketch sketch) {
    assertEncodes(merged, values, sketch);
    try {
      testProtoRoundTrip(merged, values, sketch);
    } catch (InvalidProtocolBufferException e) {
      fail(e);
    }
    testEncodeDecodeRoundTrip(merged, values, sketch);
  }

  void testProtoRoundTrip(boolean merged, double[] values, DDSketch sketch)
      throws InvalidProtocolBufferException {
    assertEncodes(
        merged,
        values,
        DDSketchProtoBinding.fromProto(storeSupplier(), DDSketchProtoBinding.toProto(sketch)));
    assertEncodes(
        merged,
        values,
        DDSketchProtoBinding.fromProto(
            storeSupplier(),
            com.datadoghq.sketch.ddsketch.proto.DDSketch.parseFrom(sketch.serialize())));
  }

  void testEncodeDecodeRoundTrip(
      boolean merged, double[] values, DDSketch sketch, Supplier<Store> finalStoreSupplier) {
    final GrowingByteArrayOutput output = new GrowingByteArrayOutput();
    try {
      sketch.encode(output, false);
    } catch (IOException e) {
      fail(e);
    }
    final Input input = new ByteArrayInput(output.backingArray(), 0, output.numWrittenBytes());
    final DDSketch decoded;
    try {
      decoded = DDSketch.decode(input, finalStoreSupplier);
      assertThat(input.hasRemaining()).isFalse();
    } catch (IOException e) {
      fail(e);
      return;
    }
    assertEncodes(merged, values, decoded);
  }

  void testEncodeDecodeRoundTrip(boolean merged, double[] values, DDSketch sketch) {
    Arrays.stream(StoreTestCase.values())
        .filter(StoreTestCase::isLossless)
        .forEach(
            storeTestCase ->
                testEncodeDecodeRoundTrip(merged, values, sketch, storeTestCase.storeSupplier()));
  }

  @Test
  void testIndexMappingEncodingMismatch() {
    final IndexMapping mapping1 = new QuadraticallyInterpolatedMapping(relativeAccuracy());
    final DDSketch sketch1 = new DDSketch(mapping1, storeSupplier());
    sketch1.accept(0.9);
    final GrowingByteArrayOutput output1 = new GrowingByteArrayOutput();
    try {
      sketch1.encode(output1, false);
    } catch (IOException e) {
      fail(e);
    }

    final IndexMapping mapping2 = new CubicallyInterpolatedMapping(relativeAccuracy());
    final DDSketch sketch2 = new DDSketch(mapping2, storeSupplier());
    final GrowingByteArrayOutput output2 = new GrowingByteArrayOutput();
    sketch2.accept(0.8);
    try {
      sketch2.encode(output2, false);
    } catch (IOException e) {
      fail(e);
    }

    final Input input1 = new ByteArrayInput(output1.backingArray(), 0, output1.numWrittenBytes());
    final DDSketch decoded;
    try {
      decoded = DDSketch.decode(input1, storeSupplier());
      assertThat(input1.hasRemaining()).isFalse();
    } catch (IOException e) {
      fail(e);
      return;
    }
    assertThat(decoded.getIndexMapping().getClass()).isEqualTo(mapping1.getClass());

    final Input input2 = new ByteArrayInput(output2.backingArray(), 0, output2.numWrittenBytes());
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> decoded.decodeAndMergeWith(input2));
  }

  @Test
  void testMissingIndexMappingEncoding() {
    final GrowingByteArrayOutput output1 = new GrowingByteArrayOutput();
    final IndexMapping mapping = mapping();
    try {
      mapping.encode(output1);
    } catch (IOException e) {
      fail(e);
    }

    final Input input1 = new ByteArrayInput(output1.backingArray(), 0, output1.numWrittenBytes());
    try {
      DDSketch.decode(input1, storeSupplier());
      assertThat(input1.hasRemaining()).isFalse();
    } catch (IOException e) {
      fail(e);
      return;
    }

    final Input input2 = new ByteArrayInput(new byte[] {});
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> DDSketch.decode(input2, storeSupplier()));
  }

  @ParameterizedTest
  @MethodSource("values")
  void testConversion(double[] values) {
    final double gamma = (1 + relativeAccuracy()) / (1 - relativeAccuracy());

    final double initialGamma = Math.pow(gamma, 0.3);
    final double initialRelativeAccuracy = (initialGamma - 1) / (initialGamma + 1);
    final IndexMapping initialIndexMapping =
        new BitwiseLinearlyInterpolatedMapping(initialRelativeAccuracy);
    final DDSketch initialSketch = new DDSketch(initialIndexMapping, UnboundedSizeDenseStore::new);
    Arrays.stream(values).forEach(initialSketch);

    final double newGamma = Math.pow(gamma, 0.4); // initialGamma^2 * newGamma <= gamma
    final double newRelativeAccuracy = (newGamma - 1) / (newGamma + 1);
    final IndexMapping newIndexMapping = new LogarithmicMapping(newRelativeAccuracy);
    final DDSketch convertedSketch =
        initialSketch.convert(newIndexMapping, UnboundedSizeDenseStore::new);

    assertEncodes(false, values, convertedSketch);
  }

  static Stream<Arguments> values() {
    return Stream.of(
        arguments(new Object[] {new double[] {0}}),
        arguments(new Object[] {new double[] {-1}}),
        arguments(new Object[] {new double[] {-1, -1, -1}}),
        arguments(new Object[] {new double[] {-1, -1, -1, 1, 1, 1}}),
        arguments(new Object[] {new double[] {-10, -10, -10}}),
        arguments(new Object[] {new double[] {-10, -10, -10, 10, 10, 10}}),
        arguments(new Object[] {IntStream.range(0, 10000).mapToDouble(i -> -2).toArray()}),
        arguments(
            new Object[] {
              IntStream.range(0, 20000).mapToDouble(i -> i % 2 == 0 ? 2 : -2).toArray()
            }),
        arguments(new Object[] {new double[] {-10, -10, -11, -11, -11}}),
        arguments(new Object[] {new double[] {-10, -10, -11, -11, -11, 10, 10, 11, 11, 11}}),
        arguments(new Object[] {IntStream.range(0, 100).mapToDouble(i -> 0).toArray()}),
        arguments(
            new Object[] {
              DoubleStream.concat(
                      IntStream.range(0, 10).mapToDouble(i -> 0),
                      IntStream.range(-100, 100).mapToDouble(i -> i))
                  .toArray()
            }),
        arguments(
            new Object[] {
              DoubleStream.concat(
                      IntStream.range(-100, 100).mapToDouble(i -> i),
                      IntStream.range(0, 10).mapToDouble(i -> 0))
                  .toArray()
            }),
        arguments(
            new Object[] {
              DoubleStream.concat(
                      IntStream.range(-100, -1).mapToDouble(i -> i),
                      IntStream.range(1, 100).mapToDouble(i -> i))
                  .toArray()
            }),
        arguments(new Object[] {IntStream.range(-10000, 0).mapToDouble(v -> v).toArray()}),
        arguments(new Object[] {IntStream.range(-10000, 10000).mapToDouble(v -> v).toArray()}),
        arguments(new Object[] {IntStream.range(0, 10000).mapToDouble(v -> -v).toArray()}),
        arguments(new Object[] {IntStream.range(0, 20000).mapToDouble(v -> 10000 - v).toArray()}),
        arguments(new Object[] {IntStream.range(0, 100).mapToDouble(i -> -Math.exp(i)).toArray()}),
        arguments(
            new Object[] {
              DoubleStream.concat(
                      IntStream.range(0, 100).mapToDouble(i -> -Math.exp(i)),
                      IntStream.range(0, 100).mapToDouble(Math::exp))
                  .toArray()
            }),
        arguments(new Object[] {IntStream.range(0, 100).mapToDouble(i -> -Math.exp(-i)).toArray()}),
        arguments(
            new Object[] {
              DoubleStream.concat(
                      IntStream.range(0, 100).mapToDouble(i -> -Math.exp(-i)),
                      IntStream.range(0, 100).mapToDouble(i -> Math.exp(-i)))
                  .toArray()
            }));
  }

  static class DDSketchTest1 extends DDSketchTest {

    @Override
    double relativeAccuracy() {
      return 1e-1;
    }
  }

  static class DDSketchTest2 extends DDSketchTest {

    @Override
    double relativeAccuracy() {
      return 1e-2;
    }
  }

  static class DDSketchTest3 extends DDSketchTest {

    @Override
    double relativeAccuracy() {
      return 1e-3;
    }
  }
}
