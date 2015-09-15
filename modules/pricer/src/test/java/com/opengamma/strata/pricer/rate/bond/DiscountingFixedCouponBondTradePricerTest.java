/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.bond;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
import static com.opengamma.strata.collect.TestHelper.assertThrows;
import static com.opengamma.strata.collect.TestHelper.date;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.joda.beans.MetaProperty;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.math.interpolation.Interpolator1DFactory;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.BusinessDayAdjustment;
import com.opengamma.strata.basics.date.BusinessDayConventions;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.date.DaysAdjustment;
import com.opengamma.strata.basics.date.HolidayCalendar;
import com.opengamma.strata.basics.date.HolidayCalendars;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.basics.schedule.Frequency;
import com.opengamma.strata.basics.schedule.PeriodicSchedule;
import com.opengamma.strata.basics.schedule.StubConvention;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.collect.tuple.Pair;
import com.opengamma.strata.finance.Security;
import com.opengamma.strata.finance.SecurityLink;
import com.opengamma.strata.finance.TradeInfo;
import com.opengamma.strata.finance.UnitSecurity;
import com.opengamma.strata.finance.rate.bond.ExpandedFixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondTrade;
import com.opengamma.strata.finance.rate.bond.YieldConvention;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.curve.NodalCurve;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.value.BondGroup;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.IssuerCurveDiscountFactors;
import com.opengamma.strata.market.value.LegalEntityGroup;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.impl.bond.DiscountingFixedCouponBondPaymentPeriodPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

/**
 * Test {@link DiscountingFixedCouponBondTradePricer}.
 */
@Test
public class DiscountingFixedCouponBondTradePricerTest {

  // fixed coupon bond
  private static final StandardId SECURITY_ID = StandardId.of("OG-Ticker", "GOVT1-BOND1");
  private static final StandardId ISSUER_ID = StandardId.of("OG-Ticker", "GOVT1");
  private static final LocalDate SETTLEMENT = date(2016, 4, 30);
  private static final LocalDate VALUATION = date(2016, 4, 25);
  private static final TradeInfo TRADE_INFO = TradeInfo.builder().tradeDate(VALUATION).settlementDate(SETTLEMENT).build();
  private static final long QUANTITY = 1;
  private static final YieldConvention YIELD_CONVENTION = YieldConvention.GERMAN_BONDS;
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
  private static final DaysAdjustment EX_COUPON = DaysAdjustment.ofBusinessDays(-5, EUR_CALENDAR, BUSINESS_ADJUST);
  private static final FixedCouponBond PRODUCT = FixedCouponBond.builder()
      .dayCount(DAY_COUNT)
      .fixedRate(FIXED_RATE)
      .legalEntityId(ISSUER_ID)
      .currency(EUR)
      .notional(NOTIONAL)
      .periodicSchedule(PERIOD_SCHEDULE)
      .settlementDateOffset(DATE_OFFSET)
      .yieldConvention(YIELD_CONVENTION)
      .exCouponPeriod(EX_COUPON)
      .build();
  private static final Security<FixedCouponBond> BOND_SECURITY =
      UnitSecurity.builder(PRODUCT).standardId(SECURITY_ID).build();
  private static final SecurityLink<FixedCouponBond> SECURITY_LINK = SecurityLink.resolved(BOND_SECURITY);
  private static final Payment UPFRONT_PAYMENT = Payment.of(CurrencyAmount.of(EUR, -NOTIONAL * 0.065), SETTLEMENT);
  /** nonzero ex-coupon period */
  private static final FixedCouponBondTrade TRADE = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK)
      .tradeInfo(TRADE_INFO)
      .quantity(QUANTITY)
      .payment(UPFRONT_PAYMENT)
      .build();
  private static final LocalDate VALUATION_ENDED = END_DATE.minusDays(2); // computation is based on settlement date
  private static final LocalDate SETTLEMENT_ENDED = PRODUCT.getSettlementDateOffset().adjust(VALUATION_ENDED);
  private static final TradeInfo TRADE_INFO_ENDED = TradeInfo.builder().tradeDate(VALUATION_ENDED)
      .settlementDate(PRODUCT.getSettlementDateOffset().adjust(VALUATION_ENDED)).build();
  private static final Payment UPFRONT_PAYMENT_ENDED =
      Payment.of(CurrencyAmount.of(EUR, 0d), SETTLEMENT_ENDED);
  /** expired */
  private static final FixedCouponBondTrade TRADE_ENDED = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK)
      .tradeInfo(TRADE_INFO_ENDED)
      .quantity(QUANTITY)
      .payment(UPFRONT_PAYMENT_ENDED)
      .build();
  private static final FixedCouponBond PRODUCT_NO_EXCOUPON = FixedCouponBond.builder()
      .dayCount(DAY_COUNT)
      .fixedRate(FIXED_RATE)
      .legalEntityId(ISSUER_ID)
      .currency(EUR)
      .notional(NOTIONAL)
      .periodicSchedule(PERIOD_SCHEDULE)
      .settlementDateOffset(DATE_OFFSET)
      .yieldConvention(YIELD_CONVENTION)
      .build();
  private static final Security<FixedCouponBond> BOND_SECURITY_NO_EXCOUPON =
      UnitSecurity.builder(PRODUCT_NO_EXCOUPON).standardId(SECURITY_ID).build();
  private static final SecurityLink<FixedCouponBond> SECURITY_LINK_NO_EXCOUPON =
      SecurityLink.resolved(BOND_SECURITY_NO_EXCOUPON);
  /** no ex-coupon period */
  private static final FixedCouponBondTrade TRADE_NO_EXCOUPON = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_NO_EXCOUPON)
      .tradeInfo(TRADE_INFO)
      .quantity(QUANTITY)
      .payment(UPFRONT_PAYMENT)
      .build();

  // rates provider
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;
  private static final CurveName NAME_REPO = CurveName.of("TestRepoCurve");
  private static final CurveMetadata METADATA_REPO = Curves.zeroRates(NAME_REPO, ACT_365F);
  private static final InterpolatedNodalCurve CURVE_REPO = InterpolatedNodalCurve.of(
      METADATA_REPO, new double[] {0.1, 2.0, 10.0}, new double[] {0.05, 0.06, 0.09}, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_REPO = ZeroRateDiscountFactors.of(EUR, VALUATION, CURVE_REPO);
  private static final BondGroup GROUP_REPO = BondGroup.of("GOVT1 BOND1");
  private static final CurveName NAME_ISSUER = CurveName.of("TestIssuerCurve");
  private static final CurveMetadata METADATA_ISSUER = Curves.zeroRates(NAME_ISSUER, ACT_365F);
  private static final InterpolatedNodalCurve CURVE_ISSUER = InterpolatedNodalCurve.of(
      METADATA_ISSUER, new double[] {0.2, 9.0, 15.0}, new double[] {0.03, 0.5, 0.13}, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_ISSUER = ZeroRateDiscountFactors.of(EUR, VALUATION, CURVE_ISSUER);
  private static final LegalEntityGroup GROUP_ISSUER = LegalEntityGroup.of("GOVT1");
  private static final LegalEntityDiscountingProvider PROVIDER = LegalEntityDiscountingProvider.builder()
      .issuerCurves(ImmutableMap.<Pair<LegalEntityGroup, Currency>, DiscountFactors>of(
          Pair.<LegalEntityGroup, Currency>of(GROUP_ISSUER, EUR), DSC_FACTORS_ISSUER))
      .legalEntityMap(ImmutableMap.<StandardId, LegalEntityGroup>of(ISSUER_ID, GROUP_ISSUER))
      .repoCurves(ImmutableMap.<Pair<BondGroup, Currency>, DiscountFactors>of(
          Pair.<BondGroup, Currency>of(GROUP_REPO, EUR), DSC_FACTORS_REPO))
      .bondMap(ImmutableMap.<StandardId, BondGroup>of(SECURITY_ID, GROUP_REPO))
      .valuationDate(VALUATION)
      .build();

  // pricers
  private static final DiscountingFixedCouponBondTradePricer PRICER = DiscountingFixedCouponBondTradePricer.DEFAULT;
  private static final DiscountingPaymentPricer PRICER_NOMINAL = DiscountingPaymentPricer.DEFAULT;
  private static final DiscountingFixedCouponBondPaymentPeriodPricer PRICER_COUPON =
      DiscountingFixedCouponBondPaymentPeriodPricer.DEFAULT;

  private static final double Z_SPREAD = 0.035;
  private static final int PERIOD_PER_YEAR = 4;
  private static final double TOL = 1.0e-12;
  private static final double EPS = 1.0e-6;

  //-------------------------------------------------------------------------
  public void test_presentValue() {
    // product
    CurrencyAmount computed = PRICER.presentValueProduct(TRADE, PROVIDER);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(expanded.getNominalPayment(), DSC_FACTORS_ISSUER);
    int size = expanded.getPeriodicPayments().size();
    double pvCupon = 0d;
    for (int i = 2; i < size; ++i) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      pvCupon += PRICER_COUPON.presentValue(payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER));
    }
    expected = expected.plus(pvCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValue(TRADE, PROVIDER);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected.plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_continuous() {
    // product
    CurrencyAmount computed = PRICER.presentValueProductWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(
        expanded.getNominalPayment(), DSC_FACTORS_ISSUER, Z_SPREAD, false, 0);
    int size = expanded.getPeriodicPayments().size();
    double pvcCupon = 0d;
    for (int i = 2; i < size; ++i) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      pvcCupon += PRICER_COUPON.presentValueWithSpread(
          payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER), Z_SPREAD, false, 0);
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected.plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_periodic() {
    // product
    CurrencyAmount computed = PRICER.presentValueProductWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(
        expanded.getNominalPayment(), DSC_FACTORS_ISSUER, Z_SPREAD, true, PERIOD_PER_YEAR);
    int size = expanded.getPeriodicPayments().size();
    double pvcCupon = 0d;
    for (int i = 2; i < size; ++i) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      pvcCupon += PRICER_COUPON.presentValueWithSpread(
          payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER), Z_SPREAD, true, PERIOD_PER_YEAR);
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected.plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValue_noExcoupon() {
    // product
    CurrencyAmount computed = PRICER.presentValueProduct(TRADE_NO_EXCOUPON, PROVIDER);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(expanded.getNominalPayment(), DSC_FACTORS_ISSUER);
    int size = expanded.getPeriodicPayments().size();
    double pvcCupon = 0d;
    for (int i = 2; i < size; ++i) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      pvcCupon += PRICER_COUPON.presentValue(payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER));
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValue(TRADE, PROVIDER);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected.plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_continuous_noExcoupon() {
    // product
    CurrencyAmount computed = PRICER.presentValueProductWithZSpread(TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, false, 0);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(
        expanded.getNominalPayment(), DSC_FACTORS_ISSUER, Z_SPREAD, false, 0);
    int size = expanded.getPeriodicPayments().size();
    double pvcCupon = 0d;
    for (int i = 2; i < size; ++i) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      pvcCupon += PRICER_COUPON.presentValueWithSpread(
          payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER), Z_SPREAD, false, 0);
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected.plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_periodic_noExcoupon() {
    // product
    CurrencyAmount computed = PRICER.presentValueProductWithZSpread(TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, true,
        PERIOD_PER_YEAR);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(
        expanded.getNominalPayment(), DSC_FACTORS_ISSUER, Z_SPREAD, true, PERIOD_PER_YEAR);
    int size = expanded.getPeriodicPayments().size();
    double pvcCupon = 0d;
    for (int i = 2; i < size; ++i) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(i);
      pvcCupon += PRICER_COUPON.presentValueWithSpread(
          payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER), Z_SPREAD, true, PERIOD_PER_YEAR);
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected.plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValue_Ended() {
    CurrencyAmount computed = PRICER.presentValueProduct(TRADE_ENDED, PROVIDER);
    assertEquals(computed, CurrencyAmount.zero(EUR));
    CurrencyAmount computedTrade = PRICER.presentValue(TRADE_ENDED, PROVIDER);
    assertEquals(computedTrade, CurrencyAmount.zero(EUR));
  }

  public void test_presentValueWithZSpread_continuous_Ended() {
    CurrencyAmount computed = PRICER.presentValueProductWithZSpread(TRADE_ENDED, PROVIDER, Z_SPREAD, false, 0);
    assertEquals(computed, CurrencyAmount.zero(EUR));
    CurrencyAmount computedTrade = PRICER.presentValueWithZSpread(TRADE_ENDED, PROVIDER, Z_SPREAD, false, 0);
    assertEquals(computedTrade, CurrencyAmount.zero(EUR));
  }

  public void test_presentValueWithZSpread_periodic_Ended() {
    CurrencyAmount computed = PRICER.presentValueProductWithZSpread(TRADE_ENDED, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    assertEquals(computed, CurrencyAmount.zero(EUR));
    CurrencyAmount computedTrade = PRICER.presentValueWithZSpread(TRADE_ENDED, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    assertEquals(computedTrade, CurrencyAmount.zero(EUR));
  }

  //-------------------------------------------------------------------------
  public void test_presentValueFromCleanPrice() {
    // product
    double cleanPrice = 0.985;
    CurrencyAmount computed = PRICER.presentValueProductFromCleanPrice(TRADE, PROVIDER, cleanPrice);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    double accruedInterest = PRICER.accruedInterest(TRADE);
    double expected = cleanPrice * df * NOTIONAL + accruedInterest * df;
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected, NOTIONAL * TOL);
    // trade
    CurrencyAmount computedTrade = PRICER.presentValueFromCleanPrice(TRADE, PROVIDER, cleanPrice);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(computedTrade.getAmount(), expected + pvPayment.getAmount(), NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void test_dirtyPriceFromCurves() {
    double computed = PRICER.dirtyPriceFromCurves(TRADE, PROVIDER);
    CurrencyAmount pv = PRICER.presentValueProduct(TRADE, PROVIDER);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    assertEquals(computed, pv.getAmount() / df / NOTIONAL);
  }

  public void test_dirtyPriceFromCurvesWithZSpread_continuous() {
    double computed = PRICER.dirtyPriceFromCurvesWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount pv = PRICER.presentValueProductWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    assertEquals(computed, pv.getAmount() / df / NOTIONAL);
  }

  public void test_dirtyPriceFromCurvesWithZSpread_periodic() {
    double computed = PRICER.dirtyPriceFromCurvesWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount pv = PRICER.presentValueProductWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    assertEquals(computed, pv.getAmount() / df / NOTIONAL);
  }

  public void test_dirtyPriceFromCleanPrice_cleanPriceFromDirtyPrice() {
    double dirtyPrice = PRICER.dirtyPriceFromCurves(TRADE, PROVIDER);
    CurrencyAmount pv = PRICER.presentValueProduct(TRADE, PROVIDER);
    double cleanPrice = PRICER.cleanPriceFromDirtyPrice(TRADE, dirtyPrice);
    double pvRe = PRICER.presentValueProductFromCleanPrice(TRADE, PROVIDER, cleanPrice).getAmount();
    double dirtyPriceRe = PRICER.dirtyPriceFromCleanPrice(TRADE, cleanPrice);
    assertEquals(pvRe, pv.getAmount(), NOTIONAL * TOL);
    assertEquals(dirtyPriceRe, dirtyPrice, TOL);
  }

  //-------------------------------------------------------------------------
  public void test_zSpreadFromCurvesAndPV_continuous() {
    CurrencyAmount pv = PRICER.presentValueProductWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    double computed = PRICER.zSpreadFromCurvesAndPresentValue(TRADE, PROVIDER, pv, false, 0);
    assertEquals(computed, Z_SPREAD, TOL);
  }

  public void test_zSpreadFromCurvesAndPV_periodic() {
    CurrencyAmount pv = PRICER.presentValueProductWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    double computed = PRICER.zSpreadFromCurvesAndPresentValue(TRADE, PROVIDER, pv, true, PERIOD_PER_YEAR);
    assertEquals(computed, Z_SPREAD, TOL);
  }

  //-------------------------------------------------------------------------
  public void test_presentValueSensitivity() {
    // product
    PointSensitivityBuilder point = PRICER.presentValueProductSensitivity(TRADE, PROVIDER);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(PROVIDER, (p) -> PRICER.presentValueProduct(TRADE, (p)), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
    // trade
    PointSensitivityBuilder pointTrade = PRICER.presentValueSensitivity(TRADE, PROVIDER);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(PROVIDER, (p) -> PRICER.presentValue(TRADE, (p)), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_continuous() {
    // product
    PointSensitivityBuilder point = PRICER.presentValueProductSensitivityWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueProductWithZSpread(TRADE, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
    // trade
    PointSensitivityBuilder pointTrade = PRICER.presentValueSensitivityWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueWithZSpread(TRADE, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_periodic() {
    // product
    PointSensitivityBuilder point = PRICER.presentValueProductSensitivityWithZSpread(
        TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueProductWithZSpread(TRADE, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
    // trade
    PointSensitivityBuilder pointTrade =
        PRICER.presentValueSensitivityWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueWithZSpread(TRADE, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueProductSensitivity_noExcoupon() {
    // product
    PointSensitivityBuilder point = PRICER.presentValueProductSensitivity(TRADE_NO_EXCOUPON, PROVIDER);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueProduct(TRADE_NO_EXCOUPON, (p)), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
    // trade
    PointSensitivityBuilder pointTrade = PRICER.presentValueSensitivity(TRADE_NO_EXCOUPON, PROVIDER);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> PRICER.presentValue(TRADE_NO_EXCOUPON, (p)), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_continuous_noExcoupon() {
    // product
    PointSensitivityBuilder point = PRICER.presentValueProductSensitivityWithZSpread(TRADE_NO_EXCOUPON, PROVIDER,
        Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueProductWithZSpread(TRADE_NO_EXCOUPON, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
    // trade
    PointSensitivityBuilder pointTrade = PRICER.presentValueSensitivityWithZSpread(TRADE_NO_EXCOUPON, PROVIDER,
        Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueWithZSpread(TRADE_NO_EXCOUPON, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_periodic_noExcoupon() {
    // product
    PointSensitivityBuilder point = PRICER.presentValueProductSensitivityWithZSpread(
        TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(PROVIDER,
        (p) -> PRICER.presentValueProductWithZSpread(TRADE_NO_EXCOUPON, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
    // trade
    PointSensitivityBuilder pointTrade = PRICER.presentValueSensitivityWithZSpread(
        TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(PROVIDER,
        (p) -> PRICER.presentValueWithZSpread(TRADE_NO_EXCOUPON, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueProductSensitivity_Ended() {
    PointSensitivityBuilder computed = PRICER.presentValueProductSensitivity(TRADE_ENDED, PROVIDER);
    assertEquals(computed, PointSensitivityBuilder.none());
  }

  public void test_presentValueSensitivityWithZSpread_continuous_Ended() {
    PointSensitivityBuilder computed = PRICER.presentValueProductSensitivityWithZSpread(
        TRADE_ENDED, PROVIDER, Z_SPREAD, false, 0);
    assertEquals(computed, PointSensitivityBuilder.none());
  }

  public void test_ppresentValueSensitivityWithZSpread_periodic_Ended() {
    PointSensitivityBuilder computed = PRICER.presentValueProductSensitivityWithZSpread(
        TRADE_ENDED, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    assertEquals(computed, PointSensitivityBuilder.none());
  }

  public void test_dirtyPriceSensitivity() {
    PointSensitivityBuilder point = PRICER.dirtyPriceSensitivity(TRADE, PROVIDER);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> CurrencyAmount.of(EUR, PRICER.dirtyPriceFromCurves(TRADE, (p))), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
  }

  public void test_dirtyPriceSensitivityWithZspread_continuous() {
    PointSensitivityBuilder point = PRICER.dirtyPriceSensitivityWithZspread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(PROVIDER,
        (p) -> CurrencyAmount.of(EUR, PRICER.dirtyPriceFromCurvesWithZSpread(TRADE, (p), Z_SPREAD, false, 0)), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
  }

  public void test_dirtyPriceSensitivityWithZspread_periodic() {
    PointSensitivityBuilder point = PRICER.dirtyPriceSensitivityWithZspread(
        TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(PROVIDER, (p) -> CurrencyAmount.of(
        EUR, PRICER.dirtyPriceFromCurvesWithZSpread(TRADE, (p), Z_SPREAD, true, PERIOD_PER_YEAR)), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
  }

  private CurveCurrencyParameterSensitivities sensitivity(
      LegalEntityDiscountingProvider provider,
      Function<LegalEntityDiscountingProvider, CurrencyAmount> valueFn, double shift) {

    CurrencyAmount valueInit = valueFn.apply(provider);
    CurveCurrencyParameterSensitivities discounting = sensitivity(
        provider, valueFn, LegalEntityDiscountingProvider.meta().repoCurves(), valueInit, shift);
    CurveCurrencyParameterSensitivities forward = sensitivity(
        provider, valueFn, LegalEntityDiscountingProvider.meta().issuerCurves(), valueInit, shift);
    return discounting.combinedWith(forward);
  }

  private <T> CurveCurrencyParameterSensitivities sensitivity(
      LegalEntityDiscountingProvider provider,
      Function<LegalEntityDiscountingProvider, CurrencyAmount> valueFn,
      MetaProperty<ImmutableMap<Pair<T, Currency>, DiscountFactors>> metaProperty,
      CurrencyAmount valueInit,
      double shift) {

    ImmutableMap<Pair<T, Currency>, DiscountFactors> baseCurves = metaProperty.get(provider);
    CurveCurrencyParameterSensitivities result = CurveCurrencyParameterSensitivities.empty();
    for (Pair<T, Currency> key : baseCurves.keySet()) {
      DiscountFactors discountFactors = baseCurves.get(key);
      // Assume underlying object is ZeroRateDiscountFactors 
      NodalCurve curveInt = (NodalCurve) ((ZeroRateDiscountFactors) discountFactors).getCurve();
      int nbNodePoint = curveInt.getXValues().length;
      double[] sensitivity = new double[nbNodePoint];
      for (int i = 0; i < nbNodePoint; i++) {
        Curve dscBumped = bumpedCurve(curveInt, i, shift);
        Map<Pair<T, Currency>, DiscountFactors> mapBumped = new HashMap<>(baseCurves);
        mapBumped.put(key, ZeroRateDiscountFactors.of(
            discountFactors.getCurrency(), discountFactors.getValuationDate(), dscBumped));
        LegalEntityDiscountingProvider providerDscBumped = provider.toBuilder().set(metaProperty, mapBumped).build();
        sensitivity[i] = (valueFn.apply(providerDscBumped).getAmount() - valueInit.getAmount()) / shift;
      }
      CurveMetadata metadata = curveInt.getMetadata();
      result = result.combinedWith(
          CurveCurrencyParameterSensitivity.of(metadata, valueInit.getCurrency(), sensitivity));
    }
    return result;
  }

  private NodalCurve bumpedCurve(NodalCurve curveInt, int loopnode, double shift) {
    double[] yieldBumped = curveInt.getYValues();
    yieldBumped[loopnode] += shift;
    return curveInt.withYValues(yieldBumped);
  }

  //-------------------------------------------------------------------------
  public void test_accruedInterest() {
    // settle before start
    LocalDate valDate1 = START_DATE.minusDays(7);
    LocalDate settleDate1 = START_DATE.minusDays(5);
    TradeInfo tradeInfo1 = TradeInfo.builder().tradeDate(valDate1).settlementDate(settleDate1).build();
    FixedCouponBondTrade trade1 = FixedCouponBondTrade.builder()
        .securityLink(SECURITY_LINK)
        .tradeInfo(tradeInfo1)
        .quantity(QUANTITY)
        .payment(UPFRONT_PAYMENT) // not used
        .build();
    double accruedInterest1 = PRICER.accruedInterest(trade1);
    assertEquals(accruedInterest1, 0d);
    // settle between endDate and endDate -lag
    LocalDate valDate2 = date(2015, 10, 6);
    LocalDate settleDate2 = date(2015, 10, 8);
    TradeInfo tradeInfo2 = TradeInfo.builder().tradeDate(valDate2).settlementDate(settleDate2).build();
    FixedCouponBondTrade trade2 = FixedCouponBondTrade.builder()
        .securityLink(SECURITY_LINK)
        .tradeInfo(tradeInfo2)
        .quantity(QUANTITY)
        .payment(UPFRONT_PAYMENT) // not used
        .build();
    double accruedInterest2 = PRICER.accruedInterest(trade2);
    assertEquals(accruedInterest2, -4.0 / 365.0 * FIXED_RATE * NOTIONAL, EPS);
    // normal
    LocalDate valDate3 = date(2015, 4, 16);
    LocalDate settleDate3 = date(2015, 4, 18); // not adjusted
    TradeInfo tradeInfo3 = TradeInfo.builder().tradeDate(valDate3).settlementDate(settleDate3).build();
    FixedCouponBond product = FixedCouponBond.builder()
        .dayCount(DAY_COUNT)
        .fixedRate(FIXED_RATE)
        .legalEntityId(ISSUER_ID)
        .currency(EUR)
        .notional(NOTIONAL)
        .periodicSchedule(PERIOD_SCHEDULE)
        .settlementDateOffset(DATE_OFFSET)
        .yieldConvention(YIELD_CONVENTION)
        .exCouponPeriod(DaysAdjustment.NONE)
        .build();
    Security<FixedCouponBond> security =
        UnitSecurity.builder(product).standardId(SECURITY_ID).build();
    SecurityLink<FixedCouponBond> link = SecurityLink.resolved(security);
    FixedCouponBondTrade trade3 = FixedCouponBondTrade.builder()
        .securityLink(link)
        .tradeInfo(tradeInfo3)
        .quantity(QUANTITY)
        .payment(UPFRONT_PAYMENT) // not used
        .build();
    double accruedInterest3 = PRICER.accruedInterest(trade3);
    assertEquals(accruedInterest3, 6.0 / 365.0 * FIXED_RATE * NOTIONAL, EPS);
  }

  //-------------------------------------------------------------------------
  /* US Street convention */
  private static final LocalDate START_US = date(2006, 11, 15);
  private static final LocalDate END_US = START_US.plusYears(10);
  private static final PeriodicSchedule SCHEDULE_US = PeriodicSchedule.of(START_US, END_US, Frequency.P6M,
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, HolidayCalendars.SAT_SUN),
      StubConvention.SHORT_INITIAL, false);
  private static final FixedCouponBond PRODUCT_US = FixedCouponBond.builder()
      .dayCount(DayCounts.ACT_ACT_ICMA)
      .fixedRate(0.04625)
      .legalEntityId(ISSUER_ID)
      .currency(Currency.USD)
      .notional(100)
      .periodicSchedule(SCHEDULE_US)
      .settlementDateOffset(DaysAdjustment.ofBusinessDays(3, HolidayCalendars.SAT_SUN))
      .yieldConvention(YieldConvention.US_STREET)
      .exCouponPeriod(DaysAdjustment.NONE)
      .build();
  private static final Security<FixedCouponBond> SECURITY_US =
      UnitSecurity.builder(PRODUCT_US).standardId(SECURITY_ID).build();
  private static final SecurityLink<FixedCouponBond> SECURITY_LINK_US = SecurityLink.resolved(SECURITY_US);

  private static final LocalDate VALUATION_US = date(2011, 8, 18);
  private static final TradeInfo TRADE_INFO_US = TradeInfo.builder()
      .tradeDate(VALUATION_US)
      .settlementDate(PRODUCT_US.getSettlementDateOffset().adjust(VALUATION_US))
      .build();
  private static final FixedCouponBondTrade TRADE_US = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_US)
      .tradeInfo(TRADE_INFO_US)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final LocalDate VALUATION_LAST_US = date(2016, 6, 3);
  private static final TradeInfo TRADE_INFO_LAST_US = TradeInfo.builder()
      .tradeDate(VALUATION_LAST_US)
      .settlementDate(PRODUCT_US.getSettlementDateOffset().adjust(VALUATION_LAST_US))
      .build();
  private static final FixedCouponBondTrade TRADE_LAST_US = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_US)
      .tradeInfo(TRADE_INFO_LAST_US)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final double YIELD_US = 0.04;

  public void dirtyPriceFromYieldUS() {
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_US, YIELD_US);
    assertEquals(dirtyPrice, 1.0417352500524246, TOL); // 2.x.
    double yield = PRICER.yieldFromDirtyPrice(TRADE_US, dirtyPrice);
    assertEquals(yield, YIELD_US, TOL);
  }

  public void dirtyPriceFromYieldUSLastPeriod() {
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_LAST_US, YIELD_US);
    assertEquals(dirtyPrice, 1.005635683760684, TOL); // 2.x.
    double yield = PRICER.yieldFromDirtyPrice(TRADE_LAST_US, dirtyPrice);
    assertEquals(yield, YIELD_US, TOL);
  }

  public void modifiedDurationFromYieldUS() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_US, YIELD_US);
    double price = PRICER.dirtyPriceFromYield(TRADE_US, YIELD_US);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_US, YIELD_US + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_US, YIELD_US - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void modifiedDurationFromYieldUSLastPeriod() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_LAST_US, YIELD_US);
    double price = PRICER.dirtyPriceFromYield(TRADE_LAST_US, YIELD_US);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_LAST_US, YIELD_US + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_LAST_US, YIELD_US - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldUS() {
    double computed = PRICER.convexityFromYield(TRADE_US, YIELD_US);
    double duration = PRICER.modifiedDurationFromYield(TRADE_US, YIELD_US);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_US, YIELD_US + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_US, YIELD_US - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldUSLastPeriod() {
    double computed = PRICER.convexityFromYield(TRADE_LAST_US, YIELD_US);
    double duration = PRICER.modifiedDurationFromYield(TRADE_LAST_US, YIELD_US);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_LAST_US, YIELD_US + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_LAST_US, YIELD_US - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void macaulayDurationFromYieldUS() {
    double duration = PRICER.macaulayDurationFromYield(TRADE_US, YIELD_US);
    assertEquals(duration, 4.6575232098896215, TOL); // 2.x.
  }

  public void macaulayDurationFromYieldUSLastPeriod() {
    double duration = PRICER.macaulayDurationFromYield(TRADE_LAST_US, YIELD_US);
    assertEquals(duration, 0.43478260869565216, TOL); // 2.x.
  }

  /* UK BUMP/DMO convention */
  private static final LocalDate START_UK = date(2002, 9, 7);
  private static final LocalDate END_UK = START_UK.plusYears(12);
  private static final PeriodicSchedule SCHEDULE_UK = PeriodicSchedule.of(START_UK, END_UK, Frequency.P6M,
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, HolidayCalendars.SAT_SUN),
      StubConvention.SHORT_INITIAL, false);
  private static final FixedCouponBond PRODUCT_UK = FixedCouponBond.builder()
      .dayCount(DayCounts.ACT_ACT_ICMA)
      .fixedRate(0.05)
      .legalEntityId(ISSUER_ID)
      .currency(Currency.GBP)
      .notional(100)
      .periodicSchedule(SCHEDULE_UK)
      .settlementDateOffset(DaysAdjustment.ofBusinessDays(1, HolidayCalendars.SAT_SUN))
      .yieldConvention(YieldConvention.UK_BUMP_DMO)
      .exCouponPeriod(DaysAdjustment.ofBusinessDays(-7, HolidayCalendars.SAT_SUN))
      .build();
  private static final Security<FixedCouponBond> SECURITY_UK =
      UnitSecurity.builder(PRODUCT_UK).standardId(SECURITY_ID).build();
  private static final SecurityLink<FixedCouponBond> SECURITY_LINK_UK = SecurityLink.resolved(SECURITY_UK);

  private static final LocalDate VALUATION_UK = date(2011, 9, 2);
  private static final TradeInfo TRADE_INFO_UK = TradeInfo.builder()
      .tradeDate(VALUATION_UK)
      .settlementDate(PRODUCT_UK.getSettlementDateOffset().adjust(VALUATION_UK))
      .build();
  private static final FixedCouponBondTrade TRADE_UK = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_UK)
      .tradeInfo(TRADE_INFO_UK)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final LocalDate VALUATION_LAST_UK = date(2014, 6, 3);
  private static final TradeInfo TRADE_INFO_LAST_UK = TradeInfo.builder()
      .tradeDate(VALUATION_LAST_UK)
      .settlementDate(PRODUCT_UK.getSettlementDateOffset().adjust(VALUATION_LAST_UK))
      .build();
  private static final FixedCouponBondTrade TRADE_LAST_UK = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_UK)
      .tradeInfo(TRADE_INFO_LAST_UK)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final double YIELD_UK = 0.04;

  public void dirtyPriceFromYieldUK() {
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_UK, YIELD_UK);
    assertEquals(dirtyPrice, 1.0277859038905428, TOL); // 2.x.
    double yield = PRICER.yieldFromDirtyPrice(TRADE_UK, dirtyPrice);
    assertEquals(yield, YIELD_UK, TOL);
  }

  public void dirtyPriceFromYieldUKLastPeriod() {
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_LAST_UK, YIELD_UK);
    assertEquals(dirtyPrice, 1.0145736043763598, TOL); // 2.x.
    double yield = PRICER.yieldFromDirtyPrice(TRADE_LAST_UK, dirtyPrice);
    assertEquals(yield, YIELD_UK, TOL);
  }

  public void modifiedDurationFromYieldUK() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_UK, YIELD_UK);
    double price = PRICER.dirtyPriceFromYield(TRADE_UK, YIELD_UK);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_UK, YIELD_UK + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_UK, YIELD_UK - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void modifiedDurationFromYieldUKLastPeriod() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_LAST_UK, YIELD_UK);
    double price = PRICER.dirtyPriceFromYield(TRADE_LAST_UK, YIELD_UK);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_LAST_UK, YIELD_UK + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_LAST_UK, YIELD_UK - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldUK() {
    double computed = PRICER.convexityFromYield(TRADE_UK, YIELD_UK);
    double duration = PRICER.modifiedDurationFromYield(TRADE_UK, YIELD_UK);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_UK, YIELD_UK + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_UK, YIELD_UK - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldUKLastPeriod() {
    double computed = PRICER.convexityFromYield(TRADE_LAST_UK, YIELD_UK);
    double duration = PRICER.modifiedDurationFromYield(TRADE_LAST_UK, YIELD_UK);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_LAST_UK, YIELD_UK + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_LAST_UK, YIELD_UK - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void macaulayDurationFromYieldUK() {
    double duration = PRICER.macaulayDurationFromYield(TRADE_UK, YIELD_UK);
    assertEquals(duration, 2.8312260658609163, TOL); // 2.x.
  }

  public void macaulayDurationFromYieldUKLastPeriod() {
    double duration = PRICER.macaulayDurationFromYield(TRADE_LAST_UK, YIELD_UK);
    assertEquals(duration, 0.25815217391304346, TOL); // 2.x.
  }

  /* German bond convention */
  private static final LocalDate START_GER = date(2002, 9, 7);
  private static final LocalDate END_GER = START_GER.plusYears(12);
  private static final PeriodicSchedule SCHEDULE_GER = PeriodicSchedule.of(START_GER, END_GER, Frequency.P12M,
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, HolidayCalendars.SAT_SUN),
      StubConvention.SHORT_INITIAL, false);
  private static final FixedCouponBond PRODUCT_GER = FixedCouponBond.builder()
      .dayCount(DayCounts.ACT_ACT_ICMA)
      .fixedRate(0.05)
      .legalEntityId(ISSUER_ID)
      .currency(Currency.EUR)
      .notional(100)
      .periodicSchedule(SCHEDULE_GER)
      .settlementDateOffset(DaysAdjustment.ofBusinessDays(3, HolidayCalendars.SAT_SUN))
      .yieldConvention(YieldConvention.GERMAN_BONDS)
      .exCouponPeriod(DaysAdjustment.NONE)
      .build();
  private static final Security<FixedCouponBond> SECURITY_GER =
      UnitSecurity.builder(PRODUCT_GER).standardId(SECURITY_ID).build();
  private static final SecurityLink<FixedCouponBond> SECURITY_LINK_GER = SecurityLink.resolved(SECURITY_GER);

  private static final LocalDate VALUATION_GER = date(2011, 9, 2);
  private static final TradeInfo TRADE_INFO_GER = TradeInfo.builder()
      .tradeDate(VALUATION_GER)
      .settlementDate(PRODUCT_GER.getSettlementDateOffset().adjust(VALUATION_GER))
      .build();
  private static final FixedCouponBondTrade TRADE_GER = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_GER)
      .tradeInfo(TRADE_INFO_GER)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final LocalDate VALUATION_LAST_GER = date(2014, 6, 3);
  private static final TradeInfo TRADE_INFO_LAST_GER = TradeInfo.builder()
      .tradeDate(VALUATION_LAST_GER)
      .settlementDate(PRODUCT_GER.getSettlementDateOffset().adjust(VALUATION_LAST_GER))
      .build();
  private static final FixedCouponBondTrade TRADE_LAST_GER = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_GER)
      .tradeInfo(TRADE_INFO_LAST_GER)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final double YIELD_GER = 0.04;

  public void dirtyPriceFromYieldGerman() {
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_GER, YIELD_GER);
    assertEquals(dirtyPrice, 1.027750910332271, TOL); // 2.x.
    double yield = PRICER.yieldFromDirtyPrice(TRADE_GER, dirtyPrice);
    assertEquals(yield, YIELD_GER, TOL);
  }

  public void dirtyPriceFromYieldGermanLastPeriod() {
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_LAST_GER, YIELD_GER);
    assertEquals(dirtyPrice, 1.039406595790844, TOL); // 2.x.
    double yield = PRICER.yieldFromDirtyPrice(TRADE_LAST_GER, dirtyPrice);
    assertEquals(yield, YIELD_GER, TOL);
  }

  public void modifiedDurationFromYieldGER() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_GER, YIELD_GER);
    double price = PRICER.dirtyPriceFromYield(TRADE_GER, YIELD_GER);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_GER, YIELD_GER + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_GER, YIELD_GER - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void modifiedDurationFromYieldGERLastPeriod() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_LAST_GER, YIELD_GER);
    double price = PRICER.dirtyPriceFromYield(TRADE_LAST_GER, YIELD_GER);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_LAST_GER, YIELD_GER + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_LAST_GER, YIELD_GER - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldGER() {
    double computed = PRICER.convexityFromYield(TRADE_GER, YIELD_GER);
    double duration = PRICER.modifiedDurationFromYield(TRADE_GER, YIELD_GER);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_GER, YIELD_GER + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_GER, YIELD_GER - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldGERLastPeriod() {
    double computed = PRICER.convexityFromYield(TRADE_LAST_GER, YIELD_GER);
    double duration = PRICER.modifiedDurationFromYield(TRADE_LAST_GER, YIELD_GER);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_LAST_GER, YIELD_GER + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_LAST_GER, YIELD_GER - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void macaulayDurationFromYieldGER() {
    double duration = PRICER.macaulayDurationFromYield(TRADE_GER, YIELD_GER);
    assertEquals(duration, 2.861462874541554, TOL); // 2.x.
  }

  public void macaulayDurationFromYieldGERLastPeriod() {
    double duration = PRICER.macaulayDurationFromYield(TRADE_LAST_GER, YIELD_GER);
    assertEquals(duration, 0.26231286613148186, TOL); // 2.x.
  }

  /* Japan simple convention */
  private static final LocalDate START_JP = date(2002, 9, 7);
  private static final LocalDate END_JP = START_JP.plusYears(20);
  private static final PeriodicSchedule SCHEDULE_JP = PeriodicSchedule.of(START_JP, END_JP, Frequency.P6M,
      BusinessDayAdjustment.of(BusinessDayConventions.FOLLOWING, HolidayCalendars.SAT_SUN),
      StubConvention.SHORT_INITIAL, false);
  private static final double RATE_JP = 0.04;
  private static final FixedCouponBond PRODUCT_JP = FixedCouponBond.builder()
      .dayCount(DayCounts.ACT_ACT_ICMA)
      .fixedRate(RATE_JP)
      .legalEntityId(ISSUER_ID)
      .currency(Currency.EUR)
      .notional(100)
      .periodicSchedule(SCHEDULE_JP)
      .settlementDateOffset(DaysAdjustment.ofBusinessDays(3, HolidayCalendars.SAT_SUN))
      .yieldConvention(YieldConvention.JAPAN_SIMPLE)
      .exCouponPeriod(DaysAdjustment.NONE)
      .build();
  private static final Security<FixedCouponBond> SECURITY_JP =
      UnitSecurity.builder(PRODUCT_JP).standardId(SECURITY_ID).build();
  private static final SecurityLink<FixedCouponBond> SECURITY_LINK_JP = SecurityLink.resolved(SECURITY_JP);

  private static final LocalDate VALUATION_JP = date(2011, 9, 2);
  private static final TradeInfo TRADE_INFO_JP = TradeInfo.builder()
      .tradeDate(VALUATION_JP)
      .settlementDate(PRODUCT_JP.getSettlementDateOffset().adjust(VALUATION_JP))
      .build();
  private static final FixedCouponBondTrade TRADE_JP = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_JP)
      .tradeInfo(TRADE_INFO_JP)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final LocalDate VALUATION_LAST_JP = date(2022, 6, 3);
  private static final TradeInfo TRADE_INFO_LAST_JP = TradeInfo.builder()
      .tradeDate(VALUATION_LAST_JP)
      .settlementDate(PRODUCT_JP.getSettlementDateOffset().adjust(VALUATION_LAST_JP))
      .build();
  private static final FixedCouponBondTrade TRADE_LAST_JP = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_JP)
      .tradeInfo(TRADE_INFO_LAST_JP)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final LocalDate VALUATION_ENDED_JP = date(2023, 8, 3);
  private static final TradeInfo TRADE_INFO_ENDED_JP = TradeInfo.builder()
      .tradeDate(VALUATION_ENDED_JP)
      .settlementDate(PRODUCT_JP.getSettlementDateOffset().adjust(VALUATION_ENDED_JP))
      .build();
  private static final FixedCouponBondTrade TRADE_ENDED_JP = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK_JP)
      .tradeInfo(TRADE_INFO_ENDED_JP)
      .quantity(100)
      .payment(UPFRONT_PAYMENT) // not used
      .build();
  private static final double YIELD_JP = 0.035;

  public void dirtyPriceFromYieldJP() {
    double computed = PRICER.dirtyPriceFromYield(TRADE_JP, YIELD_JP);
    double maturity = DayCounts.ACT_365F.relativeYearFraction(TRADE_INFO_JP.getSettlementDate().get(), END_JP);
    double expected = PRICER.dirtyPriceFromCleanPrice(TRADE_JP, (1d + RATE_JP * maturity) / (1d + YIELD_JP * maturity));
    assertEquals(computed, expected, TOL);
    double yield = PRICER.yieldFromDirtyPrice(TRADE_JP, computed);
    assertEquals(yield, YIELD_JP, TOL);
  }

  public void dirtyPriceFromYieldJPLastPeriod() {
    double computed = PRICER.dirtyPriceFromYield(TRADE_LAST_JP, YIELD_JP);
    double maturity = DayCounts.ACT_365F.relativeYearFraction(TRADE_INFO_LAST_JP.getSettlementDate().get(), END_JP);
    double expected = PRICER.dirtyPriceFromCleanPrice(
        TRADE_LAST_JP, (1d + RATE_JP * maturity) / (1d + YIELD_JP * maturity));
    assertEquals(computed, expected, TOL);
    double yield = PRICER.yieldFromDirtyPrice(TRADE_LAST_JP, computed);
    assertEquals(yield, YIELD_JP, TOL);
  }

  public void dirtyPriceFromYieldJPEnded() {
    double computed = PRICER.dirtyPriceFromYield(TRADE_ENDED_JP, YIELD_JP);
    assertEquals(computed, 0d, TOL);
  }

  public void modifiedDurationFromYielddJP() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_JP, YIELD_JP);
    double price = PRICER.dirtyPriceFromYield(TRADE_JP, YIELD_JP);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_JP, YIELD_JP + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_JP, YIELD_JP - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void modifiedDurationFromYieldJPLastPeriod() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_LAST_JP, YIELD_JP);
    double price = PRICER.dirtyPriceFromYield(TRADE_LAST_JP, YIELD_JP);
    double priceUp = PRICER.dirtyPriceFromYield(TRADE_LAST_JP, YIELD_JP + EPS);
    double priceDw = PRICER.dirtyPriceFromYield(TRADE_LAST_JP, YIELD_JP - EPS);
    double expected = 0.5 * (priceDw - priceUp) / price / EPS;
    assertEquals(computed, expected, EPS);
  }

  public void modifiedDurationFromYielddJPEnded() {
    double computed = PRICER.modifiedDurationFromYield(TRADE_ENDED_JP, YIELD_JP);
    assertEquals(computed, 0d, EPS);
  }

  public void convexityFromYieldJP() {
    double computed = PRICER.convexityFromYield(TRADE_JP, YIELD_JP);
    double duration = PRICER.modifiedDurationFromYield(TRADE_JP, YIELD_JP);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_JP, YIELD_JP + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_JP, YIELD_JP - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldJPLastPeriod() {
    double computed = PRICER.convexityFromYield(TRADE_LAST_JP, YIELD_JP);
    double duration = PRICER.modifiedDurationFromYield(TRADE_LAST_JP, YIELD_JP);
    double durationUp = PRICER.modifiedDurationFromYield(TRADE_LAST_JP, YIELD_JP + EPS);
    double durationDw = PRICER.modifiedDurationFromYield(TRADE_LAST_JP, YIELD_JP - EPS);
    double expected = 0.5 * (durationDw - durationUp) / EPS + duration * duration;
    assertEquals(computed, expected, EPS);
  }

  public void convexityFromYieldJPEnded() {
    double computed = PRICER.convexityFromYield(TRADE_ENDED_JP, YIELD_JP);
    assertEquals(computed, 0d, EPS);
  }

  public void macaulayDurationFromYieldYieldJP() {
    assertThrows(() -> PRICER.macaulayDurationFromYield(TRADE_JP, YIELD_JP),
        UnsupportedOperationException.class, "The convention JAPAN_SIMPLE is not supported.");
  }

}
