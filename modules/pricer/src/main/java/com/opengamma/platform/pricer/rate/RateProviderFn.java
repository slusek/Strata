/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.rate;

import java.time.LocalDate;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.basics.currency.Currency;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3LD;

/**
 * Provides a rate for a {@code Rate}.
 * <p>
 * The rate will be based on known historic data and forward curves.
 * <p>
 * Implementations must be immutable and thread-safe functions.
 * 
 * @param <T>  the type of rate to be provided
 */
public interface RateProviderFn<T extends Rate> {

  /**
   * Determines the applicable rate for the specified period.
   * <p>
   * Each type of rate has specific rules, encapsulated in {@code Rate}.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param rate  the rate to provide
   * @param startDate  the start date of the period that the rate is applicable for
   * @param endDate  the end date of the period that the rate is applicable for
   * @return the applicable rate
   */
  public abstract double rate(
      PricingEnvironment env,
      LocalDate valuationDate,
      T rate,
      LocalDate startDate,
      LocalDate endDate);

  /**
   * Determines the sensitivity of the rate to the multicurve in teh PricingEnvironment.
   * <p>
   * Each type of rate has specific rules, encapsulated in {@code Rate}.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param rate  the rate to provide
   * @param startDate  the start date of the period that the rate is applicable for
   * @param endDate  the end date of the period that the rate is applicable for
   * @return The sensitivity.
   */
  public abstract Pair<Double,MulticurveSensitivity> rateMulticurveSensitivity(
      PricingEnvironment env,
      LocalDate valuationDate,
      T rate,
      LocalDate startDate,
      LocalDate endDate);
  
  public abstract double[] rate(
      PricingEnvironment[] env,
      LocalDate valuationDate,
      T rate,
      LocalDate startDate,
      LocalDate endDate);
  
  public abstract Pair<Double,MulticurveSensitivity3> rateMulticurveSensitivity3(
      PricingEnvironment env,
      LocalDate valuationDate,
      T rate,
      LocalDate startDate,
      LocalDate endDate,
      Currency currency);
  
  public abstract Pair<Double,MulticurveSensitivity3LD> rateMulticurveSensitivity3LD(
      PricingEnvironment env,
      LocalDate valuationDate,
      T rate,
      LocalDate startDate,
      LocalDate endDate,
      Currency currency);

}
