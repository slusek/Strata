/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.opengamma.platform.finance.rate.FixedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.rate.RateProviderFn;

/**
 * Test {@link DefaultRateProviderFn}. Tests only the FixedRate. The other rates have their own individual test.
 */
@Test
public class DefaultRateProviderFnTest {
  
  /* Rate */
  private static final double RATE = 0.0125;
  private static final FixedRate FIXED_RATE = FixedRate.of(RATE);
  /* Provider */
  private static final RateProviderFn<Rate> RATE_PROVIDER = DefaultRateProviderFn.DEFAULT;
  /* Constants */
  private static final double TOLERANCE_RATE = 1.0E-10;
  
  @Test
  public void rate() {
    double rateComputed = RATE_PROVIDER.rate(null, null, FIXED_RATE, null, null);
    assertEquals(RATE, rateComputed, TOLERANCE_RATE,
        "DefaultRateProviderFn: fixed rate");
  }
  
}
