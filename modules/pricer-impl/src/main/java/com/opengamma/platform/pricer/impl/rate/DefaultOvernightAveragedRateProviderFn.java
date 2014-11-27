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
import com.opengamma.util.ArgumentChecker;

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
    final List<LocalDate> publicationDateList = new ArrayList<>(); // Dates on which the fixing is published
    final List<LocalDate> ratePeriodStartList = new ArrayList<>(); // Dates related to the underlying rate start periods.
    final List<LocalDate> ratePeriodEndList = new ArrayList<>(); // Dates related to the underlying rate end periods.
    final List<Double> fixingAccrualFactorList = new ArrayList<>();
    int publicationOffset = index.getPublicationDateOffset(); //TODO: is there are check it is 0 or 1?
    LocalDate currentStart = startDate;
    while (currentStart.isBefore(endDate)) {
      LocalDate currentEnd = index.getFixingCalendar().next(currentStart);
      fixingDateList.add(currentStart);
      publicationDateList.add(publicationOffset == 0 ? currentStart : currentEnd);
      ratePeriodStartList.add(currentStart); // TODO: review T/N rates
      ratePeriodEndList.add(currentEnd);
      fixingAccrualFactorList.add(index.getDayCount().yearFraction(currentStart, currentEnd));
      currentStart = currentEnd;
    }
    // dealing with Rate cutoff
    int nbPeriods = fixingAccrualFactorList.size();
    int cutoffOffset = rate.getRateCutoffDaysOffset();
    for(int i = 0; i < nbPeriods; i++) {
      fixingDateList.set(nbPeriods-1-i, fixingDateList.get(nbPeriods-1-cutoffOffset));
      ratePeriodStartList.set(nbPeriods-1-i, ratePeriodStartList.get(nbPeriods-1-cutoffOffset));
      ratePeriodEndList.set(nbPeriods-1-i, ratePeriodEndList.get(nbPeriods-1-cutoffOffset));
    }

    // try accessing fixing time-series
    LocalDateDoubleTimeSeries indexFixingDateSeries = env.getTimeSeries(index);
    int fixedPeriod = 0;
    double accruedUnitNotional = 1d;
    // accrue notional for publication before valuation, up to and including valuation-1
    while ( (fixedPeriod  < nbPeriods) &&
        valuationDate.isAfter(publicationDateList.get(fixedPeriod)) ) {
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
        accruedUnitNotional *= 1 + fixingAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
        fixedPeriod++;
      }
    }

    // TODO
    return 1d;
  }

}
