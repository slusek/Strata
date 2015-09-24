/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.bond;

import static com.opengamma.strata.basics.currency.Currency.EUR;
import static com.opengamma.strata.basics.date.DayCounts.ACT_365F;
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
import com.opengamma.strata.finance.rate.bond.FixedCouponBond;
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
import com.opengamma.strata.market.value.LegalEntityGroup;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
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
  private static final Payment UPFRONT_PAYMENT = Payment.of(CurrencyAmount.of(EUR, -NOTIONAL * 0.99), SETTLEMENT);
  /** nonzero ex-coupon period */
  private static final FixedCouponBondTrade TRADE = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK)
      .tradeInfo(TRADE_INFO)
      .quantity(QUANTITY)
      .payment(UPFRONT_PAYMENT)
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
  private static final DiscountingFixedCouponBondTradePricer TRADE_PRICER = DiscountingFixedCouponBondTradePricer.DEFAULT;
  private static final DiscountingFixedCouponBondProductPricer PRODUCT_PRICER =
      DiscountingFixedCouponBondProductPricer.DEFAULT;
  private static final DiscountingPaymentPricer PRICER_NOMINAL = DiscountingPaymentPricer.DEFAULT;

  private static final double Z_SPREAD = 0.035;
  private static final int PERIOD_PER_YEAR = 4;
  private static final double TOL = 1.0e-12;
  private static final double EPS = 1.0e-6;

  //-------------------------------------------------------------------------
  public void test_presentValue() {
    CurrencyAmount computedTrade = TRADE_PRICER.presentValue(TRADE, PROVIDER);
    CurrencyAmount computedProduct = PRODUCT_PRICER.presentValue(PRODUCT, PROVIDER);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(
        computedTrade.getAmount(), computedProduct.multipliedBy(QUANTITY).plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_continuous() {
    CurrencyAmount computedTrade = TRADE_PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount computedProduct = PRODUCT_PRICER.presentValueWithZSpread(PRODUCT, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(
        computedTrade.getAmount(), computedProduct.multipliedBy(QUANTITY).plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_periodic() {
    CurrencyAmount computedTrade =
        TRADE_PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount computedProduct =
        PRODUCT_PRICER.presentValueWithZSpread(PRODUCT, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(
        computedTrade.getAmount(), computedProduct.multipliedBy(QUANTITY).plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValue_noExcoupon() {
    CurrencyAmount computedTrade = TRADE_PRICER.presentValue(TRADE_NO_EXCOUPON, PROVIDER);
    CurrencyAmount computedProduct = PRODUCT_PRICER.presentValue(PRODUCT_NO_EXCOUPON, PROVIDER);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(
        computedTrade.getAmount(), computedProduct.multipliedBy(QUANTITY).plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_continuous_noExcoupon() {
    CurrencyAmount computedTrade =
        TRADE_PRICER.presentValueWithZSpread(TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount computedProduct =
        PRODUCT_PRICER.presentValueWithZSpread(PRODUCT_NO_EXCOUPON, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(
        computedTrade.getAmount(), computedProduct.multipliedBy(QUANTITY).plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_periodic_noExcoupon() {
    CurrencyAmount computedTrade =
        TRADE_PRICER.presentValueWithZSpread(TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount computedProduct =
        PRODUCT_PRICER.presentValueWithZSpread(PRODUCT_NO_EXCOUPON, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount pvPayment = PRICER_NOMINAL.presentValue(UPFRONT_PAYMENT, DSC_FACTORS_REPO);
    assertEquals(
        computedTrade.getAmount(), computedProduct.multipliedBy(QUANTITY).plus(pvPayment).getAmount(), NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void test_presentValueSensitivity() {
    PointSensitivityBuilder pointTrade = TRADE_PRICER.presentValueSensitivity(TRADE, PROVIDER);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(PROVIDER,
        (p) -> TRADE_PRICER.presentValue(TRADE, (p)), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_continuous() {
    PointSensitivityBuilder pointTrade =
        TRADE_PRICER.presentValueSensitivityWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> TRADE_PRICER.presentValueWithZSpread(TRADE, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_periodic() {
    PointSensitivityBuilder pointTrade =
        TRADE_PRICER.presentValueSensitivityWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> TRADE_PRICER.presentValueWithZSpread(TRADE, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueProductSensitivity_noExcoupon() {
    PointSensitivityBuilder pointTrade = TRADE_PRICER.presentValueSensitivity(TRADE_NO_EXCOUPON, PROVIDER);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> TRADE_PRICER.presentValue(TRADE_NO_EXCOUPON, (p)), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_continuous_noExcoupon() {
    PointSensitivityBuilder pointTrade =
        TRADE_PRICER.presentValueSensitivityWithZSpread(TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(
        PROVIDER, (p) -> TRADE_PRICER.presentValueWithZSpread(TRADE_NO_EXCOUPON, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_periodic_noExcoupon() {
    PointSensitivityBuilder pointTrade = TRADE_PRICER.presentValueSensitivityWithZSpread(
        TRADE_NO_EXCOUPON, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computedTrade = PROVIDER.curveParameterSensitivity(pointTrade.build());
    CurveCurrencyParameterSensitivities expectedTrade = sensitivity(PROVIDER,
        (p) -> TRADE_PRICER.presentValueWithZSpread(TRADE_NO_EXCOUPON, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computedTrade.equalWithTolerance(expectedTrade, NOTIONAL * EPS));
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

}
