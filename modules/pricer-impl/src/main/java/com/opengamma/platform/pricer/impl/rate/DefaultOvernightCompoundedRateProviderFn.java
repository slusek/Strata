/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.rate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.basics.index.OvernightIndex;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.rate.OvernightCompoundedRate;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;

/**
 * Rate provider implementation for a rate based on a single overnight index that is compounded.
 * <p>
 * The rate provider examines the historic time-series of known rates and the
 * forward curve to determine the effective annualized rate.
 */
public class DefaultOvernightCompoundedRateProviderFn
    implements RateProviderFn<OvernightCompoundedRate> {

  /**
   * Default implementation.
   */
  public static final DefaultOvernightCompoundedRateProviderFn DEFAULT = new DefaultOvernightCompoundedRateProviderFn();

  //-------------------------------------------------------------------------
  @Override
  public double rate(
      PricingEnvironment env,
      LocalDate valuationDate,
      OvernightCompoundedRate rate,
      LocalDate startDate,
      LocalDate endDate) {
    // the time-series contains the value on the fixing date, not the publication date
    if (rate.getRateCutoffDaysOffset() < 0) {
      throw new IllegalArgumentException("Rate cutoff not supported");
    }
    int cutoffOffset = rate.getRateCutoffDaysOffset() > 1 ? rate.getRateCutoffDaysOffset() : 1;
    // if no fixings apply, then only use forward rate
    OvernightIndex index = rate.getIndex();
    final LocalDate firstFixingDate = index.calculateFixingFromEffective(startDate);
    final LocalDate firstPublicationDate = index.calculatePublicationFromFixing(firstFixingDate);
    if (valuationDate.isBefore(firstPublicationDate) && cutoffOffset == 1) {
      return rateFromForwardCurve(env, valuationDate, index, startDate, endDate);
    }
    // fixing periods, based on business days of the index
    final List<LocalDate> fixingDateList = new ArrayList<>();
    final List<Double> noCutOffAccrualFactorList = new ArrayList<>();
    final List<LocalDate> ratePeriodStartDates = new ArrayList<>();
    final List<LocalDate> ratePeriodEndDates = new ArrayList<>();
    LocalDate currentStart = startDate;
    ratePeriodStartDates.add(currentStart);
    fixingDateList.add(firstFixingDate);
    while (currentStart.isBefore(endDate)) {
      LocalDate currentEnd = index.getFixingCalendar().next(currentStart);
      ratePeriodEndDates.add(currentEnd);
      LocalDate nextFixingDate = index.calculateFixingFromEffective(currentEnd);
      fixingDateList.add(nextFixingDate);
      noCutOffAccrualFactorList.add(index.getDayCount().yearFraction(currentStart, currentEnd));
      currentStart = currentEnd;
      ratePeriodStartDates.add(currentStart);
    }

    int nbPeriods = noCutOffAccrualFactorList.size();
    for (int i = 0; i < cutoffOffset - 1; i++) {
      fixingDateList.set(nbPeriods - 1 - i, fixingDateList.get(nbPeriods - cutoffOffset));
    }

    // try accessing fixing time-series
    LocalDateDoubleTimeSeries indexFixingDateSeries = env.getTimeSeries(index);
    int fixedPeriod = 0;
    int publicationLag = index.getPublicationDateOffset();
    double accruedUnitNotional = 1d;
    OptionalDouble fixedRate = OptionalDouble.empty();
    // accrue notional for fixings before today, up to and including yesterday
    while ((fixedPeriod < fixingDateList.size() - 1) &&
        ((fixedPeriod + publicationLag) < fixingDateList.size()) &&
        valuationDate.isAfter(fixingDateList.get(fixedPeriod + publicationLag))) {
      LocalDate currentDate1 = fixingDateList.get(fixedPeriod);
      fixedRate = indexFixingDateSeries.get(currentDate1);
      if (!fixedRate.isPresent()) {
        LocalDate latestDate = indexFixingDateSeries.getLatestDate();
        if (currentDate1.isAfter(latestDate)) {
          throw new OpenGammaRuntimeException("Could not get fixing value of index " + index.getName() +
              " for date " + currentDate1 + ". The last data is available on " + latestDate);
        }
        if (!fixedRate.isPresent()) {
          throw new OpenGammaRuntimeException("Could not get fixing value of index " + index.getName() +
              " for date " + currentDate1);
        }
      }
      accruedUnitNotional *= 1 + noCutOffAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
      fixedPeriod++;
    }
    // accrue notional for fixings for today
    if (fixedPeriod < fixingDateList.size() - 1) {
      fixedRate = indexFixingDateSeries.get(fixingDateList.get(fixedPeriod));
      // Check to see if a fixing is available on current date
      if (fixedRate.isPresent()) {
        accruedUnitNotional *= 1 + noCutOffAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
        fixedPeriod++;
      }
    }
    // use forward curve for remainder
    double fixingAccrualfactor = index.getDayCount().yearFraction(startDate, endDate);
    int refPeriod = nbPeriods - cutoffOffset;
    if (fixedPeriod < fixingDateList.size() - 1) {
      // fixing period is the remaining time of the period
      LocalDate remainingStartDate = index.calculateEffectiveFromFixing(fixingDateList.get(fixedPeriod));
      double start = env.relativeTime(valuationDate, remainingStartDate);
      double end = env.relativeTime(valuationDate, ratePeriodEndDates.get(refPeriod));
      double fixingAccrualFactorLeft = 0.0;
      int loopMax = refPeriod + 1;
      for (int loopperiod = fixedPeriod; loopperiod < loopMax; loopperiod++) {
        fixingAccrualFactorLeft += noCutOffAccrualFactorList.get(loopperiod);
      }
      double observedRate = env.getMulticurve().getSimplyCompoundForwardRate(
          env.convert(index), start, end, fixingAccrualFactorLeft);
      double ratio = 1d + fixingAccrualFactorLeft * observedRate;
      // cutoff part
      double rateCutoff = env.getMulticurve().getSimplyCompoundForwardRate(env.convert(index),
            env.relativeTime(valuationDate, ratePeriodStartDates.get(refPeriod)),
            env.relativeTime(valuationDate, ratePeriodEndDates.get(refPeriod)),
            noCutOffAccrualFactorList.get(refPeriod));
      for (int i = 0; i < cutoffOffset - 1; i++) {
        double accFactorCutoff = noCutOffAccrualFactorList.get(nbPeriods - cutoffOffset + i + 1);
        double ratioCutoff = 1d + accFactorCutoff * rateCutoff;
        ratio *= ratioCutoff;
      }

      return (accruedUnitNotional * ratio - 1d) / fixingAccrualfactor;
    }
    // all fixed
    return (accruedUnitNotional - 1d) / fixingAccrualfactor;
  }

  // dates entirely in the future
  private double rateFromForwardCurve(
      PricingEnvironment env,
      LocalDate valuationDate,
      OvernightIndex index,
      LocalDate startDate,
      LocalDate endDate) {
    
    double fixingStart = env.relativeTime(valuationDate, startDate);
    double fixingEnd = env.relativeTime(valuationDate, endDate);
    double fixingAccrualfactor = index.getDayCount().yearFraction(startDate, endDate);
    double observedRate = env.getMulticurve().getSimplyCompoundForwardRate(
        env.convert(index), fixingStart, fixingEnd, fixingAccrualfactor);
    return observedRate;
  }

  @Override
  public Pair<Double, MulticurveSensitivity> rateMulticurveSensitivity(
      PricingEnvironment env, LocalDate valuationDate, OvernightCompoundedRate rate, LocalDate startDate, LocalDate endDate) {
    return null;
  }

}
