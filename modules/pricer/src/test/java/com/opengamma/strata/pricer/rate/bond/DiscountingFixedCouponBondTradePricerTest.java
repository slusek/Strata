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

import java.time.LocalDate;
import java.util.List;

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
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
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
  private static final StandardId LEGAL_ENTITY_ID = StandardId.of("OG-Ticker", "GOVT1");
  private static final TradeInfo TRADE_INFO = TradeInfo.builder()
      .tradeDate(date(2015, 4, 25))
      .settlementDate(date(2015, 4, 30))
      .build();
  private static final long QUANTITY = 1;
  private static final YieldConvention YIELD_CONVENTION = YieldConvention.GERMAN_BONDS;
  private static final double NOTIONAL = 1.0e7;
  private static final double FIXED_RATE = 0.015;
  private static final HolidayCalendar EUR_CALENDAR = HolidayCalendars.EUTA;
  private static final DaysAdjustment DATE_OFFSET = DaysAdjustment.ofBusinessDays(3, EUR_CALENDAR);
  private static final DayCount DAY_COUNT = DayCounts.ACT_ACT_ICMA;
  private static final LocalDate START_DATE = LocalDate.of(2015, 4, 12);
  private static final LocalDate END_DATE = LocalDate.of(2025, 4, 12);
  private static final BusinessDayAdjustment BUSINESS_ADJUST =
      BusinessDayAdjustment.of(BusinessDayConventions.MODIFIED_FOLLOWING, EUR_CALENDAR);
  private static final PeriodicSchedule PERIOD_SCHEDULE = PeriodicSchedule.of(
      START_DATE, END_DATE, Frequency.P6M, BUSINESS_ADJUST, StubConvention.SHORT_INITIAL, false);
  private static final FixedCouponBond PRODUCT = FixedCouponBond.builder()
      .dayCount(DAY_COUNT)
      .fixedRate(FIXED_RATE)
      .legalEntityId(LEGAL_ENTITY_ID)
      .currency(EUR)
      .notional(NOTIONAL)
      .periodicSchedule(PERIOD_SCHEDULE)
      .settlementDateOffset(DATE_OFFSET)
      .yieldConvention(YIELD_CONVENTION)
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
  private static final LocalDate VALUATION = date(2015, 4, 22);
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;
  private static final CurveName NAME_REPO = CurveName.of("TestRepoCurve");
  private static final CurveMetadata METADATA_REPO = Curves.zeroRates(NAME_REPO, ACT_365F);
  private static final InterpolatedNodalCurve CURVE_REPO =
      InterpolatedNodalCurve.of(METADATA_REPO, new double[] {0.0, 10.0 }, new double[] {0.05, 0.09 }, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_REPO = ZeroRateDiscountFactors.of(EUR, VALUATION, CURVE_REPO);
  private static final BondGroup GROUP_REPO = BondGroup.of("GOVT1 BOND1");
  private static final ImmutableList<StandardId> LIST_REPO =
      ImmutableList.<StandardId>of(LEGAL_ENTITY_ID, SECURITY_ID);
  private static final CurveName NAME_ISSUER = CurveName.of("TestIssuerCurve");
  private static final CurveMetadata METADATA_ISSUER = Curves.zeroRates(NAME_ISSUER, ACT_365F);
  private static final InterpolatedNodalCurve CURVE_ISSUER =
      InterpolatedNodalCurve.of(METADATA_ISSUER, new double[] {0.0, 15.0 }, new double[] {0.03, 0.13 }, INTERPOLATOR);
  private static final DiscountFactors DSC_FACTORS_ISSUER = ZeroRateDiscountFactors.of(EUR, VALUATION, CURVE_ISSUER);
  private static final LegalEntityGroup GROUP_ISSUER = LegalEntityGroup.of("GOVT1");
  private static final LegalEntityDiscountingProvider PROVIDER = LegalEntityDiscountingProvider.builder()
      .issuerCurves(ImmutableMap.<Pair<LegalEntityGroup, Currency>, DiscountFactors>of(
          Pair.<LegalEntityGroup, Currency>of(GROUP_ISSUER, EUR), DSC_FACTORS_ISSUER))
      .legalEntityMap(ImmutableMap.<StandardId, LegalEntityGroup>of(LEGAL_ENTITY_ID, GROUP_ISSUER))
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
    double df = DSC_FACTORS_REPO.discountFactor(TRADE_INFO.getSettlementDate().get());
    double expected = cleanPrice * df * NOTIONAL; // zero accrued
    assertEquals(computed.getAmount(), expected, NOTIONAL * TOL);
  }

  //-------------------------------------------------------------------------
  public void test_dirtyPriceFromCurves() {

  }

  public void test_dirtyPriceFromCurvesWithZSpread_continuous() {

  }

  public void test_dirtyPriceFromCurvesWithZSpread_periodic() {

  }

  //-------------------------------------------------------------------------
  // TODO look at BondSecurityDiscountingMethodTest.
}
