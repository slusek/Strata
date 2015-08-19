/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.bond;

import java.time.LocalDate;

import com.google.common.collect.ImmutableList;
import com.opengamma.analytics.math.rootfinding.BracketRoot;
import com.opengamma.analytics.math.rootfinding.BrentSingleRootFinder;
import com.opengamma.analytics.math.rootfinding.RealSingleRootFinder;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.finance.rate.bond.ExpandedFixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondTrade;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.impl.bond.DiscountingFixedCouponBondPaymentPeriodPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

public class DiscountingFixedCouponBondTradePricer {
  
  private static final DiscountingPaymentPricer NOMINAL_PRICER = DiscountingPaymentPricer.DEFAULT;
  
  private static final DiscountingFixedCouponBondPaymentPeriodPricer PERIOD_PRICER =
      DiscountingFixedCouponBondPaymentPeriodPricer.DEFAULT;

  /**
   * The root bracket used for yield finding.
   */
  private static final BracketRoot BRACKETER = new BracketRoot();
  /**
   * The root finder used for yield finding.
   */
  private static final RealSingleRootFinder ROOT_FINDER = new BrentSingleRootFinder();
  /**
   * Brackets a root
   */
  private static final BracketRoot ROOT_BRACKETER = new BracketRoot();

  public DiscountingFixedCouponBondTradePricer() {
  }

  //-------------------------------------------------------------------------
  public CurrencyAmount presentValue(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    ExpandedFixedCouponBond product = trade.getProduct().expand();
    DiscountFactors discountFactors = provider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency()).getDiscountFactors();
    CurrencyAmount pvNominal = presentValueNominal(product, discountFactors);
    CurrencyAmount pvCoupon = presentValueCoupon(product, discountFactors);
    return pvNominal.plus(pvCoupon);
  }

  public double dirtyPriceFromCurves(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    FixedCouponBond product = trade.getProduct();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    CurrencyAmount pv = presentValue(trade, provider);
    StandardId securityId = trade.getSecurityLink().getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    double df = provider.repoCurveDiscountFactors(
        ImmutableList.<StandardId>of(legalEntityId, securityId), product.getCurrency()).discountFactor(settlementDate);
    double notional = product.getNotionalAmount().getAmount();
    return pv.getAmount() / df / notional;
  }

  public double dirtyPriceFromCleanPrice(FixedCouponBondTrade trade, double cleanPrice) {
    double notional = trade.getProduct().getNotionalAmount().getAmount();
    double accruedInterest = accruedInterest(trade);
    return cleanPrice + accruedInterest / notional;
  }

  public CurrencyAmount presentValueFromZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    ExpandedFixedCouponBond product = trade.getProduct().expand();
    DiscountFactors discountFactors = provider.issuerCurveDiscountFactors(
        product.getLegalEntityId(), product.getCurrency()).getDiscountFactors();
    CurrencyAmount pvNominal = presentValueNominal(product, discountFactors, zSpread, periodic, periodPerYear);
    CurrencyAmount pvCoupon = presentValueCoupon(product, discountFactors, zSpread, periodic, periodPerYear);
    return pvNominal.plus(pvCoupon);
  }

//  public CurrencyAmount presentValueFromZSpread(Bond bond, RatesProvider provider, double zSpread, boolean periodic,  // TODO how to add shift to curve?
//      int periodPerYear) {
//    if (periodic) {
//      //      IssuerProviderInterface issuerShifted = new IssuerProviderIssuerDecoratedSpreadPeriodic(issuerMulticurves,
//      //          bond.getIssuerEntity(), zSpread, periodPerYear);
//      RatesProvider providerShifted = null;
//      return presentValue(bond, providerShifted);
//    }
//    return presentValueFromZSpread(bond, provider, zSpread);
//  }
//
//  public double zSpreadFromCurvesAndPV(Bond bond, RatesProvider provider, CurrencyAmount pv, boolean periodic,
//      int periodPerYear) {
//    Currency ccy = bond.getCurrency();
//
//    final Function1D<Double, Double> residual = new Function1D<Double, Double>() {
//      @Override
//      public Double evaluate(final Double z) {
//        return presentValueFromZSpread(bond, provider, z, periodic, periodPerYear).getAmount() -
//            pv.getAmount();
//      }
//    };
//
//    double[] range = ROOT_BRACKETER.getBracketedPoints(residual, -0.01, 0.01); // Starting range is [-1%, 1%]
//    return ROOT_FINDER.getRoot(residual, range[0], range[1]);
//  }
//
//  //-------------------------------------------------------------------------
//  public PointSensitivityBuilder presentValueSensitivity(
//      Bond bond,
//      RatesProvider provider) {
//    DecoratedRatesProvider decoratedProvider =
//        DecoratedRatesProvider.of(provider, bond.getCurrency(), bond.getLegalEntity());
//
//    PointSensitivityBuilder builder = PointSensitivityBuilder.none();
//    BiFunction<PaymentPeriod, RatesProvider, PointSensitivityBuilder> periodFn = paymentPeriodPricer::presentValueSensitivity;
//    for (PaymentPeriod period : bond.getPaymentPeriods()) {
//      if (!period.getPaymentDate().isBefore(decoratedProvider.getValuationDate())) {
//        builder = builder.combinedWith(periodFn.apply(period, decoratedProvider));
//      }
//    }
//    BiFunction<PaymentEvent, RatesProvider, PointSensitivityBuilder> eventFn = paymentEventPricer::presentValueSensitivity;
//    for (PaymentEvent event : bond.getPaymentEvents()) {
//      if (!event.getPaymentDate().isBefore(decoratedProvider.getValuationDate())) {
//        builder = builder.combinedWith(eventFn.apply(event, decoratedProvider));
//      }
//    }
//    return builder;
//  }

  //-------------------------------------------------------------------------
  private CurrencyAmount presentValueCoupon(ExpandedFixedCouponBond product, DiscountFactors discountFactors) {
    double total = 0d;
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (!period.getPaymentDate().isBefore(discountFactors.getValuationDate())) {
        total += PERIOD_PRICER.presentValue(period, discountFactors);
      }
    }
    return CurrencyAmount.of(product.getCurrency(), total);
  }

  private CurrencyAmount presentValueNominal(ExpandedFixedCouponBond product, DiscountFactors discountFactors) {
    Payment nominal = product.getNominalPayment();
    return NOMINAL_PRICER.presentValue(nominal, discountFactors);
  }

  private CurrencyAmount presentValueCoupon(ExpandedFixedCouponBond product, DiscountFactors discountFactors,
      double zSpread, boolean periodic, int periodPerYear) {
    double total = 0d;
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (!period.getPaymentDate().isBefore(discountFactors.getValuationDate())) {
        total += PERIOD_PRICER.presentValue(period, discountFactors, zSpread, periodic, periodPerYear);
      }
    }
    return CurrencyAmount.of(product.getCurrency(), total);
  }

  private CurrencyAmount presentValueNominal(ExpandedFixedCouponBond product, DiscountFactors discountFactors,
      double zSpread, boolean periodic, int periodPerYear) {
    Payment nominal = product.getNominalPayment();
    return NOMINAL_PRICER.presentValue(nominal, discountFactors, zSpread, periodic, periodPerYear);
  }

  //-------------------------------------------------------------------------
  public double accruedInterest(FixedCouponBondTrade trade) {
    FixedCouponBond product = trade.getProduct();
    ExpandedFixedCouponBond expand = product.expand();
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    double notional = product.getNotionalAmount().getAmount();

    int nbCoupon = expand.getPeriodicPayments().size();
    int couponIndex = 0;
    for (int loopcpn = 0; loopcpn < nbCoupon; loopcpn++) {
      if (expand.getPeriodicPayments().get(loopcpn).getEndDate().isAfter(settlementDate)) {
        couponIndex = loopcpn;
        break;
      }
    }
    FixedCouponBondPaymentPeriod paymentPeriod = expand.getPeriodicPayments().get(couponIndex);
    LocalDate previousAccrualDate = paymentPeriod.getStartDate();
    LocalDate nextAccrualDate = paymentPeriod.getEndDate();

    //    boolean isEom = product.getPeriodicSchedule().createSchedule().isEndOfMonthConvention();
    //    StubConvention stub = product.getPeriodicSchedule().getStubConvention().get();
    DayCount dayCount = expand.getDayCount();
    double fixedRate = product.getFixedRate();
    double accruedInterest = dayCount.yearFraction(previousAccrualDate, settlementDate) * fixedRate * notional; // TODO eom with previousAccrualDate and nextAccrualDate
    int exCouponDays = expand.getSettlementDateOffset().getDays();
    double result = 0d;
    if (exCouponDays != 0 && nextAccrualDate.minusDays(exCouponDays).isBefore(settlementDate)) {
      result = accruedInterest - notional;
    } else {
      result = accruedInterest;
    }
    return result;
  }
}
