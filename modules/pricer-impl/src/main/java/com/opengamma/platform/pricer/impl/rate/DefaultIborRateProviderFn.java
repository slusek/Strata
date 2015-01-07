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
import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.rate.IborRate;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.platform.pricer.results.ForwardRateSensitivity;
import com.opengamma.platform.pricer.results.ForwardRateSensitivityLD;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3LD;

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
    return env.indexRate(rate.getIndex(), valuationDate, rate.getFixingDate());
  }

  @Override
  public double[] rate(
      PricingEnvironment[] env, 
      LocalDate valuationDate, 
      IborRate rate, 
      LocalDate startDate, 
      LocalDate endDate) {
    LocalDate fixingDate = rate.getFixingDate();
    IborIndex index = rate.getIndex();
    int nbEnv = env.length;
    double[] iborRates = new double[nbEnv];
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      for (int i = 0; i < nbEnv; i++) {
        OptionalDouble fixedRate = env[i].getTimeSeries(index).get(fixingDate);
        if (fixedRate.isPresent()) {
          iborRates[i] = fixedRate.getAsDouble();
        } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
          throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
        }
      }
    }
    // forward rate
    LocalDate fixingStartDate = index.calculateEffectiveFromFixing(fixingDate);
    LocalDate fixingEndDate = index.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = index.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    for (int i = 0; i < nbEnv; i++) {
      iborRates[i] = env[i].getMulticurve().getSimplyCompoundForwardRate(
          env[i].convert(index),
          env[i].relativeTime(index, valuationDate, fixingStartDate),
          env[i].relativeTime(index, valuationDate, fixingEndDate),
          fixingYearFraction);
    }
    return iborRates;
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

  @Override
  public Pair<Double, MulticurveSensitivity3> rateMulticurveSensitivity3(
      PricingEnvironment env, 
      LocalDate valuationDate, 
      IborRate rate, 
      LocalDate startDate, 
      LocalDate endDate,
      Currency ccy) {
    LocalDate fixingDate = rate.getFixingDate();
    IborIndex index = rate.getIndex();
    com.opengamma.analytics.financial.instrument.index.IborIndex indexConverted = env.convert(index);
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      OptionalDouble fixedRate = env.getTimeSeries(index).get(fixingDate);
      if (fixedRate.isPresent()) {
        return Pair.of(fixedRate.getAsDouble(), new MulticurveSensitivity3());
      } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
        throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
      }
    }
    // forward rate
    LocalDate fixingStartDate = index.calculateEffectiveFromFixing(fixingDate);
    LocalDate fixingEndDate = index.calculateMaturityFromEffective(fixingStartDate);
    double fixingAccrualFactor = index.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    double fixingTime = env.relativeTime(valuationDate, fixingDate);
    double fixingStartTime = env.relativeTime(valuationDate, fixingStartDate);
    double fixingEndTime = env.relativeTime(valuationDate, fixingEndDate);
    double forwardRate = env.getMulticurve().getSimplyCompoundForwardRate(indexConverted, fixingStartTime, fixingEndTime, 
        fixingAccrualFactor);
    final List<ForwardRateSensitivity> forwardRateSensi = new ArrayList<>();
    String curveName = env.getMulticurve().getName(indexConverted);
    forwardRateSensi.add(new ForwardRateSensitivity(curveName, fixingTime, fixingStartTime, fixingEndTime, 
        fixingAccrualFactor, 1.0d, ccy));
    return Pair.of(forwardRate, MulticurveSensitivity3.ofForwardRate(forwardRateSensi));
  }

  @Override
  public Pair<Double, MulticurveSensitivity3LD> rateMulticurveSensitivity3LD(
      PricingEnvironment env, 
      LocalDate valuationDate, 
      IborRate rate, 
      LocalDate startDate, 
      LocalDate endDate,
      Currency ccy) {
    LocalDate fixingDate = rate.getFixingDate();
    IborIndex index = rate.getIndex();
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      OptionalDouble fixedRate = env.getTimeSeries(index).get(fixingDate);
      if (fixedRate.isPresent()) {
        return Pair.of(fixedRate.getAsDouble(), new MulticurveSensitivity3LD());
      } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
        throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
      }
    }
    // forward rate
    double forwardRate = env.indexRate(rate.getIndex(), valuationDate, rate.getFixingDate());
    final List<ForwardRateSensitivityLD> forwardRateSensi = new ArrayList<>();
    forwardRateSensi.add(new ForwardRateSensitivityLD(index, fixingDate, 1.0d, ccy));
    return Pair.of(forwardRate, MulticurveSensitivity3LD.ofForwardRate(forwardRateSensi));
  }

}
