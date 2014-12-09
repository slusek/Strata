/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.ForwardSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.SimplyCompoundedForwardSensitivity;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.rate.IborRate;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;

/**
 * Rate provider implementation for an IBOR-like index.
 * <p>
 * The rate provider examines the historic time-series of known rates and the
 * forward curve to determine the effective annualized rate.
 */
public class DefaultIborRateProviderFn
    implements RateProviderFn<IborRate> {

  /**
   * Default implementation.
   */
  public static final DefaultIborRateProviderFn DEFAULT = new DefaultIborRateProviderFn();

  //-------------------------------------------------------------------------
  @Override
  public double rate(
      PricingEnvironment env,
      LocalDate valuationDate,
      IborRate rate,
      LocalDate startDate,
      LocalDate endDate) {
    LocalDate fixingDate = rate.getFixingDate();
    IborIndex index = rate.getIndex();
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      OptionalDouble fixedRate = env.getTimeSeries(index).get(fixingDate);
      if (fixedRate.isPresent()) {
        return fixedRate.getAsDouble();
      } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
        throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
      }
    }
    // forward rate
    LocalDate fixingStartDate = index.calculateEffectiveFromFixing(fixingDate);
    LocalDate fixingEndDate = index.calculateMaturityFromEffective(fixingStartDate);
    double fixingAccrualFactor = index.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    return env.getMulticurve().getSimplyCompoundForwardRate(
        env.convert(index),
        env.relativeTime(valuationDate, fixingStartDate),
        env.relativeTime(valuationDate, fixingEndDate),
        fixingAccrualFactor);
  }

  @Override
  public Pair<Double,MulticurveSensitivity> rateMulticurveSensitivity(
      PricingEnvironment env, 
      LocalDate valuationDate, 
      IborRate rate, 
      LocalDate startDate, 
      LocalDate endDate) {
    LocalDate fixingDate = rate.getFixingDate();
    IborIndex index = rate.getIndex();
    com.opengamma.analytics.financial.instrument.index.IborIndex indexConverted = env.convert(index);
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      OptionalDouble fixedRate = env.getTimeSeries(index).get(fixingDate);
      if (fixedRate.isPresent()) {
        return Pair.of(fixedRate.getAsDouble(), new MulticurveSensitivity());
      } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
        throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
      }
    }
    // forward rate
    LocalDate fixingStartDate = index.calculateEffectiveFromFixing(fixingDate);
    LocalDate fixingEndDate = index.calculateMaturityFromEffective(fixingStartDate);
    double fixingAccrualFactor = index.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixingStartTime = env.relativeTime(valuationDate, fixingStartDate);
    double fixingEndTime = env.relativeTime(valuationDate, fixingEndDate);
    double forwardRate = env.getMulticurve().getSimplyCompoundForwardRate(indexConverted, fixingStartTime, fixingEndTime, 
        fixingAccrualFactor);
    final Map<String, List<ForwardSensitivity>> mapFwd = new HashMap<>();
    final List<ForwardSensitivity> listForward = new ArrayList<>();
    listForward.add(new SimplyCompoundedForwardSensitivity(fixingStartTime, fixingEndTime, fixingAccrualFactor, 1.0d));
    mapFwd.put(env.getMulticurve().getName(indexConverted), listForward);
    return Pair.of(forwardRate, MulticurveSensitivity.ofForward(mapFwd));
  }

}
