/* Unless explicitly stated otherwise all files in this repository are licensed under the Apache License 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2021 Datadog, Inc.
 */

package com.datadoghq.sketch.ddsketch.store;

class UnboundedSizeDenseStoreTest extends ExhaustiveStoreTest {

  @Override
  Store newStore() {
    return new UnboundedSizeDenseStore();
  }

  @Override
  void testExtremeValues() {
    // UnboundedSizeDenseStore is not meant to be used with values that are extremely far from one
    // another as it would allocate an excessively large array.
  }

  @Override
  void testMergingExtremeValues() {
    // UnboundedSizeDenseStore is not meant to be used with values that are extremely far from one
    // another as it would allocate an excessively large array.
  }
}
