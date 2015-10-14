/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.finance.rate.swap.type;

import static com.opengamma.strata.basics.date.BusinessDayConventions.MODIFIED_FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ACT_360;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.basics.index.OvernightIndices.EUR_EONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.GBP_SONIA;
import static com.opengamma.strata.basics.index.OvernightIndices.JPY_TONAR;
import static com.opengamma.strata.basics.index.OvernightIndices.USD_FED_FUND;
import static com.opengamma.strata.basics.schedule.Frequency.P12M;
import static com.opengamma.strata.basics.schedule.Frequency.TERM;
import static com.opengamma.strata.finance.rate.swap.OvernightAccrualMethod.COMPOUNDED;

import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.schedule.Frequency;

/**
 * Market standard Fixed-Overnight swap conventions.
 * <p>
 * http://www.opengamma.com/sites/default/files/interest-rate-instruments-and-market-conventions.pdf
 */
public final class FixedOvernightSwapConventions {

  /**
   * USD fixed vs Fed Fund OIS swap for terms less than or equal to one year.
   * <p>
   * Both legs pay once at the end and use day count 'Act/360'.
   * The spot date offset is 2 days and the payment date offset is 2 days.
   */
  public static final FixedOvernightSwapConvention USD_FIXED_TERM_FED_FUND_OIS =
      makeConvention(USD_FED_FUND, ACT_360, TERM, 2, 2);

  /**
   * USD fixed vs Fed Fund OIS swap for terms greater than one year.
   * <p>
   * Both legs pay annually and use day count 'Act/360'.
   * The spot date offset is 2 days and the payment date offset is 2 days.
   */
  public static final FixedOvernightSwapConvention USD_FIXED_1Y_FED_FUND_OIS =
      makeConvention(USD_FED_FUND, ACT_360, P12M, 2, 2);

  //-------------------------------------------------------------------------
  /**
   * EUR fixed vs EONIA OIS swap for terms less than or equal to one year.
   * <p>
   * Both legs pay once at the end and use day count 'Act/360'.
   * The spot date offset is 2 days and the payment date offset is 1 day.
   */
  public static final FixedOvernightSwapConvention EUR_FIXED_TERM_EONIA_OIS =
      makeConvention(EUR_EONIA, ACT_360, TERM, 1, 2);

  /**
   * EUR fixed vs EONIA OIS swap for terms greater than one year.
   * <p>
   * Both legs pay annually and use day count 'Act/360'.
   * The spot date offset is 2 days and the payment date offset is 1 day.
   */
  public static final FixedOvernightSwapConvention EUR_FIXED_1Y_EONIA_OIS =
      makeConvention(EUR_EONIA, ACT_360, P12M, 1, 2);

  //-------------------------------------------------------------------------
  /**
   * GBP fixed vs SONIA OIS swap for terms less than or equal to one year.
   * <p>
   * Both legs pay once at the end and use day count 'Act/365F'.
   * The spot date offset is 0 days and there is no payment date offset.
   */
  public static final FixedOvernightSwapConvention GBP_FIXED_TERM_SONIA_OIS =
      makeConvention(GBP_SONIA, ACT_365F, TERM, 0, 0);

  /**
   * GBP fixed vs SONIA OIS swap for terms greater than one year.
   * <p>
   * Both legs pay annually and use day count 'Act/365F'.
   * The spot date offset is 0 days and there is no payment date offset.
   */
  public static final FixedOvernightSwapConvention GBP_FIXED_1Y_SONIA_OIS =
      makeConvention(GBP_SONIA, ACT_365F, P12M, 0, 0);

  //-------------------------------------------------------------------------
  /**
   * JPY fixed vs TONAR OIS swap for terms less than or equal to one year.
   * <p>
   * Both legs pay once at the end and use day count 'Act/365F'.
   * The spot date offset is 2 days and there is no payment date offset.
   */
  public static final FixedOvernightSwapConvention JPY_FIXED_TERM_TONAR_OIS =
      makeConvention(JPY_TONAR, ACT_365F, TERM, 0, 0);

  /**
   * JPY fixed vs TONAR OIS swap for terms greater than one year.
   * <p>
   * Both legs pay annually and use day count 'Act/365F'.
   * The spot date offset is 2 days and there is no payment date offset.
   */
  public static final FixedOvernightSwapConvention JPY_FIXED_1Y_TONAR_OIS =
      makeConvention(JPY_TONAR, ACT_365F, P12M, 0, 0);

  //-------------------------------------------------------------------------
  // build conventions
  private static FixedOvernightSwapConvention makeConvention(
      OvernightIndex index,
      DayCount dayCount,
      Frequency frequency,
      int paymentLag,
      int spotLag) {

    HolidayCalendar calendar = index.getFixingCalendar();
    DaysAdjustment paymentDateOffset = DaysAdjustment.ofBusinessDays(paymentLag, calendar);
    DaysAdjustment spotDateOffset = DaysAdjustment.ofBusinessDays(spotLag, calendar);
    return FixedOvernightSwapConvention.of(
        FixedRateSwapLegConvention.builder()
            .currency(index.getCurrency())
            .dayCount(dayCount)
            .accrualFrequency(frequency)
            .accrualBusinessDayAdjustment(BusinessDayAdjustment.of(MODIFIED_FOLLOWING, calendar))
            .paymentFrequency(frequency)
            .paymentDateOffset(paymentDateOffset)
            .build(),
        OvernightRateSwapLegConvention.builder()
            .index(index)
            .accrualMethod(COMPOUNDED)
            .accrualFrequency(frequency)
            .paymentFrequency(frequency)
            .paymentDateOffset(paymentDateOffset)
            .build(),
        spotDateOffset);
  }

  //-------------------------------------------------------------------------
  /**
   * Restricted constructor.
   */
  private FixedOvernightSwapConventions() {
  }

}
