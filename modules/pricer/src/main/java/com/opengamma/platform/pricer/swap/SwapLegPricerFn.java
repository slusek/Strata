/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.swap;

import java.time.LocalDate;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.swap.SwapLeg;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3LD;

/**
 * Pricer for swap legs.
 * <p>
 * Implementations must be immutable and thread-safe functions.
 * 
 * @param <T>  the type of period
 */
public interface SwapLegPricerFn<T extends SwapLeg> {

  /**
   * Calculates the present value of the swap leg.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swapLeg  the swap leg to price
   * @return the present value of the swap
   */
  public abstract double presentValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      T swapLeg);
  
  public abstract double[] presentValue(
      PricingEnvironment[] env,
      LocalDate valuationDate,
      T swapLeg);

  /**
   * Calculates the future value of the swap leg.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swapLeg  the swap leg to price
   * @return the future value of the swap
   */
  public abstract double futureValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      T swapLeg);

  /**
   * Calculates the present value curve sensitivity of the swap leg.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param swapLeg  the swap leg to price
   * @return the present value curve sensitivity
   */
  public abstract Pair<Double, MulticurveSensitivity> presentValueCurveSensitivity(
      PricingEnvironment env,
      LocalDate valuationDate,
      T swapLeg);
  
  public abstract Pair<Double, MulticurveSensitivity3> presentValueCurveSensitivity3(
      PricingEnvironment env,
      LocalDate valuationDate,
      T swapLeg);
  
  public abstract Pair<Double, MulticurveSensitivity3LD> presentValueCurveSensitivity3LD(
      PricingEnvironment env,
      LocalDate valuationDate,
      T swapLeg);

}
