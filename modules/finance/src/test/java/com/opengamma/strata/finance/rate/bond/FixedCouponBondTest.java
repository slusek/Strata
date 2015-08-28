/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.finance.rate.bond;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.collect.TestHelper.assertSerialization;
import static com.opengamma.strata.collect.TestHelper.coverBeanEquals;
import static com.opengamma.strata.collect.TestHelper.coverImmutableBean;
import static org.testng.Assert.assertEquals;

import java.time.LocalDate;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendars;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.collect.id.StandardId;

/**
 * Test {@link FixedCouponBond}.
 */
@Test
public class FixedCouponBondTest {

  private static final YieldConvention YIELD_CONVENTION = YieldConvention.GERMAN_BONDS;
  private static final StandardId LEGAL_ENTITY = StandardId.of("OG-Ticker", "BUN EUR");
  private static final double NOTIONAL = 1.0e7;
  private static final double FIXED_RATE = 0.015;
  private static final HolidayCalendar EUR_CALENDAR = HolidayCalendars.EUTA;
  private static final DaysAdjustment DATE_OFFSET = DaysAdjustment.ofBusinessDays(3, EUR_CALENDAR);
  private static final DayCount DAY_COUNT = DayCounts.ACT_365F;
  private static final LocalDate START_DATE = LocalDate.of(2015, 4, 12);
  private static final LocalDate END_DATE = LocalDate.of(2025, 4, 12);
  private static final BusinessDayAdjustment BUSINESS_ADJUST =
      BusinessDayAdjustment.of(BusinessDayConventions.MODIFIED_FOLLOWING, EUR_CALENDAR);
  private static final PeriodicSchedule PERIOD_SCHEDULE = PeriodicSchedule.of(
      START_DATE, END_DATE, Frequency.P6M, BUSINESS_ADJUST, StubConvention.SHORT_INITIAL, false);
  private static final int EX_COUPON_DAYS = 5;

  public void test_of() {
    FixedCouponBond test = FixedCouponBond.builder()
        .dayCount(DAY_COUNT)
        .fixedRate(FIXED_RATE)
        .legalEntityId(LEGAL_ENTITY)
        .currency(EUR)
        .notional(NOTIONAL)
        .periodicSchedule(PERIOD_SCHEDULE)
        .settlementDateOffset(DATE_OFFSET)
        .yieldConvention(YIELD_CONVENTION)
        .exCouponDays(EX_COUPON_DAYS)
        .build();
    assertEquals(test.getDayCount(), DAY_COUNT);
    assertEquals(test.getFixedRate(), FIXED_RATE);
    assertEquals(test.getLegalEntityId(), LEGAL_ENTITY);
    assertEquals(test.getCurrency(), EUR);
    assertEquals(test.getNotional(), NOTIONAL);
    assertEquals(test.getPeriodicSchedule(), PERIOD_SCHEDULE);
    assertEquals(test.getSettlementDateOffset(), DATE_OFFSET);
    assertEquals(test.getYieldConvention(), YIELD_CONVENTION);
    assertEquals(test.getExCouponDays(), EX_COUPON_DAYS);
  }

  public void test_expand() {
    FixedCouponBond base = FixedCouponBond.builder()
        .dayCount(DAY_COUNT)
        .fixedRate(FIXED_RATE)
        .legalEntityId(LEGAL_ENTITY)
        .currency(EUR)
        .notional(NOTIONAL)
        .periodicSchedule(PERIOD_SCHEDULE)
        .settlementDateOffset(DATE_OFFSET)
        .yieldConvention(YIELD_CONVENTION)
        .exCouponDays(EX_COUPON_DAYS)
        .build();
    ExpandedFixedCouponBond expanded = base.expand();
    assertEquals(expanded.getDayCount(), DAY_COUNT);
    assertEquals(expanded.getLegalEntityId(), LEGAL_ENTITY);
    assertEquals(expanded.getSettlementDateOffset(), DATE_OFFSET);
    assertEquals(expanded.getYieldConvention(), YIELD_CONVENTION);
    ImmutableList<FixedCouponBondPaymentPeriod> periodicPayments = expanded.getPeriodicPayments();
    int expNum = 20;
    assertEquals(periodicPayments.size(), expNum);
    LocalDate unadjustedEnd = END_DATE;
    for (int i = 0; i < expNum; ++i) {
      FixedCouponBondPaymentPeriod payment = periodicPayments.get(expNum - 1 - i);
      assertEquals(payment.getCurrency(), EUR);
      assertEquals(payment.getNotional(), NOTIONAL);
      assertEquals(payment.getFixedRate(), FIXED_RATE);
      assertEquals(payment.getUnadjustedEndDate(), unadjustedEnd);
      assertEquals(payment.getEndDate(), BUSINESS_ADJUST.adjust(unadjustedEnd));
      assertEquals(payment.getPaymentDate(), payment.getEndDate());
      LocalDate unadjustedStart = unadjustedEnd.minusMonths(6);
      assertEquals(payment.getUnadjustedStartDate(), unadjustedStart);
      assertEquals(payment.getStartDate(), BUSINESS_ADJUST.adjust(unadjustedStart));
      assertEquals(payment.getYearFraction(), PERIOD_SCHEDULE.createSchedule().getPeriod(expNum - 1 - i)
              .yearFraction(DAY_COUNT, PERIOD_SCHEDULE.createSchedule()));
      unadjustedEnd = unadjustedStart;
    }
    Payment expectedPayment = Payment.of(CurrencyAmount.of(EUR, NOTIONAL), BUSINESS_ADJUST.adjust(END_DATE));
    assertEquals(expanded.getNominalPayment(), expectedPayment);
    assertEquals(expanded.getExCouponDays(), EX_COUPON_DAYS);
  }

  //-------------------------------------------------------------------------
  public void coverage() {
    FixedCouponBond test1 = FixedCouponBond.builder()
        .dayCount(DAY_COUNT)
        .fixedRate(FIXED_RATE)
        .legalEntityId(LEGAL_ENTITY)
        .currency(EUR)
        .notional(NOTIONAL)
        .periodicSchedule(PERIOD_SCHEDULE)
        .settlementDateOffset(DATE_OFFSET)
        .yieldConvention(YIELD_CONVENTION)
        .exCouponDays(EX_COUPON_DAYS)
        .build();
    coverImmutableBean(test1);
    BusinessDayAdjustment adj = BusinessDayAdjustment.of(
        BusinessDayConventions.MODIFIED_FOLLOWING, HolidayCalendars.SAT_SUN);
    PeriodicSchedule sche = PeriodicSchedule.of(
        START_DATE, END_DATE, Frequency.P12M, adj, StubConvention.SHORT_INITIAL, true);
    FixedCouponBond test2 = FixedCouponBond.builder()
        .dayCount(DayCounts.ACT_360)
        .fixedRate(0.005)
        .legalEntityId(StandardId.of("OG-Ticker", "BUN EUR 2"))
        .currency(GBP)
        .notional(1.0e6)
        .periodicSchedule(sche)
        .settlementDateOffset(DaysAdjustment.ofBusinessDays(2, HolidayCalendars.SAT_SUN))
        .yieldConvention(YieldConvention.UK_BUMP_DMO)
        .build();
    coverBeanEquals(test1, test2);
  }

  public void test_serialization() {
    FixedCouponBond test = FixedCouponBond.builder()
        .dayCount(DAY_COUNT)
        .fixedRate(FIXED_RATE)
        .legalEntityId(LEGAL_ENTITY)
        .currency(EUR)
        .notional(NOTIONAL)
        .periodicSchedule(PERIOD_SCHEDULE)
        .settlementDateOffset(DATE_OFFSET)
        .yieldConvention(YIELD_CONVENTION)
        .exCouponDays(EX_COUPON_DAYS)
        .build();
    assertSerialization(test);
  }

}
