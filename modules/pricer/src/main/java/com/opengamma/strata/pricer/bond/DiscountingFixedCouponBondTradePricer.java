/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.bond;

import java.time.LocalDate;

import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.RepoCurveZeroRateSensitivity;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.value.CompoundedRateType;
import com.opengamma.strata.market.value.IssuerCurveDiscountFactors;
import com.opengamma.strata.market.value.RepoCurveDiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;
import com.opengamma.strata.product.Security;
import com.opengamma.strata.product.bond.ExpandedFixedCouponBond;
import com.opengamma.strata.product.bond.FixedCouponBond;
import com.opengamma.strata.product.bond.FixedCouponBondPaymentPeriod;
import com.opengamma.strata.product.bond.FixedCouponBondTrade;

/**
 * Pricer for for rate fixed coupon bond trades.
 * <p>
 * This function provides the ability to price a {@link FixedCouponBondTrade}.
 */
public class DiscountingFixedCouponBondTradePricer {

  /**
   * Default implementation.
   */
  public static final DiscountingFixedCouponBondTradePricer DEFAULT = new DiscountingFixedCouponBondTradePricer(
      DiscountingFixedCouponBondProductPricer.DEFAULT,
      DiscountingPaymentPricer.DEFAULT);

  /**
   * Pricer for {@link FixedCouponBond}.
   */
  private final DiscountingFixedCouponBondProductPricer productPricer;
  /**
   * Pricer for {@link Payment}.
   */
  private final DiscountingPaymentPricer paymentPricer;

  /**
   * Creates an instance.
   * 
   * @param productPricer  the pricer for {@link FixedCouponBond}
   * @param paymentPricer  the pricer for {@link Payment}
  */
  public DiscountingFixedCouponBondTradePricer(
      DiscountingFixedCouponBondProductPricer productPricer,
      DiscountingPaymentPricer paymentPricer) {

    this.productPricer = ArgChecker.notNull(productPricer, "productPricer");
    this.paymentPricer = ArgChecker.notNull(paymentPricer, "paymentPricer");
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the fixed coupon bond trade.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * <p>
   * Coupon payments of the underlying product are considered based on the settlement date of the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValue(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    CurrencyAmount pvProduct = productPricer.presentValue(trade.getProduct(), provider, settlementDate);
    return presentValueFromProductPresentValue(trade, provider, pvProduct);
  }

  /**
   * Calculates the present value of the fixed coupon bond trade with z-spread.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic
   * compounded rates of the discounting curve.
   * <p>
   * Coupon payments of the underlying product are considered based on the settlement date of the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param compoundedRateType  the compounded rate type
   * @param periodsPerYear  the number of periods per year
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    CurrencyAmount pvProduct = productPricer.presentValueWithZSpread(
        trade.getProduct(), provider, zSpread, compoundedRateType, periodsPerYear, settlementDate);
    return presentValueFromProductPresentValue(trade, provider, pvProduct);
  }

  private CurrencyAmount presentValueFromProductPresentValue(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      CurrencyAmount productPresentValue) {
    CurrencyAmount pvProduct = productPresentValue.multipliedBy(trade.getQuantity());
    CurrencyAmount pvPayment = presentValuePayment(trade, provider);
    return pvProduct.plus(pvPayment);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the fixed coupon bond trade from the clean price of the underlying product.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * <p>
   * Coupon payments of the underlying product are considered based on the settlement date of the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param cleanPrice  the clean price
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueFromCleanPrice(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double cleanPrice) {

    Security<FixedCouponBond> security = trade.getSecurity();
    FixedCouponBond product = security.getProduct();
    LocalDate standardSettlementDate = product.getSettlementDateOffset().adjust(provider.getValuationDate());
    LocalDate tradeSettlementDate = trade.getTradeInfo().getSettlementDate().get();
    StandardId securityId = security.getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    Currency currency = product.getCurrency();
    double df = provider.repoCurveDiscountFactors(securityId, legalEntityId, currency).discountFactor(standardSettlementDate);
    double pvStandard =
        (cleanPrice * product.getNotional() + productPricer.accruedInterest(product, standardSettlementDate)) * df;
    if (standardSettlementDate.isEqual(tradeSettlementDate)) {
      return presentValueFromProductPresentValue(trade, provider, CurrencyAmount.of(currency, pvStandard));
    }
    // check coupon payment between two settlement dates
    IssuerCurveDiscountFactors discountFactors = provider.issuerCurveDiscountFactors(legalEntityId, currency);
    ExpandedFixedCouponBond expanded = product.expand();
    boolean exCoupon = product.getExCouponPeriod().getDays() != 0;
    double pvDiff = 0d;
    if (standardSettlementDate.isAfter(tradeSettlementDate)) {
      pvDiff = productPricer.presentValueCoupon(
          expanded, discountFactors, tradeSettlementDate, standardSettlementDate, exCoupon);
    } else {
      pvDiff = -productPricer.presentValueCoupon(
          expanded, discountFactors, standardSettlementDate, tradeSettlementDate, exCoupon);
    }
    return presentValueFromProductPresentValue(trade, provider, CurrencyAmount.of(currency, pvStandard + pvDiff));
  }

  /**
   * Calculates the present value of the fixed coupon bond trade with z-spread from the
   * clean price of the underlying product.
   * <p>
   * The present value of the trade is the value on the valuation date.
   * The result is expressed using the payment currency of the bond.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic
   * compounded rates of the discounting curve.
   * <p>
   * Coupon payments of the underlying product are considered based on the settlement date of the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param cleanPrice  the clean price
   * @param zSpread  the z-spread
   * @param compoundedRateType  the compounded rate type
   * @param periodsPerYear  the number of periods per year
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueFromCleanPriceWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double cleanPrice,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    Security<FixedCouponBond> security = trade.getSecurity();
    FixedCouponBond product = security.getProduct();
    LocalDate standardSettlementDate = product.getSettlementDateOffset().adjust(provider.getValuationDate());
    LocalDate tradeSettlementDate = trade.getTradeInfo().getSettlementDate().get();
    StandardId securityId = security.getStandardId();
    StandardId legalEntityId = product.getLegalEntityId();
    Currency currency = product.getCurrency();
    double df = provider.repoCurveDiscountFactors(securityId, legalEntityId, currency).discountFactor(
        standardSettlementDate);
    double pvStandard =
        (cleanPrice * product.getNotional() + productPricer.accruedInterest(product, standardSettlementDate)) * df;
    if (standardSettlementDate.isEqual(tradeSettlementDate)) {
      return presentValueFromProductPresentValue(trade, provider, CurrencyAmount.of(currency, pvStandard));
    }
    // check coupon payment between two settlement dates
    IssuerCurveDiscountFactors discountFactors = provider.issuerCurveDiscountFactors(legalEntityId, currency);
    ExpandedFixedCouponBond expanded = product.expand();
    boolean exCoupon = product.getExCouponPeriod().getDays() != 0;
    double pvDiff = 0d;
    if (standardSettlementDate.isAfter(tradeSettlementDate)) {
      pvDiff = productPricer.presentValueCouponWithZSpread(
          expanded,
          discountFactors,
          tradeSettlementDate,
          standardSettlementDate,
          zSpread,
          compoundedRateType,
          periodsPerYear,
          exCoupon);
    } else {
      pvDiff = -productPricer.presentValueCouponWithZSpread(
          expanded,
          discountFactors,
          standardSettlementDate,
          tradeSettlementDate,
          zSpread,
          compoundedRateType,
          periodsPerYear,
          exCoupon);
    }
    return presentValueFromProductPresentValue(trade, provider, CurrencyAmount.of(currency, pvStandard + pvDiff));
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the fixed coupon bond trade.
   * <p>
   * The present value sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * <p>
   * Coupon payments of the underlying product are considered based on the settlement date of the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivityBuilder presentValueSensitivity(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider) {

    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    PointSensitivityBuilder sensiProduct = productPricer.presentValueSensitivity(
        trade.getProduct(), provider, settlementDate);
    return presentValueSensitivityFromProductPresentValueSensitivity(trade, provider, sensiProduct);
  }

  /**
   * Calculates the present value sensitivity of the fixed coupon bond trade with z-spread.
   * <p>
   * The present value sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic
   * compounded rates of the discounting curve.
   * <p>
   * Coupon payments of the underlying product are considered based on the settlement date of the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param compoundedRateType  the compounded rate type
   * @param periodsPerYear  the number of periods per year
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivityBuilder presentValueSensitivityWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    PointSensitivityBuilder sensiProduct = productPricer.presentValueSensitivityWithZSpread(
        trade.getProduct(), provider, zSpread, compoundedRateType, periodsPerYear, settlementDate);
    return presentValueSensitivityFromProductPresentValueSensitivity(trade, provider, sensiProduct);
  }

  private PointSensitivityBuilder presentValueSensitivityFromProductPresentValueSensitivity(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      PointSensitivityBuilder productPresnetValueSensitivity) {

    PointSensitivityBuilder sensiProduct = productPresnetValueSensitivity.multipliedBy(trade.getQuantity());
    PointSensitivityBuilder sensiPayment = presentValueSensitivityPayment(trade, provider);
    return sensiProduct.combinedWith(sensiPayment);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the currency exposure of the fixed coupon bond trade.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the currency exposure of the fixed coupon bond trade
   */
  public MultiCurrencyAmount currencyExposure(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {

    return MultiCurrencyAmount.of(presentValue(trade, provider));
  }

  /**
   * Calculates the currency exposure of the fixed coupon bond trade with z-spread.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param compoundedRateType  the compounded rate type
   * @param periodsPerYear  the number of periods per year
   * @return the currency exposure of the fixed coupon bond trade
   */
  public MultiCurrencyAmount currencyExposureWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      CompoundedRateType compoundedRateType,
      int periodsPerYear) {

    return MultiCurrencyAmount.of(presentValueWithZSpread(trade, provider, zSpread, compoundedRateType, periodsPerYear));
  }

  /**
   * Calculates the current of the fixed coupon bond trade.
   * 
   * @param trade  the trade to price
   * @param valuationDate  the valuation date
   * @return the current cash amount
   */
  public CurrencyAmount currentCash(FixedCouponBondTrade trade, LocalDate valuationDate) {
    Payment upfront = trade.getPayment();
    Currency currency = upfront.getCurrency(); // assumes single currency is involved in trade
    CurrencyAmount currentCash = CurrencyAmount.zero(currency);
    if (upfront.getDate().equals(valuationDate)) {
      currentCash = currentCash.plus(CurrencyAmount.of(currency, upfront.getAmount()));
    }
    LocalDate settlementDate = trade.getTradeInfo().getSettlementDate().get();
    FixedCouponBond product = trade.getProduct();
    if (!settlementDate.isAfter(valuationDate)) {
      ExpandedFixedCouponBond expanded = product.expand();
      double cashCoupon = product.getExCouponPeriod().getDays() != 0 ? 0d : currentCashCouponPayment(expanded, valuationDate);
      Payment payment = expanded.getNominalPayment();
      double cashNominal = payment.getDate().isEqual(valuationDate) ? payment.getAmount() : 0d;
      currentCash = currentCash.plus(CurrencyAmount.of(currency, (cashCoupon + cashNominal) * trade.getQuantity()));
    }
    return currentCash;
  }

  private double currentCashCouponPayment(ExpandedFixedCouponBond product, LocalDate referenceDate) {
    double cash = 0d;
    for (FixedCouponBondPaymentPeriod period : product.getPeriodicPayments()) {
      if (period.getPaymentDate().isEqual(referenceDate)) {
        cash += period.getFixedRate() * period.getNotional() * period.getYearFraction();
      }
    }
    return cash;
  }

  //-------------------------------------------------------------------------
  private CurrencyAmount presentValuePayment(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    FixedCouponBond product = trade.getProduct();
    RepoCurveDiscountFactors discountFactors = provider.repoCurveDiscountFactors(
        product.getLegalEntityId(), trade.getSecurity().getStandardId(), product.getCurrency());
    return paymentPricer.presentValue(trade.getPayment(), discountFactors.getDiscountFactors());
  }

  private PointSensitivityBuilder presentValueSensitivityPayment(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider) {

    FixedCouponBond product = trade.getProduct();
    RepoCurveDiscountFactors discountFactors = provider.repoCurveDiscountFactors(
        product.getLegalEntityId(), trade.getSecurity().getStandardId(), product.getCurrency());
    PointSensitivityBuilder pt = paymentPricer.presentValueSensitivity(
        trade.getPayment(), discountFactors.getDiscountFactors());
    if (pt instanceof ZeroRateSensitivity) {
      return RepoCurveZeroRateSensitivity.of((ZeroRateSensitivity) pt, discountFactors.getBondGroup());
    }
    return pt; // NoPointSensitivity
  }

}
