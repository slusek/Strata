/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product.rate;

import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.index.Index;

/**
 * Test.
 */
@Test
public class OvernightCompoundedRateObservationTest {

  public void test_of_noRateCutoff() {
    OvernightCompoundedRateObservation test =
        OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1));
    OvernightCompoundedRateObservation expected = OvernightCompoundedRateObservation.builder()
        .index(USD_FED_FUND)
        .startDate(date(2014, 6, 1))
        .endDate(date(2014, 7, 1))
        .rateCutOffDays(0)
        .build();
    assertEquals(test, expected);
  }

  public void test_of_rateCutoff_0() {
    OvernightCompoundedRateObservation test =
        OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1), 0);
    OvernightCompoundedRateObservation expected = OvernightCompoundedRateObservation.builder()
        .index(USD_FED_FUND)
        .startDate(date(2014, 6, 1))
        .endDate(date(2014, 7, 1))
        .rateCutOffDays(0)
        .build();
    assertEquals(test, expected);
  }

  public void test_of_rateCutoff_2() {
    OvernightCompoundedRateObservation test =
        OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1), 2);
    OvernightCompoundedRateObservation expected = OvernightCompoundedRateObservation.builder()
        .index(USD_FED_FUND)
        .startDate(date(2014, 6, 1))
        .endDate(date(2014, 7, 1))
        .rateCutOffDays(2)
        .build();
    assertEquals(test, expected);
  }

  public void test_of_badDateOrder() {
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 6, 1)));
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 2), date(2014, 6, 1)));
  }

  public void test_of_rateCutoff_negative() {
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1), -1));
  }

  public void test_of_null() {
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(null, date(2014, 6, 1), date(2014, 7, 1)));
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(USD_FED_FUND, null, date(2014, 7, 1)));
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), null));
    assertThrowsIllegalArg(() -> OvernightCompoundedRateObservation.of(null, null, null));
  }

  //-------------------------------------------------------------------------
  public void test_collectIndices() {
    OvernightCompoundedRateObservation test =
        OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1));
    ImmutableSet.Builder<Index> builder = ImmutableSet.builder();
    test.collectIndices(builder);
    assertEquals(builder.build(), ImmutableSet.of(USD_FED_FUND));
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    OvernightCompoundedRateObservation test =
        OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1));
    coverImmutableBean(test);
    OvernightCompoundedRateObservation test2 =
        OvernightCompoundedRateObservation.of(GBP_SONIA, date(2014, 6, 3), date(2014, 7, 3), 3);
    coverBeanEquals(test, test2);
  }

  public void test_serialization() {
    OvernightCompoundedRateObservation test =
        OvernightCompoundedRateObservation.of(USD_FED_FUND, date(2014, 6, 1), date(2014, 7, 1));
    assertSerialization(test);
  }

}
