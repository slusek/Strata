/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.bond;

import java.time.LocalDate;

import com.google.common.collect.ImmutableList;
import com.opengamma.analytics.math.function.Function1D;
import com.opengamma.analytics.math.rootfinding.BracketRoot;
import com.opengamma.analytics.math.rootfinding.BrentSingleRootFinder;
import com.opengamma.analytics.math.rootfinding.RealSingleRootFinder;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.DayCounts;
import com.opengamma.strata.basics.schedule.Schedule;
import com.opengamma.strata.basics.schedule.SchedulePeriod;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.finance.rate.bond.ExpandedFixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondTrade;
import com.opengamma.strata.finance.rate.bond.YieldConvention;
import com.opengamma.strata.market.sensitivity.IssuerCurveZeroRateSensitivity;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.RepoCurveZeroRateSensitivity;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.value.IssuerCurveDiscountFactors;
import com.opengamma.strata.market.value.RepoCurveDiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.impl.bond.DiscountingFixedCouponBondPaymentPeriodPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

/**
 * Pricer for for rate fixed coupon bond trades.
 * <p>
 * This function provides the ability to price a {@link FixedCouponBondTrade}.
 */
public class DiscountingFixedCouponBondTradePricer {
  //TODO quantity is not handled

  /**
   * The root finder.
   */
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();
  /**
   * Brackets a root.
   */
  private static final BracketRoot ROOT_BRACKETER = new BracketRoot();
  /**
   * Default implementation.
   */
  public static final DiscountingFixedCouponBondTradePricer DEFAULT = new DiscountingFixedCouponBondTradePricer(
      DiscountingPaymentPricer.DEFAULT, DiscountingFixedCouponBondPaymentPeriodPricer.DEFAULT);

  /**
   * Pricer for {@link Payment}.
   */
  private final DiscountingPaymentPricer nominalPricer;
  /**
   * Pricer for {@link FixedCouponBondPaymentPeriod}.
   */
  private final DiscountingFixedCouponBondPaymentPeriodPricer periodPricer;

  /**
   * Creates an instance.
   * 
   * @param nominalPricer  the pricer for {@link Payment}
   * @param periodPricer  the pricer for {@link FixedCouponBondPaymentPeriod}
   */
  public DiscountingFixedCouponBondTradePricer(DiscountingPaymentPricer nominalPricer,
      DiscountingFixedCouponBondPaymentPeriodPricer periodPricer) {
    this.nominalPricer = ArgChecker.notNull(nominalPricer, "nominalPricer");
    this.periodPricer = ArgChecker.notNull(periodPricer, "periodPricer");
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the fixed coupon bond trade.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValue(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    ExpandedFixedCouponBond product = trade.getProduct().expand();
    IssuerCurveDiscountFactors discountFactors = provider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency());
    CurrencyAmount pvNominal = presentValueNominal(product, discountFactors);
    CurrencyAmount pvCoupon = presentValueCoupon(product, discountFactors);
    return pvNominal.plus(pvCoupon);
  }

  /**
   * Calculates the present value of the fixed coupon bond trade with z-spread.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates 
   * of the issuer discounting curve. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    ExpandedFixedCouponBond product = trade.getProduct().expand();
    IssuerCurveDiscountFactors discountFactors = provider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency());
    CurrencyAmount pvNominal = presentValueNominalFromZSpread(product, discountFactors, zSpread, periodic,
        periodPerYear);
    CurrencyAmount pvCoupon = presentValueCouponFromZSpread(product, discountFactors, zSpread, periodic, periodPerYear);
    return pvNominal.plus(pvCoupon);
  }

  /**
   * Calculates the present value of the fixed coupon bond trade from its clean price.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param cleanPrice  the clean price.
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueFromCleanPrice(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double cleanPrice) {
    FixedCouponBond product = trade.getProduct();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    StandardId securityId = trade.getSecurityLink().getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = provider.repoCurveDiscountFactors(
        ImmutableList.<StandardId>of(legalEntityId, securityId), product.getCurrency()).discountFactor(settlementDate);
    double pvPrice = (cleanPrice * product.getNotional() + accruedInterest(trade)) * df;
    return CurrencyAmount.of(product.getCurrency(), pvPrice);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the dirty price of the fixed coupon bond trade.
   * <p>
   * This requires the trade information, {@code settlementDate}. Thus {@code tradeInfo} in the trade must not be empty.
   * The result is based on the payment currency of the bond.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the dirty price of the fixed coupon bond trade
   */
  public double dirtyPriceFromCurves(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    FixedCouponBond product = trade.getProduct();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    CurrencyAmount pv = presentValue(trade, provider);
    StandardId securityId = trade.getSecurityLink().getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = provider.repoCurveDiscountFactors(
        ImmutableList.<StandardId>of(legalEntityId, securityId), product.getCurrency()).discountFactor(settlementDate);
    double notional = product.getNotional();
    return pv.getAmount() / df / notional;
  }

  /**
   * Calculates the dirty price of the fixed coupon bond trade with z-spread.
   * <p>
   * This requires the trade information, {@code settlementDate}. Thus {@code tradeInfo} in the trade must not be empty.
   * The result is based on the payment currency of the bond.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates 
   * of the issuer discounting curve. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the dirty price of the fixed coupon bond trade
   */
  public double dirtyPriceFromCurvesWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    FixedCouponBond product = trade.getProduct();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    CurrencyAmount pv = presentValueWithZSpread(trade, provider, zSpread, periodic, periodPerYear);
    StandardId securityId = trade.getSecurityLink().getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = provider.repoCurveDiscountFactors(
        ImmutableList.<StandardId>of(legalEntityId, securityId), product.getCurrency()).discountFactor(settlementDate);
    double notional = product.getNotional();
    return pv.getAmount() / df / notional;
  }

  /**
   * Calculates the dirty price of the fixed coupon bond trade from its clean price.
   * 
   * @param trade  the trade to price
   * @param cleanPrice  the clean price
   * @return the present value of the fixed coupon bond trade
   */
  public double dirtyPriceFromCleanPrice(FixedCouponBondTrade trade, double cleanPrice) {
    double notional = trade.getProduct().getNotional();
    double accruedInterest = accruedInterest(trade);
    return cleanPrice + accruedInterest / notional;
  }

  /**
   * Calculates the clean price of the fixed coupon bond trade from its dirty price.
   * 
   * @param trade  the trade to price
   * @param dirtyPrice  the dirty price
   * @return the present value of the fixed coupon bond trade
   */
  public double cleanPriceFromDirtyPrice(FixedCouponBondTrade trade, double dirtyPrice) {
    final double notional = trade.getProduct().getNotional();
    double accruedInterest = accruedInterest(trade);
    return dirtyPrice - accruedInterest / notional;
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the dirty price of the fixed coupon bond trade with z-spread.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates of 
   * the discounting curve associated to the bond (Issuer Entity) to match the present value.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param pv  the present value
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the z-spread of the fixed coupon bond trade
   */
  public double zSpreadFromCurvesAndPV(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      CurrencyAmount pv,
      boolean periodic,
      int periodPerYear) {
    final Function1D<Double, Double> residual = new Function1D<Double, Double>() {
      @Override
      public Double evaluate(final Double z) {
        return presentValueWithZSpread(trade, provider, z, periodic, periodPerYear).getAmount() -
            pv.getAmount();
      }
    };
    double[] range = ROOT_BRACKETER.getBracketedPoints(residual, -0.01, 0.01); // Starting range is [-1%, 1%]
    return ROOT_FINDER.getRoot(residual, range[0], range[1]);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the fixed coupon bond trade.
   * <p>
   * The present value sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivityBuilder presentValueSensitivity(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider) {
    ExpandedFixedCouponBond product = trade.getProduct().expand();
    IssuerCurveDiscountFactors discountFactors = provider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency());
    PointSensitivityBuilder pvNominal = presentValueSensitivityNominal(product, discountFactors);
    PointSensitivityBuilder pvCoupon = presentValueSensitivityCoupon(product, discountFactors);
    return pvNominal.combinedWith(pvCoupon);
  }

  /**
   * Calculates the present value sensitivity of the fixed coupon bond trade with z-spread.
   * <p>
   * The present value sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates 
   * of the issuer discounting curve. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivityBuilder presentValueSensitivityWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    ExpandedFixedCouponBond product = trade.getProduct().expand();
    IssuerCurveDiscountFactors discountFactors = provider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency());
    PointSensitivityBuilder pvNominal = presentValueSensitivityNominalFromZSpread(product, discountFactors, zSpread,
        periodic, periodPerYear);
    PointSensitivityBuilder pvCoupon = presentValueSensitivityCouponFromZSpread(product, discountFactors, zSpread,
        periodic, periodPerYear);
    return pvNominal.combinedWith(pvCoupon);
  }

  /**
   * Calculates the dirty price sensitivity of the fixed coupon bond trade.
   * <p>
   * The dirty price sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the dirty price value curve sensitivity of the trade
   */
  public PointSensitivityBuilder dirtyPriceSensitivity(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider) {
    FixedCouponBond product = trade.getProduct();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    StandardId securityId = trade.getSecurityLink().getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    RepoCurveDiscountFactors discountFactors = provider.repoCurveDiscountFactors(
        ImmutableList.<StandardId>of(legalEntityId, securityId), product.getCurrency());
    double df = discountFactors.discountFactor(settlementDate);
    CurrencyAmount pv = presentValue(trade, provider);
    double notional = product.getNotional();
    PointSensitivityBuilder pvSensi = presentValueSensitivity(trade, provider).multipliedBy(1d / df / notional);
    RepoCurveZeroRateSensitivity dfSensi = discountFactors.zeroRatePointSensitivity(settlementDate)
        .multipliedBy(-pv.getAmount() / df / df / notional);
    return pvSensi.combinedWith(dfSensi);
  }

  /**
   * Calculates the dirty price sensitivity of the fixed coupon bond trade with z-spread.
   * <p>
   * The dirty price sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates 
   * of the issuer discounting curve. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the dirty price curve sensitivity of the trade
   */
  public PointSensitivityBuilder dirtyPriceSensitivityWithZspread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    FixedCouponBond product = trade.getProduct();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    StandardId securityId = trade.getSecurityLink().getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    RepoCurveDiscountFactors discountFactors = provider.repoCurveDiscountFactors(
        ImmutableList.<StandardId>of(legalEntityId, securityId), product.getCurrency());
    double df = discountFactors.discountFactor(settlementDate);
    CurrencyAmount pv = presentValueWithZSpread(trade, provider, zSpread, periodic, periodPerYear);
    double notional = product.getNotional();
    PointSensitivityBuilder pvSensi = presentValueSensitivityWithZSpread(
        trade, provider, zSpread, periodic, periodPerYear).multipliedBy(1d / df / notional);
    RepoCurveZeroRateSensitivity dfSensi = discountFactors.zeroRatePointSensitivity(settlementDate)
        .multipliedBy(-pv.getAmount() / df / df / notional);
    return pvSensi.combinedWith(dfSensi);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the accrued interest of the fixed coupon bond trade.
   * <p>
   * This requires the trade information, {@code settlementDate}. 
   * Thus {@code tradeInfo} in the trade must not be empty.
   * 
   * @param trade  the trade to price
   * @return the accrued interest of the trade 
   */
  public double accruedInterest(FixedCouponBondTrade trade) {
    FixedCouponBond product = trade.getProduct();
    Schedule schedule = product.getPeriodicSchedule().createSchedule();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    if (schedule.getPeriods().get(0).getStartDate().isAfter(settlementDate)) {
      return 0d;
    }
    double notional = product.getNotional();
    int couponIndex = couponIndex(schedule, settlementDate);
    SchedulePeriod schedulePeriod = schedule.getPeriods().get(couponIndex);
    LocalDate previousAccrualDate = schedulePeriod.getStartDate();
    LocalDate nextAccrualDate = schedulePeriod.getEndDate();
    double fixedRate = product.getFixedRate();
    double accruedInterest = product.getDayCount().yearFraction(previousAccrualDate, settlementDate, schedule)
        * fixedRate * notional;
    int exCouponDays = product.getSettlementDateOffset().getDays();
    double result = 0d;
    if (exCouponDays != 0 && nextAccrualDate.minusDays(exCouponDays).isBefore(settlementDate)) {
      result = accruedInterest - notional * fixedRate * schedulePeriod.yearFraction(product.getDayCount(), schedule);
    } else {
      result = accruedInterest;
    }
    return result;
  }

  private double factorToNextCoupon(FixedCouponBondTrade trade) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    if (expanded.getPeriodicPayments().get(0).getStartDate().isAfter(settlementDate)) {
      return 0d;
    }
    double factorSpot = accruedInterest(trade);
    int couponIndex = couponIndex(product.getPeriodicSchedule().createSchedule(), settlementDate);
    double factorPeriod = expanded.getPeriodicPayments().get(couponIndex).getYearFraction();
    return (factorPeriod - factorSpot) / factorPeriod;
  }

  private int couponIndex(Schedule schedule, LocalDate date) {
    int nbCoupon = schedule.getPeriods().size();
    int couponIndex = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      if (schedule.getPeriods().get(loopcpn).getEndDate().isAfter(date)) {
        couponIndex = loopcpn;
        break;
      }
    }
    return couponIndex;
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the dirty price of the fixed coupon bond trade from yield.
   * <p>
   * The yield must be fractional.
   * The dirty price is computed based on {@link YieldConvention}, and the result is expressed in fraction. 
   * 
   * @param trade  the trade to price
   * @param yield  the yield
   * @return the dirty price of the trade 
   */
  public double dirtyPriceFromYield(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    YieldConvention yieldConvention = expanded.getYieldConvention();
    if (nbCoupon == 1) {
      if (yieldConvention.equals(YieldConvention.US_STREET) || yieldConvention.equals(YieldConvention.GERMAN_BONDS)) {
        FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(0);
        return (1d + payment.getFixedRate() * payment.getYearFraction()) / (1d + factorToNextCoupon(trade)
            * yield / ((double) product.getPeriodicSchedule().getFrequency().eventsPerYear()));
      }
    }
    if ((yieldConvention.equals(YieldConvention.US_STREET)) || (yieldConvention.equals(YieldConvention.UK_BUMP_DMO)) ||
        (yieldConvention.equals(YieldConvention.GERMAN_BONDS))) {
      return dirtyPriceFromYieldStandard(trade, yield);
    }
    if (yieldConvention.equals(YieldConvention.JAPAN_SIMPLE)) {
      LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
      LocalDate maturityDate = product.getPeriodicSchedule().getAdjustedEndDate();
      double maturity = DayCounts.ACT_365F.relativeYearFraction(settlementDate, maturityDate); // TODO check dayCount
      double cleanPrice = (1d + product.getFixedRate() * maturity) / (1d + yield * maturity);
      return dirtyPriceFromCleanPrice(trade, cleanPrice);
    }
    throw new UnsupportedOperationException("The convention " + yieldConvention.name() + " is not supported.");
  }

  // assumes notional and coupon rate are constant across the payments. 
  private double dirtyPriceFromYieldStandard(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    double factorOnPeriod = 1 + yield / ((double) product.getPeriodicSchedule().getFrequency().eventsPerYear());
    double fixedRate = product.getFixedRate();
    double pvAtFirstCoupon = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(loopcpn);
      pvAtFirstCoupon += fixedRate * payment.getYearFraction() / Math.pow(factorOnPeriod, loopcpn);
    }
    pvAtFirstCoupon += 1d / Math.pow(factorOnPeriod, nbCoupon - 1);
    return pvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNextCoupon(trade));
  }

  /**
   * Calculates the yield of the fixed coupon bond trade from dirty price.
   * <p>
   * The dirty price must be fractional. 
   * If the analytic formula is not available, the yield is computed by solving a root-finding problem with 
   * {@link #dirtyPriceFromYield(FixedCouponBondTrade, double)}.  
   * The result is also expressed in fraction. 
   * 
   * @param trade  the trade to price
   * @param dirtyPrice  the dirty price
   * @return the yield of the trade 
   */
  public double yieldFromDirtyPrice(FixedCouponBondTrade trade, double dirtyPrice) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    if (expanded.getYieldConvention().equals(YieldConvention.JAPAN_SIMPLE)) {
      double cleanPrice = cleanPriceFromDirtyPrice(trade, dirtyPrice);
      LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
      LocalDate maturityDate = product.getPeriodicSchedule().getAdjustedEndDate();
      double maturity = DayCounts.ACT_365F.relativeYearFraction(settlementDate, maturityDate); // TODO check dayCount
      return (product.getFixedRate() * maturity + (1d - cleanPrice) / maturity) / cleanPrice;
    }

    final Function1D<Double, Double> priceResidual = new Function1D<Double, Double>() {
      @Override
      public Double evaluate(final Double y) {
        return dirtyPriceFromYield(trade, y) - dirtyPrice;
      }
    };
    double[] range = ROOT_BRACKETER.getBracketedPoints(priceResidual, 0.00, 0.20);
    double yield = ROOT_FINDER.getRoot(priceResidual, range[0], range[1]);
    return yield;
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the modified duration of the fixed coupon bond trade from yield.
   * <p>
   * The modified duration is defined as the minus of the first derivative of dirty price with respect to yield, divided 
   * by the dirty price. 
   * <p>
   * The input yield must be fractional.
   * The dirty price and its derivative are computed based on {@link YieldConvention}, 
   * and the result is expressed in fraction. 
   * 
   * @param trade  the trade to price
   * @param yield  the yield
   * @return the modified duration of the trade 
   */
  public double modifiedDurationFromYield(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    YieldConvention yieldConvention = expanded.getYieldConvention();
    if (nbCoupon == 1) {
      if (yieldConvention.equals(YieldConvention.US_STREET) || yieldConvention.equals(YieldConvention.GERMAN_BONDS)) {
        double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
        return factorToNextCoupon(trade) / couponPerYear / (1.0 + factorToNextCoupon(trade) * yield / couponPerYear);
      }
    }
    if (yieldConvention.equals(YieldConvention.US_STREET) || yieldConvention.equals(YieldConvention.UK_BUMP_DMO) ||
        yieldConvention.equals(YieldConvention.GERMAN_BONDS)) {
      return modifiedDurationFromYieldStandard(trade, yield);
    }
    if (yieldConvention.equals(YieldConvention.JAPAN_SIMPLE)) {
      LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
      LocalDate maturityDate = product.getPeriodicSchedule().getAdjustedEndDate();
      double maturity = DayCounts.ACT_365F.relativeYearFraction(settlementDate, maturityDate); // TODO check dayCount
      double num = 1d + product.getFixedRate() * maturity;
      double den = 1d + yield * maturity;
      double dirtyPrice = dirtyPriceFromCleanPrice(trade, num / den);
      return num * maturity / den / den / dirtyPrice;
    }
    throw new UnsupportedOperationException("The convention " + yieldConvention.name() + " is not supported.");
  }

  // assumes notional and coupon rate are constant across the payments. 
  private double modifiedDurationFromYieldStandard(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
    double factorToNextCoupon = factorToNextCoupon(trade);
    double factorOnPeriod = 1 + yield / couponPerYear;
    double nominal = product.getNotional();
    double fixedRate = product.getFixedRate();
    double mdAtFirstCoupon = 0d;
    double pvAtFirstCoupon = 0d;
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(loopcpn);
      mdAtFirstCoupon += payment.getYearFraction() / Math.pow(factorOnPeriod, loopcpn + 1) *
          (loopcpn + factorToNextCoupon) / couponPerYear;
      pvAtFirstCoupon += payment.getYearFraction() / Math.pow(factorOnPeriod, loopcpn);
    }
    mdAtFirstCoupon *= fixedRate * nominal;
    pvAtFirstCoupon *= fixedRate * nominal;
    mdAtFirstCoupon += nominal / Math.pow(factorOnPeriod, nbCoupon) * (nbCoupon - 1 + factorToNextCoupon) /
        couponPerYear;
    pvAtFirstCoupon += nominal / Math.pow(factorOnPeriod, nbCoupon - 1);
    double md = mdAtFirstCoupon / pvAtFirstCoupon;
    return md;
  }

  /**
   * Calculates the Macaulay duration of the fixed coupon bond trade from yield.
   * <p>
   * Macaulay defined an alternative way of weighting the future cash flows. 
   * <p>
   * The input yield must be fractional.
   * The dirty price and its derivative are computed based on {@link YieldConvention}, 
   * and the result is expressed in fraction. 
   * 
   * @param trade  the trade to price
   * @param yield  the yield
   * @return the modified duration of the trade 
   */
  public double macaulayDurationFromYield(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    YieldConvention yieldConvention = expanded.getYieldConvention();
    if ((yieldConvention.equals(YieldConvention.US_STREET)) && (nbCoupon == 1)) {
      return factorToNextCoupon(trade) / product.getPeriodicSchedule().getFrequency().eventsPerYear();
    }
    if ((yieldConvention.equals(YieldConvention.US_STREET)) || (yieldConvention.equals(YieldConvention.UK_BUMP_DMO)) ||
        (yieldConvention.equals(YieldConvention.GERMAN_BONDS))) {
      return modifiedDurationFromYield(trade, yield) *
          (1d + yield / product.getPeriodicSchedule().getFrequency().eventsPerYear());
    }
    throw new UnsupportedOperationException("The convention " + yieldConvention.name() + " is not supported.");
  }

  /**
   * Calculates the convexity of the fixed coupon bond trade from yield.
   * <p>
   * The convexity is defined as the second derivative of dirty price with respect to yield, divided by the 
   * dirty price. 
   * <p>
   * The input yield must be fractional.
   * The dirty price and its derivative are computed based on {@link YieldConvention}, 
   * and the result is expressed in fraction. 
   * 
   * @param trade  the trade to price
   * @param yield  the yield
   * @return the convexity of the trade 
   */
  public double convexityFromYield(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    YieldConvention yieldConvention = expanded.getYieldConvention();
    if (nbCoupon == 1) {
      if (yieldConvention.equals(YieldConvention.US_STREET) || yieldConvention.equals(YieldConvention.GERMAN_BONDS)) {
        double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
        double factorToNextCoupon = factorToNextCoupon(trade);
        final double timeToPay = factorToNextCoupon / couponPerYear;
        final double disc = (1d + factorToNextCoupon * yield / couponPerYear);
        return 2d * timeToPay * timeToPay / (disc * disc);
      }
    }
    if (yieldConvention.equals(YieldConvention.US_STREET) || yieldConvention.equals(YieldConvention.UK_BUMP_DMO) ||
        yieldConvention.equals(YieldConvention.GERMAN_BONDS)) {
      return convexityFromYieldStandard(trade, yield);
    }
    if (yieldConvention.equals(YieldConvention.JAPAN_SIMPLE)) {
      LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
      LocalDate maturityDate = product.getPeriodicSchedule().getAdjustedEndDate();
      double maturity = DayCounts.ACT_365F.relativeYearFraction(settlementDate, maturityDate); // TODO check dayCount
      double num = 1d + product.getFixedRate() * maturity;
      double den = 1d + yield * maturity;
      double dirtyPrice = dirtyPriceFromCleanPrice(trade, num / den);
      return 2d * num * Math.pow(maturity, 2) * Math.pow(den, -3) / dirtyPrice;
    }
    throw new UnsupportedOperationException("The convention " + yieldConvention.name() + " is not supported.");
  }

  // assumes notional and coupon rate are constant across the payments. 
  private double convexityFromYieldStandard(FixedCouponBondTrade trade, double yield) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expanded = product.expand();
    int nbCoupon = expanded.getPeriodicPayments().size();
    double couponPerYear = product.getPeriodicSchedule().getFrequency().eventsPerYear();
    double factorToNextCoupon = factorToNextCoupon(trade);
    double factorOnPeriod = 1 + yield / couponPerYear;
    double nominal = product.getNotional();
    double fixedRate = product.getFixedRate();
    double cvAtFirstCoupon = 0;
    double pvAtFirstCoupon = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      FixedCouponBondPaymentPeriod payment = expanded.getPeriodicPayments().get(loopcpn);
      cvAtFirstCoupon += payment.getYearFraction() / Math.pow(factorOnPeriod, loopcpn + 2) *
          (loopcpn + factorToNextCoupon) * (loopcpn + factorToNextCoupon + 1);
      pvAtFirstCoupon += payment.getYearFraction() / Math.pow(factorOnPeriod, loopcpn);
    }
    cvAtFirstCoupon *= fixedRate * nominal / (couponPerYear * couponPerYear);
    pvAtFirstCoupon *= fixedRate * nominal;
    cvAtFirstCoupon += nominal / Math.pow(factorOnPeriod, nbCoupon + 1) * (nbCoupon - 1 + factorToNextCoupon) *
        (nbCoupon + factorToNextCoupon) / (couponPerYear * couponPerYear);
    pvAtFirstCoupon += nominal / Math.pow(factorOnPeriod, nbCoupon - 1);
    final double pv = pvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNextCoupon);
    final double cv = cvAtFirstCoupon * Math.pow(factorOnPeriod, -factorToNextCoupon) / pv;
    return cv;
  }

  //-------------------------------------------------------------------------
  private CurrencyAmount presentValueCoupon(ExpandedFixedCouponBond product, IssuerCurveDiscountFactors discountFactors) {
    double total = 0d;
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (!period.getPaymentDate().isBefore(discountFactors.getValuationDate())) {
        total += periodPricer.presentValue(period, discountFactors);
      }
    }
    return CurrencyAmount.of(product.getCurrency(), total);
  }

  private CurrencyAmount presentValueCouponFromZSpread(ExpandedFixedCouponBond product,
      IssuerCurveDiscountFactors discountFactors,
      double zSpread, boolean periodic, int periodPerYear) {
    double total = 0d;
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (!period.getPaymentDate().isBefore(discountFactors.getValuationDate())) {
        total += periodPricer.presentValue(period, discountFactors, zSpread, periodic, periodPerYear);
      }
    }
    return CurrencyAmount.of(product.getCurrency(), total);
  }

  private CurrencyAmount presentValueNominal(ExpandedFixedCouponBond product, IssuerCurveDiscountFactors discountFactors) {
    Payment nominal = product.getNominalPayment();
    return nominalPricer.presentValue(nominal, discountFactors.getDiscountFactors());
  }

  private CurrencyAmount presentValueNominalFromZSpread(ExpandedFixedCouponBond product,
      IssuerCurveDiscountFactors discountFactors,
      double zSpread, boolean periodic, int periodPerYear) {
    Payment nominal = product.getNominalPayment();
    return nominalPricer.presentValue(nominal, discountFactors.getDiscountFactors(), zSpread, periodic, periodPerYear);
  }

  //-------------------------------------------------------------------------
  private PointSensitivityBuilder presentValueSensitivityCoupon(ExpandedFixedCouponBond product,
      IssuerCurveDiscountFactors discountFactors) {
    PointSensitivityBuilder builder = PointSensitivityBuilder.none();
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (!period.getPaymentDate().isBefore(discountFactors.getValuationDate())) {
        builder = builder.combinedWith(periodPricer.presentValueSensitivity(period, discountFactors));
      }
    }
    return builder;
  }

  private PointSensitivityBuilder presentValueSensitivityCouponFromZSpread(ExpandedFixedCouponBond product,
      IssuerCurveDiscountFactors discountFactors, double zSpread, boolean periodic, int periodPerYear) {
    PointSensitivityBuilder builder = PointSensitivityBuilder.none();
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (!period.getPaymentDate().isBefore(discountFactors.getValuationDate())) {
        builder = builder.combinedWith(
            periodPricer.presentValueSensitivity(period, discountFactors, zSpread, periodic, periodPerYear));
      }
    }
    return builder;
  }

  private PointSensitivityBuilder presentValueSensitivityNominal(ExpandedFixedCouponBond product,
      IssuerCurveDiscountFactors discountFactors) {
    Payment nominal = product.getNominalPayment();
    PointSensitivityBuilder pt = nominalPricer.presentValueSensitivity(nominal, discountFactors.getDiscountFactors());
    if (pt instanceof ZeroRateSensitivity) {
      return IssuerCurveZeroRateSensitivity.of((ZeroRateSensitivity) pt, discountFactors.getLegalEntityGroup());
    }
    return pt; // NoPointSensitivity
  }

  private PointSensitivityBuilder presentValueSensitivityNominalFromZSpread(ExpandedFixedCouponBond product,
      IssuerCurveDiscountFactors discountFactors, double zSpread, boolean periodic, int periodPerYear) {
    Payment nominal = product.getNominalPayment();
    PointSensitivityBuilder pt = nominalPricer.presentValueSensitivity(
        nominal, discountFactors.getDiscountFactors(), zSpread, periodic, periodPerYear);
    if (pt instanceof ZeroRateSensitivity) {
      return IssuerCurveZeroRateSensitivity.of((ZeroRateSensitivity) pt, discountFactors.getLegalEntityGroup());
    }
    return pt; // NoPointSensitivity
  }
}
