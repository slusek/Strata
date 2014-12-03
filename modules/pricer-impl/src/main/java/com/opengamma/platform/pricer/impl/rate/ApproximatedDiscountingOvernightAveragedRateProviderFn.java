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
import com.opengamma.basics.date.BusinessDayConvention;
import com.opengamma.basics.date.BusinessDayConventions;
import com.opengamma.basics.date.HolidayCalendar;
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
public class ApproximatedDiscountingOvernightAveragedRateProviderFn
    implements RateProviderFn<OvernightAveragedRate> {

  /**
   * Default implementation.
   */
  public static final ApproximatedDiscountingOvernightAveragedRateProviderFn APPROXIMATED_DISCOUNTING = 
      new ApproximatedDiscountingOvernightAveragedRateProviderFn();
  
  public static final BusinessDayConvention FOLLOWING = BusinessDayConventions.FOLLOWING;

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
    HolidayCalendar calendar = index.getFixingCalendar();
    int effectiveOffset = index.getEffectiveDateOffset();
    int cutoffOffset = rate.getRateCutoffDaysOffset();
    if(valuationDate.isBefore(effectiveOffset == 0 ? startDate : index.getFixingCalendar().previous(startDate))) {
      // No fixing to be analyzed. Go directly to approximation and cut-off.
      // Cut-off part.
      LocalDate ratePeriodEndDate = FOLLOWING.adjust(endDate, calendar);
      double accrualFactorTotal = index.getDayCount().yearFraction(startDate, ratePeriodEndDate);
      final List<LocalDate> ratePeriodStartDates = new ArrayList<>(); // Dates related to the underlying rate start periods.
      final List<LocalDate> ratePeriodEndDates = new ArrayList<>(); // Dates related to the underlying rate end periods.
      final List<Double> noCutOffAccrualFactorList = new ArrayList<>();
      LocalDate currentEnd = ratePeriodEndDate;
      LocalDate currentStart = startDate;
      for (int i = 0; i < cutoffOffset; i++) {
        ratePeriodEndDates.add(currentEnd);
        currentStart = calendar.previous(currentEnd);
        ratePeriodStartDates.add(currentStart);
        double accrualFactor = index.getDayCount().yearFraction(currentStart, currentEnd);
        noCutOffAccrualFactorList.add(accrualFactor);
        currentEnd = currentStart;
      }
      // Approximated part
      LocalDate rateNoCutoffPeriodEndDate = ratePeriodEndDates.get(cutoffOffset - 1);
      double ratePeriodStartTime = env.relativeTime(valuationDate, startDate);
      double ratePeriodendTime = env.relativeTime(valuationDate, rateNoCutoffPeriodEndDate);
      double remainingFixingAccrualFactor = index.getDayCount().yearFraction(startDate, rateNoCutoffPeriodEndDate);
      double forwardRate = env.getMulticurve().getSimplyCompoundForwardRate(
          env.convert(index), ratePeriodStartTime, ratePeriodendTime, remainingFixingAccrualFactor);
      double accruedUnitNotional = Math.log(1.0 + forwardRate * remainingFixingAccrualFactor);
      // Cut-off part
      double ratePeriodStartTimeCutoff = env.relativeTime(valuationDate, ratePeriodStartDates.get(cutoffOffset - 1));
      double ratePeriodendTimeCutoff = env.relativeTime(valuationDate, ratePeriodEndDates.get(cutoffOffset - 1));
      double forwardRateCutoff = env.getMulticurve().getSimplyCompoundForwardRate(env.convert(index), 
          ratePeriodStartTimeCutoff, ratePeriodendTimeCutoff, noCutOffAccrualFactorList.get(cutoffOffset - 1));
      for (int i = 0; i < cutoffOffset - 1; i++) {
        accruedUnitNotional += noCutOffAccrualFactorList.get(i) * forwardRateCutoff;
      }
      // final rate
      return accruedUnitNotional / accrualFactorTotal;
    }
    // fixing periods, based on business days of the index
    final List<LocalDate> fixingDateList = new ArrayList<>(); // Dates on which the fixing take place
    final List<LocalDate> publicationDates = new ArrayList<>(); // Dates on which the fixing is published
    final List<LocalDate> ratePeriodStartDates = new ArrayList<>(); // Dates related to the underlying rate start periods.
    final List<LocalDate> ratePeriodEndDates = new ArrayList<>(); // Dates related to the underlying rate end periods.
    final List<Double> noCutOffAccrualFactorList = new ArrayList<>();
    int publicationOffset = index.getPublicationDateOffset(); //TODO: is there a check it is 0 or 1?
    LocalDate currentStart = startDate;
    double accrualFactorTotal = 0.0d;
    while (currentStart.isBefore(endDate)) {
      LocalDate currentEnd = calendar.next(currentStart);
      LocalDate fixingDate = effectiveOffset == 0 ? currentStart : calendar.previous(currentStart);
      fixingDateList.add(fixingDate);
      publicationDates.add(publicationOffset == 0 ? fixingDate : calendar.next(fixingDate));
      ratePeriodStartDates.add(currentStart);
      ratePeriodEndDates.add(currentEnd);
      double accrualFactor = index.getDayCount().yearFraction(currentStart, currentEnd);
      noCutOffAccrualFactorList.add(accrualFactor);
      currentStart = currentEnd;
      accrualFactorTotal += accrualFactor;
    }
    // dealing with Rate cutoff
    int nbPeriods = noCutOffAccrualFactorList.size();
    // hypothesis: c means c dates with the same rate
    final List<Double> cutOffAccrualFactorList = new ArrayList<>();
    cutOffAccrualFactorList.addAll(noCutOffAccrualFactorList);
    for (int i = 0; i < cutoffOffset - 1; i++) {
      fixingDateList.set(nbPeriods - 1 - i, fixingDateList.get(nbPeriods - cutoffOffset));
      ratePeriodStartDates.set(nbPeriods - 1 - i, ratePeriodStartDates.get(nbPeriods - cutoffOffset));
      ratePeriodEndDates.set(nbPeriods - 1 - i, ratePeriodEndDates.get(nbPeriods - cutoffOffset));
      cutOffAccrualFactorList.set(nbPeriods - 1 - i, noCutOffAccrualFactorList.get(nbPeriods - cutoffOffset));
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
      accruedUnitNotional += noCutOffAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
      fixedPeriod++;
    }
    // accrue notional for publication on valuation
    if (fixedPeriod < nbPeriods) {
      // Check to see if a fixing is available on current date
      OptionalDouble fixedRate = indexFixingDateSeries.get(fixingDateList.get(fixedPeriod));
      if (fixedRate.isPresent()) {
        accruedUnitNotional += noCutOffAccrualFactorList.get(fixedPeriod) * fixedRate.getAsDouble();
        fixedPeriod++;
      }
    }
    // forward rates if not all fixed and not part of cut-off
    int nbPeriodNotCutOff = nbPeriods - (cutoffOffset - 1);
    if (fixedPeriod < nbPeriodNotCutOff) {
      double ratePeriodStartTime = env.relativeTime(valuationDate, ratePeriodStartDates.get(fixedPeriod));
      double ratePeriodendTime = env.relativeTime(valuationDate, ratePeriodEndDates.get(nbPeriodNotCutOff - 1));
      double remainingFixingAccrualFactor = 0.0d;
      for (int i = fixedPeriod; i < nbPeriodNotCutOff; i++) {
        remainingFixingAccrualFactor += noCutOffAccrualFactorList.get(i);
      }
      double forwardRate = env.getMulticurve().getSimplyCompoundForwardRate(
          env.convert(index), ratePeriodStartTime, ratePeriodendTime, remainingFixingAccrualFactor);
      accruedUnitNotional += Math.log(1.0 + forwardRate * remainingFixingAccrualFactor);
    }
    // Cut-off part if not fixed
    for (int i = Math.max(fixedPeriod, nbPeriodNotCutOff); i < nbPeriods; i++) {
      double ratePeriodStartTime = env.relativeTime(valuationDate, ratePeriodStartDates.get(i));
      double ratePeriodendTime = env.relativeTime(valuationDate, ratePeriodEndDates.get(i));
      double forwardRate = env.getMulticurve().getSimplyCompoundForwardRate(
          env.convert(index), ratePeriodStartTime, ratePeriodendTime, cutOffAccrualFactorList.get(i));
      accruedUnitNotional += noCutOffAccrualFactorList.get(i) * forwardRate;
    }
    // final rate
    return accruedUnitNotional / accrualFactorTotal;
  }

}
