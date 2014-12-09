/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.swap;

import java.time.LocalDate;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyMulticurveSensitivity;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.swap.Swap;
import com.opengamma.platform.pricer.PricingEnvironment;

/**
 * Pricer for swaps.
 * <p>
 * Implementations must be immutable and thread-safe functions.
 */
public interface SwapPricerFn {

  /**
   * Calculates the present value of the swap.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swap  the swap to price
   * @return the present value of the swap
   */
  public abstract MultiCurrencyAmount presentValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      Swap swap);

  /**
   * Calculates the future value of the swap.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swap  the swap to price
   * @return the future value of the swap
   */
  public abstract MultiCurrencyAmount futureValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      Swap swap);
  
  public abstract Pair<MultiCurrencyAmount, MultipleCurrencyMulticurveSensitivity> presentValueCurveSensitivity(
      PricingEnvironment env,
      LocalDate valuationDate,
      Swap swap);

  /**
   * Calculates the par rate of the swap.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swap  the swap to price. The first leg of the swap should be a leg with only fixed rate coupons and gearing 1.
   * @return the par rate
   */
  public abstract double parRate(
      PricingEnvironment env,
      LocalDate valuationDate,
      Swap swap);

  /**
   * Calculates the par spread market quote of the swap. This is the spread to add to the quote of the first leg 
   * to obtain a present value of 0.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swap  the swap to price.
   * @return the par rate
   */
  public abstract double parSpread(
      PricingEnvironment env,
      LocalDate valuationDate,
      Swap swap);

}
