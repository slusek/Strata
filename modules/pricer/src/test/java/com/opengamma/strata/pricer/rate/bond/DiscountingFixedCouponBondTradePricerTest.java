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
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.joda.beans.MetaProperty;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.opengamma.analytics.math.interpolation.Interpolator1DFactory;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
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
  private static final LocalDate SETTLEMENT = date(2015, 4, 30);
  private static final LocalDate VALUATION = date(2015, 4, 25);
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
  private static final FixedCouponBondTrade TRADE = FixedCouponBondTrade.builder()
      .securityLink(SECURITY_LINK)
      .tradeInfo(TRADE_INFO)
      .quantity(QUANTITY)
      .build();
  // rates provider
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;
  private static final CurveName NAME_REPO = CurveName.of("TestRepoCurve");
  private static final CurveMetadata METADATA_REPO = Curves.zeroRates(NAME_REPO, ACT_365F);
  private static final InterpolatedNodalCurve CURVE_REPO = InterpolatedNodalCurve.of(
      METADATA_REPO, new double[] {0.1, 2.0, 10.0 }, new double[] {0.05, 0.06, 0.09 }, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_REPO = ZeroRateDiscountFactors.of(EUR, VALUATION, CURVE_REPO);
  private static final BondGroup GROUP_REPO = BondGroup.of("GOVT1 BOND1");
  private static final ImmutableList<StandardId> LIST_REPO = ImmutableList.<StandardId>of(ISSUER_ID, SECURITY_ID);
  private static final CurveName NAME_ISSUER = CurveName.of("TestIssuerCurve");
  private static final CurveMetadata METADATA_ISSUER = Curves.zeroRates(NAME_ISSUER, ACT_365F);
  private static final InterpolatedNodalCurve CURVE_ISSUER = InterpolatedNodalCurve.of(
      METADATA_ISSUER, new double[] {0.2, 9.0, 15.0 }, new double[] {0.03, 0.5, 0.13 }, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_ISSUER = ZeroRateDiscountFactors.of(EUR, VALUATION, CURVE_ISSUER);
  private static final LegalEntityGroup GROUP_ISSUER = LegalEntityGroup.of("GOVT1");
  private static final LegalEntityDiscountingProvider PROVIDER = LegalEntityDiscountingProvider.builder()
      .issuerCurves(ImmutableMap.<Pair<LegalEntityGroup, Currency>, DiscountFactors>of(
          Pair.<LegalEntityGroup, Currency>of(GROUP_ISSUER, EUR), DSC_FACTORS_ISSUER))
      .legalEntityMap(ImmutableMap.<StandardId, LegalEntityGroup>of(ISSUER_ID, GROUP_ISSUER))
      .repoCurves(ImmutableMap.<Pair<BondGroup, Currency>, DiscountFactors>of(
          Pair.<BondGroup, Currency>of(GROUP_REPO, EUR), DSC_FACTORS_REPO))
      .BondMap(ImmutableMap.<List<StandardId>, BondGroup>of(LIST_REPO, GROUP_REPO))
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
    CurrencyAmount computed = PRICER.presentValue(TRADE, PROVIDER);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(expanded.getNominalPayment(), DSC_FACTORS_ISSUER);
    double pvcCupon = 0d;
    for (FixedCouponBondPaymentPeriod payment : expanded.getPeriodicPayments()) {
      pvcCupon += PRICER_COUPON.presentValue(payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER));
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_continuous() {
    CurrencyAmount computed = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(
        expanded.getNominalPayment(), DSC_FACTORS_ISSUER, Z_SPREAD, false, 0);
    double pvcCupon = 0d;
    for (FixedCouponBondPaymentPeriod payment : expanded.getPeriodicPayments()) {
      pvcCupon += PRICER_COUPON.presentValue(
          payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER), Z_SPREAD, false, 0);
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
  }

  public void test_presentValueWithZSpread_periodic() {
    CurrencyAmount computed = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    ExpandedFixedCouponBond expanded = PRODUCT.expand();
    CurrencyAmount expected = PRICER_NOMINAL.presentValue(
        expanded.getNominalPayment(), DSC_FACTORS_ISSUER, Z_SPREAD, true, PERIOD_PER_YEAR);
    double pvcCupon = 0d;
    for (FixedCouponBondPaymentPeriod payment : expanded.getPeriodicPayments()) {
      pvcCupon += PRICER_COUPON.presentValue(
          payment, IssuerCurveDiscountFactors.of(DSC_FACTORS_ISSUER, GROUP_ISSUER), Z_SPREAD, true, PERIOD_PER_YEAR);
    }
    expected = expected.plus(pvcCupon);
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected.getAmount(), NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void test_presentValueFromCleanPrice() {
    double cleanPrice = 0.985;
    CurrencyAmount computed = PRICER.presentValueFromCleanPrice(TRADE, PROVIDER, cleanPrice);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    double accruedInterest = PRICER.accruedInterest(TRADE);
    double expected = cleanPrice * df * NOTIONAL + accruedInterest * df;
    assertEquals(computed.getCurrency(), EUR);
    assertEquals(computed.getAmount(), expected, NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void test_dirtyPriceFromCurves() {
    double computed = PRICER.dirtyPriceFromCurves(TRADE, PROVIDER);
    CurrencyAmount pv = PRICER.presentValue(TRADE, PROVIDER);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    assertEquals(computed, pv.getAmount() / df / NOTIONAL);
  }

  public void test_dirtyPriceFromCurvesWithZSpread_continuous() {
    double computed = PRICER.dirtyPriceFromCurvesWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurrencyAmount pv = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    assertEquals(computed, pv.getAmount() / df / NOTIONAL);
  }

  public void test_dirtyPriceFromCurvesWithZSpread_periodic() {
    double computed = PRICER.dirtyPriceFromCurvesWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurrencyAmount pv = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    double df = DSC_FACTORS_REPO.discountFactor(SETTLEMENT);
    assertEquals(computed, pv.getAmount() / df / NOTIONAL);
  }

  public void test_dirtyPriceFromCleanPrice_cleanPriceFromDirtyPrice() {
    double dirtyPrice = PRICER.dirtyPriceFromCurves(TRADE, PROVIDER);
    CurrencyAmount pv = PRICER.presentValue(TRADE, PROVIDER);
    double cleanPrice = PRICER.cleanPriceFromDirtyPrice(TRADE, dirtyPrice);
    double pvRe = PRICER.presentValueFromCleanPrice(TRADE, PROVIDER, cleanPrice).getAmount();
    double dirtyPriceRe = PRICER.dirtyPriceFromCleanPrice(TRADE, cleanPrice);
    assertEquals(pvRe, pv.getAmount(), NOTIONAL * TOL);
    assertEquals(dirtyPriceRe, dirtyPrice, TOL);
  }

  //-------------------------------------------------------------------------
  public void test_zSpreadFromCurvesAndPV_continuous() {
    CurrencyAmount pv = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    double computed = PRICER.zSpreadFromCurvesAndPV(TRADE, PROVIDER, pv, false, 0);
    assertEquals(computed, Z_SPREAD, TOL);
  }

  public void test_zSpreadFromCurvesAndPV_periodic() {
    CurrencyAmount pv = PRICER.presentValueWithZSpread(TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    double computed = PRICER.zSpreadFromCurvesAndPV(TRADE, PROVIDER, pv, true, PERIOD_PER_YEAR);
    assertEquals(computed, Z_SPREAD, TOL);
  }

  //-------------------------------------------------------------------------
  public void test_presentValueSensitivity() {
    PointSensitivityBuilder point = PRICER.presentValueSensitivity(TRADE, PROVIDER);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(PROVIDER, (p) -> PRICER.presentValue(TRADE, (p)), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_continuous() {
    PointSensitivityBuilder point = PRICER.presentValueSensitivityWithZSpread(TRADE, PROVIDER, Z_SPREAD, false, 0);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueWithZSpread(TRADE, (p), Z_SPREAD, false, 0), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
  }

  public void test_presentValueSensitivityWithZSpread_periodic() {
    PointSensitivityBuilder point = PRICER.presentValueSensitivityWithZSpread(
        TRADE, PROVIDER, Z_SPREAD, true, PERIOD_PER_YEAR);
    CurveCurrencyParameterSensitivities computed = PROVIDER.curveParameterSensitivity(point.build());
    CurveCurrencyParameterSensitivities expected = sensitivity(
        PROVIDER, (p) -> PRICER.presentValueWithZSpread(TRADE, (p), Z_SPREAD, true, PERIOD_PER_YEAR), EPS);
    assertTrue(computed.equalWithTolerance(expected, NOTIONAL * EPS));
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
        .build();
    double accruedInterest3 = PRICER.accruedInterest(trade3);
    assertEquals(accruedInterest3, 6.0 / 365.0 * FIXED_RATE * NOTIONAL, EPS);
  }

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
      .build();

  @Test
  public void dirtyPriceFromYieldUSStreet() {
    double yield = 0.04;
    double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_US, yield);
    assertEquals(dirtyPrice, 1.0417352500524246, TOL); // 2.x.
  }

  @Test
  public void dirtyPriceFromYieldUSStreetLastPeriod() {
    LocalDate valuation = date(2016, 6, 3);
    TradeInfo tradeInfo = TradeInfo.builder()
        .tradeDate(valuation)
        .settlementDate(PRODUCT_US.getSettlementDateOffset().adjust(valuation))
        .build();
    FixedCouponBondTrade trade = FixedCouponBondTrade.builder()
        .securityLink(SECURITY_LINK_US)
        .tradeInfo(tradeInfo)
        .quantity(100)
        .build();
    final double yield = 0.04;
    double dirtyPrice = PRICER.dirtyPriceFromYield(trade, yield);
    assertEquals(dirtyPrice, 1.005635683760684, TOL);
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
      .build();

  @Test
  public void dirtyPriceFromYieldUKExDividend() {
    final double yield = 0.04;
    final double dirtyPrice = PRICER.dirtyPriceFromYield(TRADE_UK, yield);
    assertEquals(dirtyPrice, 1.0277859038905428, TOL); // 2.x.
  }

  @Test
  public void dirtyPriceFromYieldUKLastPeriod() {
    LocalDate valuation = date(2014, 6, 3);
    TradeInfo tradeInfo = TradeInfo.builder()
        .tradeDate(valuation)
        .settlementDate(PRODUCT_UK.getSettlementDateOffset().adjust(valuation))
        .build();
    FixedCouponBondTrade trade = FixedCouponBondTrade.builder()
        .securityLink(SECURITY_LINK_UK)
        .tradeInfo(tradeInfo)
        .quantity(100)
        .build();
    final double yield = 0.04;
    double dirtyPrice = PRICER.dirtyPriceFromYield(trade, yield);
    assertEquals(dirtyPrice, 1.0145736043763598, TOL);
  }

  // TODO test german - same as uk, japan

  // TODO test duration, convexity

  // TODO look at BondSecurityDiscountingMethodTest.
}
