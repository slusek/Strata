/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.swap;

import static com.opengamma.strata.basics.PayReceive.PAY;
import static com.opengamma.strata.basics.PayReceive.RECEIVE;
import static com.opengamma.strata.basics.currency.Currency.GBP;
import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.BusinessDayConventions.FOLLOWING;
import static com.opengamma.strata.basics.date.DayCounts.ONE_ONE;
import static com.opengamma.strata.basics.date.HolidayCalendars.GBLO;
import static com.opengamma.strata.basics.date.Tenor.TENOR_10Y;
import static com.opengamma.strata.basics.index.IborIndices.GBP_LIBOR_3M;
import static com.opengamma.strata.basics.index.PriceIndices.UK_RPI;
import static com.opengamma.strata.basics.schedule.Frequency.P12M;
import static com.opengamma.strata.collect.TestHelper.assertThrowsIllegalArg;
import static com.opengamma.strata.collect.TestHelper.date;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_CMP_FLAT_EXPANDED_SWAP_LEG_PAY_GBP;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_CMP_NONE_EXPANDED_SWAP_LEG_PAY_GBP;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_EXPANDED_SWAP_LEG_PAY_USD;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_EXPANDED_SWAP_LEG_REC_USD;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_FX_RESET_EXPANDED_SWAP_LEG_PAY_GBP;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_RATE_ACCRUAL_PERIOD;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_RATE_ACCRUAL_PERIOD_2;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_RATE_PAYMENT_PERIOD_CMP_FLAT_REC_GBP;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_RATE_PAYMENT_PERIOD_PAY_USD;
import static com.opengamma.strata.pricer.swap.SwapDummyData.FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2;
import static com.opengamma.strata.pricer.swap.SwapDummyData.IBOR_EXPANDED_SWAP_LEG_REC_GBP;
import static com.opengamma.strata.pricer.swap.SwapDummyData.IBOR_EXPANDED_SWAP_LEG_REC_GBP_MULTI;
import static com.opengamma.strata.pricer.swap.SwapDummyData.IBOR_RATE_OBSERVATION;
import static com.opengamma.strata.pricer.swap.SwapDummyData.IBOR_RATE_PAYMENT_PERIOD_REC_GBP;
import static com.opengamma.strata.pricer.swap.SwapDummyData.IBOR_RATE_PAYMENT_PERIOD_REC_GBP_2;
import static com.opengamma.strata.pricer.swap.SwapDummyData.NOTIONAL_EXCHANGE_REC_GBP;
import static com.opengamma.strata.product.swap.CompoundingMethod.STRAIGHT;
import static com.opengamma.strata.product.swap.SwapLegType.FIXED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.PayReceive;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.value.ValueSchedule;
import com.opengamma.strata.collect.array.DoubleArray;
import com.opengamma.strata.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.strata.market.amount.CashFlow;
import com.opengamma.strata.market.amount.CashFlows;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.curve.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.interpolator.CurveInterpolator;
import com.opengamma.strata.market.interpolator.CurveInterpolators;
import com.opengamma.strata.market.sensitivity.IborRateSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.value.ForwardPriceIndexValues;
import com.opengamma.strata.market.value.PriceIndexValues;
import com.opengamma.strata.pricer.datasets.RatesProviderDataSets;
import com.opengamma.strata.pricer.impl.MockRatesProvider;
import com.opengamma.strata.pricer.impl.rate.ForwardInflationInterpolatedRateObservationFn;
import com.opengamma.strata.pricer.impl.rate.ForwardInflationMonthlyRateObservationFn;
import com.opengamma.strata.pricer.impl.swap.DispatchingPaymentEventPricer;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.sensitivity.RatesFiniteDifferenceSensitivityCalculator;
import com.opengamma.strata.product.rate.InflationInterpolatedRateObservation;
import com.opengamma.strata.product.rate.InflationMonthlyRateObservation;
import com.opengamma.strata.product.swap.ExpandedSwapLeg;
import com.opengamma.strata.product.swap.FixedRateCalculation;
import com.opengamma.strata.product.swap.InflationRateCalculation;
import com.opengamma.strata.product.swap.NotionalExchange;
import com.opengamma.strata.product.swap.NotionalSchedule;
import com.opengamma.strata.product.swap.PaymentEvent;
import com.opengamma.strata.product.swap.PaymentPeriod;
import com.opengamma.strata.product.swap.PaymentSchedule;
import com.opengamma.strata.product.swap.RateAccrualPeriod;
import com.opengamma.strata.product.swap.RateCalculationSwapLeg;
import com.opengamma.strata.product.swap.RatePaymentPeriod;
import com.opengamma.strata.product.swap.SwapLeg;
import com.opengamma.strata.product.swap.type.IborIborSwapConventions;

/**
 * Tests {@link DiscountingSwapLegPricer}.
 */
@Test
public class DiscountingSwapLegPricerTest {

  private static final RatesProvider MOCK_PROV = new MockRatesProvider(RatesProviderDataSets.VAL_DATE_2014_01_22);
  private static final RatesProvider MOCK_PROV_FUTURE = new MockRatesProvider(date(2040, 1, 22));

  private static final double TOLERANCE = 1.0e-12;
  private static final double TOLERANCE_DELTA = 1.0E+0;
  private static final double TOLERANCE_PVBP_FD = 1.0E-4;

  private static final DiscountingSwapLegPricer PRICER_LEG = DiscountingSwapLegPricer.DEFAULT;
  private static final ImmutableRatesProvider RATES_GBP = RatesProviderDataSets.MULTI_GBP;
  private static final ImmutableRatesProvider RATES_USD = RatesProviderDataSets.MULTI_USD;
  private static final ImmutableRatesProvider RATES_GBP_USD = RatesProviderDataSets.MULTI_GBP_USD;
  private static final double FD_SHIFT = 1.0E-7;
  private static final RatesFiniteDifferenceSensitivityCalculator FINITE_DIFFERENCE_CALCULATOR =
      new RatesFiniteDifferenceSensitivityCalculator(FD_SHIFT);

  //-------------------------------------------------------------------------
  public void test_couponEquivalent_twoPeriods() {
    ExpandedSwapLeg leg = ExpandedSwapLeg.builder()
        .type(FIXED)
        .payReceive(PAY)
        .paymentPeriods(FIXED_RATE_PAYMENT_PERIOD_PAY_USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2)
        .build();
    RatesProvider mockProv = mock(RatesProvider.class);
    double df1 = 0.99d;
    when(mockProv.discountFactor(USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getPaymentDate()))
        .thenReturn(df1);
    double df2 = 0.98d;
    when(mockProv.discountFactor(USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2.getPaymentDate()))
        .thenReturn(df2);
    when(mockProv.getValuationDate()).thenReturn(RatesProviderDataSets.VAL_DATE_2014_01_22);
    double pvbp = PRICER_LEG.pvbp(leg, mockProv);
    double ceExpected = PRICER_LEG.presentValuePeriodsInternal(leg, mockProv) / pvbp;
    double ceComputed = PRICER_LEG.couponEquivalent(leg, mockProv, pvbp);
    assertEquals(ceComputed, ceExpected, TOLERANCE);
  }

  //-------------------------------------------------------------------------
  public void test_pvbp_onePeriod() {
    RatesProvider mockProv = mock(RatesProvider.class);
    double df = 0.99d;
    when(mockProv.discountFactor(USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getPaymentDate()))
        .thenReturn(df);
    double expected = df * FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getNotional() *
        FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getAccrualPeriods().get(0).getYearFraction();
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    assertEquals(test.pvbp(FIXED_EXPANDED_SWAP_LEG_PAY_USD, mockProv), expected, TOLERANCE);
  }

  public void test_pvbp_twoPeriods() {
    ExpandedSwapLeg leg = ExpandedSwapLeg.builder()
        .type(FIXED)
        .payReceive(PAY)
        .paymentPeriods(FIXED_RATE_PAYMENT_PERIOD_PAY_USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2)
        .build();
    RatesProvider mockProv = mock(RatesProvider.class);
    double df1 = 0.99d;
    when(mockProv.discountFactor(USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getPaymentDate()))
        .thenReturn(df1);
    double df2 = 0.98d;
    when(mockProv.discountFactor(USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2.getPaymentDate()))
        .thenReturn(df2);
    double expected = df1 * FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getNotional() *
        FIXED_RATE_PAYMENT_PERIOD_PAY_USD.getAccrualPeriods().get(0).getYearFraction();
    expected += df2 * FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2.getNotional() *
        FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2.getAccrualPeriods().get(0).getYearFraction();
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    assertEquals(test.pvbp(leg, mockProv), expected, TOLERANCE);
  }

  public void test_pvbp_compounding_flat_fixed() {
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    PaymentPeriod p = FIXED_CMP_FLAT_EXPANDED_SWAP_LEG_PAY_GBP.getPaymentPeriods().get(0);
    RatesProvider mockProv = mock(RatesProvider.class);
    when(mockProv.getValuationDate()).thenReturn(RatesProviderDataSets.VAL_DATE_2014_01_22);
    double df1 = 0.99d;
    when(mockProv.discountFactor(GBP, p.getPaymentDate()))
        .thenReturn(df1);
    double spread = 1.0E-6;
    RateAccrualPeriod ap1 = FIXED_RATE_ACCRUAL_PERIOD.toBuilder().spread(spread).build();
    RateAccrualPeriod ap2 = FIXED_RATE_ACCRUAL_PERIOD_2.toBuilder().spread(spread).build();
    RatePaymentPeriod pp = FIXED_RATE_PAYMENT_PERIOD_CMP_FLAT_REC_GBP.toBuilder().accrualPeriods(ap1, ap2).build();
    ExpandedSwapLeg sl = FIXED_CMP_FLAT_EXPANDED_SWAP_LEG_PAY_GBP.toBuilder().paymentPeriods(pp).build();
    CurrencyAmount pv0 = PRICER_LEG.presentValue(FIXED_CMP_FLAT_EXPANDED_SWAP_LEG_PAY_GBP, mockProv);
    CurrencyAmount pvP = PRICER_LEG.presentValue(sl, mockProv);
    double pvbpExpected = (pvP.getAmount() - pv0.getAmount()) / spread;
    double pvbpComputed = test.pvbp(FIXED_CMP_FLAT_EXPANDED_SWAP_LEG_PAY_GBP, mockProv);
    assertEquals(pvbpComputed, pvbpExpected, TOLERANCE_PVBP_FD);
  }

  public void test_pvbp_compounding_flat_ibor() {
    LocalDate tradeDate = RATES_USD.getValuationDate();
    LocalDate effectiveDate = IborIborSwapConventions.USD_LIBOR_3M_LIBOR_6M.calculateSpotDateFromTradeDate(tradeDate);
    LocalDate endDate = effectiveDate.plus(TENOR_10Y);
    double spread = 0.0015;
    double shift = 1.0E-6;
    RateCalculationSwapLeg leg0 = IborIborSwapConventions.USD_LIBOR_3M_LIBOR_6M.getSpreadLeg()
        .toLeg(effectiveDate, endDate, RECEIVE, NOTIONAL, spread);
    RateCalculationSwapLeg legP = IborIborSwapConventions.USD_LIBOR_3M_LIBOR_6M.getSpreadLeg()
        .toLeg(effectiveDate, endDate, RECEIVE, NOTIONAL, spread + shift);
    double parSpread = PRICER_LEG.pvbp(leg0, RATES_USD);
    double pv0 = PRICER_LEG.presentValue(leg0, RATES_USD).getAmount();
    double pvP = PRICER_LEG.presentValue(legP, RATES_USD).getAmount();
    double parSpreadExpected = (pvP - pv0) / shift;
    assertEquals(parSpread, parSpreadExpected, TOLERANCE_PVBP_FD);
  }

  public void test_pvbp_fxReset() {
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    assertThrowsIllegalArg(() -> test.pvbp(FIXED_FX_RESET_EXPANDED_SWAP_LEG_PAY_GBP, MOCK_PROV));
  }

  public void test_pvbp_compounding_none() {
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    assertThrowsIllegalArg(() -> test.pvbp(FIXED_CMP_NONE_EXPANDED_SWAP_LEG_PAY_GBP, MOCK_PROV));
  }

  //-------------------------------------------------------------------------
  public void test_presentValue_withCurrency() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.presentValue(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockEvent.presentValue(NOTIONAL_EXCHANGE_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(USD, 2000d * 1.6d);
    assertEquals(test.presentValue(IBOR_EXPANDED_SWAP_LEG_REC_GBP, USD, MOCK_PROV), expected);
  }

  public void test_presentValue_withCurrency_past() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(USD, 0d);
    assertEquals(test.presentValue(IBOR_EXPANDED_SWAP_LEG_REC_GBP, USD, MOCK_PROV_FUTURE), expected);
  }

  //-------------------------------------------------------------------------
  public void test_presentValue() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.presentValue(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, MOCK_PROV))
        .thenReturn(500d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockEvent.presentValue(NOTIONAL_EXCHANGE_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(GBP, 1500d);
    assertEquals(test.presentValue(IBOR_EXPANDED_SWAP_LEG_REC_GBP, MOCK_PROV), expected);
  }

  public void test_presentValue_past() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(GBP, 0d);
    assertEquals(test.presentValue(IBOR_EXPANDED_SWAP_LEG_REC_GBP, MOCK_PROV_FUTURE), expected);
  }

  public void test_presentValue_events() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.presentValue(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, MOCK_PROV))
        .thenReturn(500d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockEvent.presentValue(NOTIONAL_EXCHANGE_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    assertEquals(test.presentValueEventsInternal(IBOR_EXPANDED_SWAP_LEG_REC_GBP, MOCK_PROV), 1000d);
  }

  public void test_presentValue_periods() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.presentValue(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, MOCK_PROV))
        .thenReturn(500d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockEvent.presentValue(NOTIONAL_EXCHANGE_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    assertEquals(test.presentValuePeriodsInternal(IBOR_EXPANDED_SWAP_LEG_REC_GBP, MOCK_PROV), 500d);
  }

  //-------------------------------------------------------------------------
  public void test_forecastValue() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.forecastValue(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockEvent.forecastValue(NOTIONAL_EXCHANGE_REC_GBP, MOCK_PROV))
        .thenReturn(1000d);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(GBP, 2000d);
    assertEquals(test.forecastValue(IBOR_EXPANDED_SWAP_LEG_REC_GBP, MOCK_PROV), expected);
  }

  public void test_forecastValue_past() {
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(GBP, 0d);
    assertEquals(test.forecastValue(IBOR_EXPANDED_SWAP_LEG_REC_GBP, MOCK_PROV_FUTURE), expected);
  }

  //-------------------------------------------------------------------------
  public void test_accruedInterest_firstAccrualPeriod() {
    RatesProvider prov = new MockRatesProvider(IBOR_RATE_PAYMENT_PERIOD_REC_GBP.getStartDate().plusDays(7));
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.accruedInterest(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, prov))
        .thenReturn(1000d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    CurrencyAmount expected = CurrencyAmount.of(GBP, 1000d);
    assertEquals(test.accruedInterest(IBOR_EXPANDED_SWAP_LEG_REC_GBP, prov), expected);
  }

  public void test_accruedInterest_valDateBeforePeriod() {
    RatesProvider prov = new MockRatesProvider(IBOR_RATE_PAYMENT_PERIOD_REC_GBP.getStartDate());
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.accruedInterest(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, prov))
        .thenReturn(1000d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    assertEquals(test.accruedInterest(IBOR_EXPANDED_SWAP_LEG_REC_GBP, prov), CurrencyAmount.zero(GBP));
  }

  public void test_accruedInterest_valDateAfterPeriod() {
    RatesProvider prov = new MockRatesProvider(IBOR_RATE_PAYMENT_PERIOD_REC_GBP.getEndDate().plusDays(1));
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    when(mockPeriod.accruedInterest(IBOR_RATE_PAYMENT_PERIOD_REC_GBP, prov))
        .thenReturn(1000d);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    assertEquals(test.accruedInterest(IBOR_EXPANDED_SWAP_LEG_REC_GBP, prov), CurrencyAmount.zero(GBP));
  }

  //-------------------------------------------------------------------------
  public void test_presentValueSensitivity() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    IborIndex index = GBP_LIBOR_3M;
    Currency ccy = GBP_LIBOR_3M.getCurrency();
    LocalDate fixingDate = IBOR_RATE_OBSERVATION.getFixingDate();
    LocalDate paymentDate = IBOR_RATE_PAYMENT_PERIOD_REC_GBP.getPaymentDate();

    IborRateSensitivity fwdSense = IborRateSensitivity.of(index, fixingDate, ccy, 140.0);
    ZeroRateSensitivity dscSense = ZeroRateSensitivity.of(ccy, paymentDate, -162.0);
    PointSensitivityBuilder sensiPeriod = fwdSense.combinedWith(dscSense);
    LocalDate paymentDateEvent = NOTIONAL_EXCHANGE_REC_GBP.getPaymentDate();
    PointSensitivityBuilder sensiEvent = ZeroRateSensitivity.of(ccy, paymentDateEvent, -134.0);
    PointSensitivities expected = sensiPeriod.build().combinedWith(sensiEvent.build());

    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockPeriod.presentValueSensitivity(expSwapLeg.getPaymentPeriods().get(0), MOCK_PROV))
        .thenReturn(sensiPeriod);
    when(mockEvent.presentValueSensitivity(expSwapLeg.getPaymentEvents().get(0), MOCK_PROV))
        .thenReturn(sensiEvent);
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    PointSensitivities res = test.presentValueSensitivity(expSwapLeg, MOCK_PROV).build();

    assertTrue(res.equalWithTolerance(expected, TOLERANCE));
  }

  public void test_presentValueSensitivity_finiteDifference() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    PointSensitivities point = PRICER_LEG.presentValueSensitivity(expSwapLeg, RATES_GBP).build();
    CurveCurrencyParameterSensitivities psAd = RATES_GBP.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities psFd =
        FINITE_DIFFERENCE_CALCULATOR.sensitivity(RATES_GBP, (p) -> PRICER_LEG.presentValue(expSwapLeg, p));
    ImmutableList<CurveCurrencyParameterSensitivity> listAd = psAd.getSensitivities();
    ImmutableList<CurveCurrencyParameterSensitivity> listFd = psFd.getSensitivities();
    assertEquals(listAd.size(), 2); // No Libor 6M sensitivity
    assertEquals(listFd.size(), 3); // Libor 6M sensitivity equal to 0 in Finite Difference
    assertTrue(psAd.equalWithTolerance(psFd, TOLERANCE_DELTA));
  }

  public void test_presentValueSensitivity_events() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    PointSensitivities point = PRICER_LEG.presentValueSensitivityEventsInternal(expSwapLeg, RATES_GBP).build();
    CurveCurrencyParameterSensitivities psAd = RATES_GBP.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities psFd = FINITE_DIFFERENCE_CALCULATOR.sensitivity(RATES_GBP,
        (p) -> CurrencyAmount.of(GBP, PRICER_LEG.presentValueEventsInternal(expSwapLeg, p)));
    assertTrue(psAd.equalWithTolerance(psFd, TOLERANCE_DELTA));
  }

  public void test_presentValueSensitivity_periods() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    PointSensitivities point = PRICER_LEG.presentValueSensitivityPeriodsInternal(expSwapLeg, RATES_GBP).build();
    CurveCurrencyParameterSensitivities psAd = RATES_GBP.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities psFd = FINITE_DIFFERENCE_CALCULATOR.sensitivity(RATES_GBP,
        (p) -> CurrencyAmount.of(GBP, PRICER_LEG.presentValuePeriodsInternal(expSwapLeg, p)));
    assertTrue(psAd.equalWithTolerance(psFd, TOLERANCE_DELTA));
  }

  //-------------------------------------------------------------------------
  public void test_pvbpSensitivity() {
    ExpandedSwapLeg leg = ExpandedSwapLeg.builder()
        .type(FIXED)
        .payReceive(PAY)
        .paymentPeriods(FIXED_RATE_PAYMENT_PERIOD_PAY_USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2)
        .build();
    PointSensitivities point = PRICER_LEG.pvbpSensitivity(leg, RATES_USD).build();
    CurveCurrencyParameterSensitivities pvbpsAd = RATES_USD.curveParameterSensitivity(point);
    CurveCurrencyParameterSensitivities pvbpsFd = FINITE_DIFFERENCE_CALCULATOR.sensitivity(RATES_USD,
        (p) -> CurrencyAmount.of(USD, PRICER_LEG.pvbp(leg, p)));
    assertTrue(pvbpsAd.equalWithTolerance(pvbpsFd, TOLERANCE_DELTA));
  }

  public void test_pvbpSensitivity_FxReset() {
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    assertThrowsIllegalArg(() -> test.pvbpSensitivity(FIXED_FX_RESET_EXPANDED_SWAP_LEG_PAY_GBP, MOCK_PROV));
  }

  public void test_pvbpSensitivity_Compounding() {
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    assertThrowsIllegalArg(() -> test.pvbpSensitivity(FIXED_CMP_NONE_EXPANDED_SWAP_LEG_PAY_GBP, MOCK_PROV));
  }

  public void test_pvbpSensitivity_compounding_flat_ibor() {
    LocalDate tradeDate = RATES_USD.getValuationDate();
    LocalDate effectiveDate = IborIborSwapConventions.USD_LIBOR_3M_LIBOR_6M.calculateSpotDateFromTradeDate(tradeDate);
    LocalDate endDate = effectiveDate.plus(TENOR_10Y);
    double spread = 0.0015;
    RateCalculationSwapLeg leg = IborIborSwapConventions.USD_LIBOR_3M_LIBOR_6M.getSpreadLeg()
        .toLeg(effectiveDate, endDate, RECEIVE, NOTIONAL, spread);
    PointSensitivities pvbppts = PRICER_LEG.pvbpSensitivity(leg, RATES_USD).build();
    CurveCurrencyParameterSensitivities psAd = RATES_USD.curveParameterSensitivity(pvbppts);
    CurveCurrencyParameterSensitivities psFd =
        FINITE_DIFFERENCE_CALCULATOR.sensitivity(RATES_USD, (p) -> CurrencyAmount.of(USD, PRICER_LEG.pvbp(leg, p)));
    ImmutableList<CurveCurrencyParameterSensitivity> listAd = psAd.getSensitivities();
    ImmutableList<CurveCurrencyParameterSensitivity> listFd = psFd.getSensitivities();
    assertEquals(listAd.size(), 2); // No Libor 6M sensitivity
    assertEquals(listFd.size(), 3); // Libor 6M sensitivity equal to 0 in Finite Difference
    assertTrue(psAd.equalWithTolerance(psFd, TOLERANCE_DELTA));
  }

  //-------------------------------------------------------------------------
  public void test_forecastValueSensitivity() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    IborIndex index = GBP_LIBOR_3M;
    Currency ccy = GBP_LIBOR_3M.getCurrency();
    LocalDate fixingDate = IBOR_RATE_OBSERVATION.getFixingDate();
    PointSensitivityBuilder sensiPeriod = IborRateSensitivity.of(index, fixingDate, ccy, 140.0);
    PointSensitivities expected = sensiPeriod.build();

    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    when(mockPeriod.forecastValueSensitivity(expSwapLeg.getPaymentPeriods().get(0), MOCK_PROV))
        .thenReturn(sensiPeriod);
    when(mockEvent.forecastValueSensitivity(expSwapLeg.getPaymentEvents().get(0), MOCK_PROV))
        .thenReturn(PointSensitivityBuilder.none());
    DiscountingSwapLegPricer test = new DiscountingSwapLegPricer(mockPeriod, mockEvent);
    PointSensitivities res = test.forecastValueSensitivity(expSwapLeg, MOCK_PROV).build();

    assertTrue(res.equalWithTolerance(expected, TOLERANCE));
  }

  //-------------------------------------------------------------------------
  public void test_annuityCash_onePeriod() {
    double yield = 0.01;
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    double computed = test.annuityCash(FIXED_EXPANDED_SWAP_LEG_REC_USD, yield);
    double expected = SwapDummyData.NOTIONAL * (1d - 1d / (1d + yield / 4d)) / yield;
    assertEquals(computed, expected, SwapDummyData.NOTIONAL * TOLERANCE);
  }

  public void test_annuityCash_twoPeriods() {
    ExpandedSwapLeg leg = ExpandedSwapLeg.builder()
        .type(FIXED)
        .payReceive(PAY)
        .paymentPeriods(FIXED_RATE_PAYMENT_PERIOD_PAY_USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2)
        .build();
    double yield = 0.01;
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    double computed = test.annuityCash(leg, yield);
    double expected = SwapDummyData.NOTIONAL * (1d - Math.pow(1d + yield / 4d, -2)) / yield;
    assertEquals(computed, expected, SwapDummyData.NOTIONAL * TOLERANCE);
  }

  public void test_annuityCashDerivative_onePeriod() {
    double yield = 0.01;
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    double computed = test.annuityCashDerivative(FIXED_EXPANDED_SWAP_LEG_REC_USD, yield);
    double expected = 0.5 * (test.annuityCash(FIXED_EXPANDED_SWAP_LEG_REC_USD, yield + FD_SHIFT)
        - test.annuityCash(FIXED_EXPANDED_SWAP_LEG_REC_USD, yield - FD_SHIFT)) / FD_SHIFT;
    assertEquals(computed, expected, SwapDummyData.NOTIONAL * FD_SHIFT);
  }

  public void test_annuityCashDerivative_twoPeriods() {
    ExpandedSwapLeg leg = ExpandedSwapLeg.builder()
        .type(FIXED)
        .payReceive(PAY)
        .paymentPeriods(FIXED_RATE_PAYMENT_PERIOD_PAY_USD, FIXED_RATE_PAYMENT_PERIOD_PAY_USD_2)
        .build();
    double yield = 0.01;
    DiscountingSwapLegPricer test = DiscountingSwapLegPricer.DEFAULT;
    double computed = test.annuityCashDerivative(leg, yield);
    double expected = 0.5 / FD_SHIFT
        * (test.annuityCash(leg, yield + FD_SHIFT) - test.annuityCash(leg, yield - FD_SHIFT));
    assertEquals(computed, expected, SwapDummyData.NOTIONAL * FD_SHIFT);
  }

  //-------------------------------------------------------------------------
  private static final LocalDate DATE_14_06_09 = date(2014, 6, 9);
  private static final LocalDate DATE_19_06_09 = date(2019, 6, 9);
  private static final LocalDate DATE_14_03_31 = date(2014, 3, 31);
  private static final double START_INDEX = 218.0;
  private static final double NOTIONAL = 1000d;
  private static final LocalDate VAL_DATE_INFLATION = LocalDate.of(2014, 7, 8);

  private static final CurveInterpolator INTERPOLATOR = CurveInterpolators.LINEAR;
  private static final double CONSTANT_INDEX = 242.0;
  private static final PriceIndexValues GBPRI_CURVE_FLAT = ForwardPriceIndexValues.of(
      UK_RPI,
      VAL_DATE_INFLATION,
      LocalDateDoubleTimeSeries.of(VAL_DATE_INFLATION.minusMonths(3), START_INDEX),
      InterpolatedNodalCurve.of(
          Curves.prices("GB_RPI_CURVE"),
          DoubleArray.of(1, 200),
          DoubleArray.of(CONSTANT_INDEX, CONSTANT_INDEX),
          INTERPOLATOR));

  private static final CurveInterpolator INTERP_SPLINE = CurveInterpolators.NATURAL_CUBIC_SPLINE;
  private static final PriceIndexValues GBPRI_CURVE = ForwardPriceIndexValues.of(
      UK_RPI,
      VAL_DATE_INFLATION,
      LocalDateDoubleTimeSeries.of(VAL_DATE_INFLATION.minusMonths(3), 227.2),
      InterpolatedNodalCurve.of(
          Curves.prices("GB_RPI_CURVE"),
          DoubleArray.of(6, 12, 24, 60, 120),
          DoubleArray.of(227.2, 252.6, 289.5, 323.1, 351.1),
          INTERP_SPLINE));

  private static final double EPS = 1.0e-14;

  public void test_inflation_monthly() {
    // setup
    SwapLeg swapLeg = createInflationSwapLeg(false, PAY);
    DiscountingSwapLegPricer pricer = DiscountingSwapLegPricer.DEFAULT;
    ImmutableMap<PriceIndex, PriceIndexValues> map = ImmutableMap.of(UK_RPI, GBPRI_CURVE_FLAT);
    Map<Currency, Curve> dscCurve = RATES_GBP.getDiscountCurves();
    LocalDateDoubleTimeSeries ts = LocalDateDoubleTimeSeries.of(DATE_14_03_31, START_INDEX);
    ImmutableRatesProvider prov = ImmutableRatesProvider.builder(VAL_DATE_INFLATION)
        .timeSeries(UK_RPI, ts)
        .priceIndexValues(map)
        .discountCurves(dscCurve)
        .build();
    // test forecastValue and presentValue
    CurrencyAmount fvComputed = pricer.forecastValue(swapLeg, prov);
    CurrencyAmount pvComputed = pricer.presentValue(swapLeg, prov);
    LocalDate paymentDate = swapLeg.expand().getPaymentPeriods().get(0).getPaymentDate();
    double dscFactor = prov.discountFactor(GBP, paymentDate);
    double fvExpected = (CONSTANT_INDEX / START_INDEX - 1.0) * (-NOTIONAL);
    assertEquals(fvComputed.getCurrency(), GBP);
    assertEquals(fvComputed.getAmount(), fvExpected, NOTIONAL * EPS);
    double pvExpected = dscFactor * fvExpected;
    assertEquals(pvComputed.getCurrency(), GBP);
    assertEquals(pvComputed.getAmount(), pvExpected, NOTIONAL * EPS);
    // test forecastValueSensitivity and presentValueSensitivity
    PointSensitivityBuilder fvSensiComputed = pricer.forecastValueSensitivity(swapLeg, prov);
    PointSensitivityBuilder pvSensiComputed = pricer.presentValueSensitivity(swapLeg, prov);
    ForwardInflationMonthlyRateObservationFn obsFn = ForwardInflationMonthlyRateObservationFn.DEFAULT;
    RatePaymentPeriod paymentPeriod = (RatePaymentPeriod) swapLeg.expand().getPaymentPeriods().get(0);
    InflationMonthlyRateObservation obs =
        (InflationMonthlyRateObservation) paymentPeriod.getAccrualPeriods().get(0).getRateObservation();
    PointSensitivityBuilder pvSensiExpected = obsFn.rateSensitivity(obs, DATE_14_06_09, DATE_19_06_09, prov);
    pvSensiExpected = pvSensiExpected.multipliedBy(-NOTIONAL);
    assertTrue(fvSensiComputed.build().normalized()
        .equalWithTolerance(pvSensiExpected.build().normalized(), EPS * NOTIONAL));
    pvSensiExpected = pvSensiExpected.multipliedBy(dscFactor);
    PointSensitivityBuilder dscSensiExpected = prov.discountFactors(GBP).zeroRatePointSensitivity(paymentDate);
    dscSensiExpected = dscSensiExpected.multipliedBy(fvExpected);
    pvSensiExpected = pvSensiExpected.combinedWith(dscSensiExpected);
    assertTrue(pvSensiComputed.build().normalized()
        .equalWithTolerance(pvSensiExpected.build().normalized(), EPS * NOTIONAL));
  }

  public void test_inflation_interpolated() {
    // setup
    SwapLeg swapLeg = createInflationSwapLeg(true, RECEIVE);
    DiscountingSwapLegPricer pricer = DiscountingSwapLegPricer.DEFAULT;
    ImmutableMap<PriceIndex, PriceIndexValues> map = ImmutableMap.of(UK_RPI, GBPRI_CURVE);
    Map<Currency, Curve> dscCurve = RATES_GBP.getDiscountCurves();
    LocalDateDoubleTimeSeries ts = LocalDateDoubleTimeSeries.of(DATE_14_03_31, START_INDEX);
    ImmutableRatesProvider prov = ImmutableRatesProvider.builder(VAL_DATE_INFLATION)
        .timeSeries(UK_RPI, ts)
        .priceIndexValues(map)
        .discountCurves(dscCurve)
        .build();
    // test forecastValue and presentValue
    CurrencyAmount fvComputed = pricer.forecastValue(swapLeg, prov);
    CurrencyAmount pvComputed = pricer.presentValue(swapLeg, prov);
    LocalDate paymentDate = swapLeg.expand().getPaymentPeriods().get(0).getPaymentDate();
    double dscFactor = prov.discountFactor(GBP, paymentDate);
    ForwardInflationInterpolatedRateObservationFn obsFn = ForwardInflationInterpolatedRateObservationFn.DEFAULT;
    RatePaymentPeriod paymentPeriod = (RatePaymentPeriod) swapLeg.expand().getPaymentPeriods().get(0);
    InflationInterpolatedRateObservation obs =
        (InflationInterpolatedRateObservation) paymentPeriod.getAccrualPeriods().get(0).getRateObservation();
    double indexRate = obsFn.rate(obs, DATE_14_06_09, DATE_19_06_09, prov);
    double fvExpected = indexRate * (NOTIONAL);
    assertEquals(fvComputed.getCurrency(), GBP);
    assertEquals(fvComputed.getAmount(), fvExpected, NOTIONAL * EPS);
    double pvExpected = dscFactor * fvExpected;
    assertEquals(pvComputed.getCurrency(), GBP);
    assertEquals(pvComputed.getAmount(), pvExpected, NOTIONAL * EPS);
    // test forecastValueSensitivity and presentValueSensitivity
    PointSensitivityBuilder fvSensiComputed = pricer.forecastValueSensitivity(swapLeg, prov);
    PointSensitivityBuilder pvSensiComputed = pricer.presentValueSensitivity(swapLeg, prov);
    PointSensitivityBuilder pvSensiExpected = obsFn.rateSensitivity(obs, DATE_14_06_09, DATE_19_06_09, prov);
    pvSensiExpected = pvSensiExpected.multipliedBy(NOTIONAL);
    assertTrue(fvSensiComputed.build().normalized()
        .equalWithTolerance(pvSensiExpected.build().normalized(), EPS * NOTIONAL));
    pvSensiExpected = pvSensiExpected.multipliedBy(dscFactor);
    PointSensitivityBuilder dscSensiExpected = prov.discountFactors(GBP).zeroRatePointSensitivity(paymentDate);
    dscSensiExpected = dscSensiExpected.multipliedBy(fvExpected);
    pvSensiExpected = pvSensiExpected.combinedWith(dscSensiExpected);
    assertTrue(pvSensiComputed.build().normalized()
        .equalWithTolerance(pvSensiExpected.build().normalized(), EPS * NOTIONAL));
  }

  private SwapLeg createInflationSwapLeg(boolean interpolated, PayReceive pay) {
    BusinessDayAdjustment adj = BusinessDayAdjustment.of(FOLLOWING, GBLO);
    PeriodicSchedule accrualSchedule = PeriodicSchedule.builder()
        .startDate(DATE_14_06_09)
        .endDate(DATE_19_06_09)
        .frequency(Frequency.ofYears(5))
        .businessDayAdjustment(adj)
        .build();
    PaymentSchedule paymentSchedule = PaymentSchedule.builder()
        .paymentFrequency(Frequency.ofYears(5))
        .paymentDateOffset(DaysAdjustment.ofBusinessDays(2, GBLO))
        .build();
    InflationRateCalculation rateCalc = InflationRateCalculation.builder()
        .index(UK_RPI)
        .interpolated(interpolated)
        .lag(Period.ofMonths(3))
        .build();
    NotionalSchedule notionalSchedule = NotionalSchedule.of(GBP, NOTIONAL);
    SwapLeg swapLeg = RateCalculationSwapLeg.builder()
        .payReceive(pay)
        .accrualSchedule(accrualSchedule)
        .paymentSchedule(paymentSchedule)
        .notionalSchedule(notionalSchedule)
        .calculation(rateCalc)
        .build();
    return swapLeg;
  }

  public void test_inflation_fixed() {
    // setup
    double fixedRate = 0.05;
    BusinessDayAdjustment adj = BusinessDayAdjustment.of(FOLLOWING, GBLO);
    PeriodicSchedule accrualSchedule = PeriodicSchedule.builder()
        .startDate(DATE_14_06_09)
        .endDate(DATE_19_06_09)
        .frequency(P12M)
        .businessDayAdjustment(adj)
        .build();
    PaymentSchedule paymentSchedule = PaymentSchedule.builder()
        .paymentFrequency(Frequency.ofYears(5))
        .paymentDateOffset(DaysAdjustment.ofBusinessDays(2, GBLO))
        .compoundingMethod(STRAIGHT)
        .build();
    FixedRateCalculation rateCalc = FixedRateCalculation.builder()
        .rate(ValueSchedule.of(fixedRate))
        .dayCount(ONE_ONE) // year fraction is always 1.
        .build();
    NotionalSchedule notionalSchedule = NotionalSchedule.of(GBP, 1000d);
    SwapLeg swapLeg = RateCalculationSwapLeg.builder()
        .payReceive(RECEIVE)
        .accrualSchedule(accrualSchedule)
        .paymentSchedule(paymentSchedule)
        .notionalSchedule(notionalSchedule)
        .calculation(rateCalc)
        .build();
    DiscountingSwapLegPricer pricer = DiscountingSwapLegPricer.DEFAULT;
    Map<Currency, Curve> dscCurve = RATES_GBP.getDiscountCurves();
    ImmutableRatesProvider prov = ImmutableRatesProvider.builder(VAL_DATE_INFLATION)
        .discountCurves(dscCurve)
        .build();
    // test forecastValue and presentValue
    CurrencyAmount fvComputed = pricer.forecastValue(swapLeg, prov);
    CurrencyAmount pvComputed = pricer.presentValue(swapLeg, prov);
    LocalDate paymentDate = swapLeg.expand().getPaymentPeriods().get(0).getPaymentDate();
    double dscFactor = prov.discountFactor(GBP, paymentDate);
    double fvExpected = (Math.pow(1.0 + fixedRate, 5) - 1.0) * NOTIONAL;
    assertEquals(fvComputed.getCurrency(), GBP);
    assertEquals(fvComputed.getAmount(), fvExpected, NOTIONAL * EPS);
    double pvExpected = fvExpected * dscFactor;
    assertEquals(pvComputed.getCurrency(), GBP);
    assertEquals(pvComputed.getAmount(), pvExpected, NOTIONAL * EPS);
    // test forecastValueSensitivity and presentValueSensitivity
    PointSensitivityBuilder fvSensiComputed = pricer.forecastValueSensitivity(swapLeg, prov);
    PointSensitivityBuilder pvSensiComputed = pricer.presentValueSensitivity(swapLeg, prov);
    assertEquals(fvSensiComputed, PointSensitivityBuilder.none());
    PointSensitivityBuilder pvSensiExpected = prov.discountFactors(GBP).zeroRatePointSensitivity(paymentDate);
    pvSensiExpected = pvSensiExpected.multipliedBy(fvExpected);
    assertTrue(pvSensiComputed.build().normalized()
        .equalWithTolerance(pvSensiExpected.build().normalized(), EPS * NOTIONAL));
  }

  //-------------------------------------------------------------------------
  public void test_cashFlows() {
    RatesProvider mockProv = mock(RatesProvider.class);
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    DispatchingPaymentEventPricer eventPricer = DispatchingPaymentEventPricer.DEFAULT;
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP_MULTI;
    PaymentPeriod period1 = IBOR_RATE_PAYMENT_PERIOD_REC_GBP;
    PaymentPeriod period2 = IBOR_RATE_PAYMENT_PERIOD_REC_GBP_2;
    NotionalExchange event = NOTIONAL_EXCHANGE_REC_GBP;
    double fv1 = 520d;
    double fv2 = 450d;
    double df = 1.0d;
    double df1 = 0.98;
    double df2 = 0.93;
    when(mockPeriod.forecastValue(period1, mockProv)).thenReturn(fv1);
    when(mockPeriod.forecastValue(period2, mockProv)).thenReturn(fv2);
    when(mockProv.getValuationDate()).thenReturn(LocalDate.of(2014, 7, 1));
    when(mockProv.discountFactor(expSwapLeg.getCurrency(), period1.getPaymentDate())).thenReturn(df1);
    when(mockProv.discountFactor(expSwapLeg.getCurrency(), period2.getPaymentDate())).thenReturn(df2);
    when(mockProv.discountFactor(expSwapLeg.getCurrency(), event.getPaymentDate())).thenReturn(df);
    DiscountingSwapLegPricer pricer = new DiscountingSwapLegPricer(mockPeriod, eventPricer);

    CashFlows computed = pricer.cashFlows(expSwapLeg, mockProv);
    CashFlow flow1 = CashFlow.ofForecastValue(period1.getPaymentDate(), GBP, fv1, df1);
    CashFlow flow2 = CashFlow.ofForecastValue(period2.getPaymentDate(), GBP, fv2, df2);
    CashFlow flow3 = CashFlow.ofForecastValue(event.getPaymentDate(), GBP, event.getPaymentAmount().getAmount(), df);
    CashFlows expected = CashFlows.of(ImmutableList.of(flow1, flow2, flow3));
    assertEquals(computed, expected);
  }

  //-------------------------------------------------------------------------
  public void test_currencyExposure() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    PointSensitivities point = PRICER_LEG.presentValueSensitivity(expSwapLeg, RATES_GBP).build();
    MultiCurrencyAmount expected = RATES_GBP.currencyExposure(point).plus(PRICER_LEG.presentValue(expSwapLeg, RATES_GBP));
    MultiCurrencyAmount computed = PRICER_LEG.currencyExposure(expSwapLeg, RATES_GBP);
    assertEquals(computed, expected);
  }

  public void test_currencyExposure_fx() {
    ExpandedSwapLeg expSwapLeg = FIXED_FX_RESET_EXPANDED_SWAP_LEG_PAY_GBP;
    PointSensitivities point = PRICER_LEG.presentValueSensitivity(expSwapLeg, RATES_GBP_USD).build();
    MultiCurrencyAmount expected = RATES_GBP_USD.currencyExposure(point.convertedTo(USD, RATES_GBP_USD))
        .plus(PRICER_LEG.presentValue(expSwapLeg, RATES_GBP_USD));
    MultiCurrencyAmount computed = PRICER_LEG.currencyExposure(expSwapLeg, RATES_GBP_USD);
    assertEquals(computed.getAmount(USD).getAmount(), expected.getAmount(USD).getAmount(), EPS * NOTIONAL);
    assertFalse(computed.contains(GBP)); // 0 GBP
  }

  //-------------------------------------------------------------------------
  public void test_currentCash_zero() {
    ExpandedSwapLeg expSwapLeg = IBOR_EXPANDED_SWAP_LEG_REC_GBP;
    CurrencyAmount computed = PRICER_LEG.currentCash(expSwapLeg, RATES_GBP);
    assertEquals(computed, CurrencyAmount.zero(expSwapLeg.getCurrency()));
  }

  public void test_currentCash_payEvent() {
    ExpandedSwapLeg expSwapLeg = FIXED_EXPANDED_SWAP_LEG_PAY_USD;
    LocalDate paymentDate = expSwapLeg.getPaymentEvents().get(0).getPaymentDate();
    RatesProvider prov = new MockRatesProvider(paymentDate);
    PaymentEventPricer<PaymentEvent> mockEvent = mock(PaymentEventPricer.class);
    double expected = 1234d;
    when(mockEvent.currentCash(expSwapLeg.getPaymentEvents().get(0), prov)).thenReturn(expected);
    DiscountingSwapLegPricer pricer = new DiscountingSwapLegPricer(PaymentPeriodPricer.instance(), mockEvent);
    CurrencyAmount computed = pricer.currentCash(expSwapLeg, prov);
    assertEquals(computed, CurrencyAmount.of(expSwapLeg.getCurrency(), expected));
  }

  public void test_currentCash_payPeriod() {
    ExpandedSwapLeg expSwapLeg = FIXED_EXPANDED_SWAP_LEG_PAY_USD;
    LocalDate paymentDate = expSwapLeg.getPaymentPeriods().get(0).getPaymentDate();
    RatesProvider prov = new MockRatesProvider(paymentDate);
    PaymentPeriodPricer<PaymentPeriod> mockPeriod = mock(PaymentPeriodPricer.class);
    double expected = 1234d;
    when(mockPeriod.currentCash(expSwapLeg.getPaymentPeriods().get(0), prov)).thenReturn(expected);
    DiscountingSwapLegPricer pricer = new DiscountingSwapLegPricer(mockPeriod, PaymentEventPricer.instance());
    CurrencyAmount computed = pricer.currentCash(expSwapLeg, prov);
    assertEquals(computed, CurrencyAmount.of(expSwapLeg.getCurrency(), expected));
  }
}
