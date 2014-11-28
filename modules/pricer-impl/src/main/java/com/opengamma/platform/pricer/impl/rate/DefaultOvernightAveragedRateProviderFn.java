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
import com.opengamma.basics.index.OvernightIndex;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.finance.rate.OvernightAveragedRate;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.rate.RateProviderFn;

/**
 * Rate provider implementation for a rate based on a single overnight index that is averaged.
 * <p>
 * The rate provider examines the historic time-series of known rates and the
 * forward curve to determine the effective annualized rate.
 */
public class DefaultOvernightAveragedRateProviderFn
    implements RateProviderFn<OvernightAveragedRate> {

  /**
   * Default implementation.
   */
  public static final DefaultOvernightAveragedRateProviderFn DEFAULT = new DefaultOvernightAveragedRateProviderFn();

  //-------------------------------------------------------------------------
  @Override
  public double rate(
      PricingEnvironment env,
      LocalDate valuationDate,
      OvernightAveragedRate rate,
      LocalDate startDate,
      LocalDate endDate) {
    // ?? is endDate a good business day in index.getFixingCalendar() ?
    // the time-series contains the value on the fixing date, not the publication date
    OvernightIndex index = rate.getIndex();
    // fixing periods, based on business days of the index
    final List<LocalDate> fixingDateList = new ArrayList<>(); // Dates on which the fixing take place
    final List<LocalDate> publicationDates = new ArrayList<>(); // Dates on which the fixing is published
    final List<LocalDate> ratePeriodStartDates = new ArrayList<>(); // Dates related to the underlying rate start periods.
    final List<LocalDate> ratePeriodEndDates = new ArrayList<>(); // Dates related to the underlying rate end periods.
    final List<Double> fixingAccrualFactorList = new ArrayList<>();
    int publicationOffset = index.getPublicationDateOffset(); //TODO: is there a check it is 0 or 1?
    int effectiveOffset = index.getEffectiveDateOffset();
    LocalDate currentStart = startDate;
    double accrualFactorTotal = 0.0d;
    while (currentStart.isBefore(endDate)) {
      LocalDate currentEnd = index.getFixingCalendar().next(currentStart);
      LocalDate fixingDate = effectiveOffset == 0 ? currentStart : index.getFixingCalendar().previous(currentStart);
      fixingDateList.add(fixingDate);
      publicationDates.add(publicationOffset == 0 ? fixingDate : index.getFixingCalendar().next(fixingDate));
      ratePeriodStartDates.add(currentStart);
      ratePeriodEndDates.add(currentEnd);
      double accrualFactor = index.getDayCount().yearFraction(currentStart, currentEnd);
      fixingAccrualFactorList.add(accrualFactor);
      currentStart = currentEnd;
      accrualFactorTotal += accrualFactor;
    }
    // dealing with Rate cutoff
    int nbPeriods = fixingAccrualFactorList.size();
    int cutoffOffset = rate.getRateCutoffDaysOffset(); // TODO: check positive or negative, 1 or 0
    // hypothesis: c means c dates with the same rate
    for (int i = 0; i < cutoffOffset - 1; i++) {
      fixingDateList.set(nbPeriods - 1 - i, fixingDateList.get(nbPeriods - cutoffOffset));
      ratePeriodStartDates.set(nbPeriods - 1 - i, ratePeriodStartDates.get(nbPeriods - cutoffOffset));
      ratePeriodEndDates.set(nbPeriods - 1 - i, ratePeriodEndDates.get(nbPeriods - cutoffOffset));
    }

    // try accessing fixing time-series
    LocalDateDoubleTimeSeries indexFixingDateSeries = env.getTimeSeries(index);
    int fixedPeriod = 0;
    double accruedUnitNotional = 0d;
    // accrue notional for publication before valuation, up to and including valuation-1
    while ( (fixedPeriod  < nbPeriods) &&
        valuationDate.isAfter(publicationDates.get(fixedPeriod)) ) {
      LocalDate currentFixing = fixingDateList.get(fixedPeriod);
      OptionalDouble fixedRate = indexFixingDateSeries.get(currentFixing);
      if (!fixedRate.isPresent()) {
        LocalDate latestDate = indexFixingDateSeries.getLatestDate();
        if (currentFixing.isAfter(latestDate)) {
          throw new OpenGammaRuntimeException("Could not get fixing value of index " + index.getName() +
              " for date " + currentFixing + ". The last data is available on " + latestDate);
        }
        if (!fixedRate.isPresent()) {
          throw new OpenGammaRuntimeException("Could not get fixing value of index " + index.getName() +
              " for date " + currentFixing);
        }
      }
      accruedUnitNotional += fixingAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
      fixedPeriod++;
    }
    // accrue notional for publication on valuation
    if (fixedPeriod < nbPeriods) {
      // Check to see if a fixing is available on current date
      OptionalDouble fixedRate = indexFixingDateSeries.get(fixingDateList.get(fixedPeriod));
      if (fixedRate.isPresent()) {
        accruedUnitNotional += fixingAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
        fixedPeriod++;
      }
    }
    // forward rates
    for (int i = fixedPeriod; i < nbPeriods; i++) {
      double ratePeriodStartTime = env.relativeTime(valuationDate, ratePeriodStartDates.get(i));
      double ratePeriodendTime = env.relativeTime(valuationDate, ratePeriodEndDates.get(i));
      double forwardRate = env.getMulticurve().getSimplyCompoundForwardRate(
          env.convert(index), ratePeriodStartTime, ratePeriodendTime, fixingAccrualFactorList.get(i));
      accruedUnitNotional += fixingAccrualFactorList.get(i) * forwardRate;
    }
    // final rate
    return accruedUnitNotional / accrualFactorTotal;
  }

}
