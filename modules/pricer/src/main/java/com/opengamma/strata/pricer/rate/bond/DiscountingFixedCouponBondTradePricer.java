/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.bond;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.Payment;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.finance.rate.bond.FixedCouponBond;
import com.opengamma.strata.finance.rate.bond.FixedCouponBondTrade;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.RepoCurveZeroRateSensitivity;
import com.opengamma.strata.market.sensitivity.ZeroRateSensitivity;
import com.opengamma.strata.market.value.RepoCurveDiscountFactors;
import com.opengamma.strata.pricer.DiscountingPaymentPricer;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

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
      DiscountingFixedCouponBondProductPricer.DEFAULT, DiscountingPaymentPricer.DEFAULT);
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
  public DiscountingFixedCouponBondTradePricer(DiscountingFixedCouponBondProductPricer productPricer,
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
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValue(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    CurrencyAmount pvProduct = productPricer.presentValue(trade.getProduct(), provider);
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
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param periodic  if true, the spread is added to periodic compounded rates,
   *  if false, the spread is added to continuously compounded rates
   * @param periodsPerYear  the number of periods per year
   * @return the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodsPerYear) {
    CurrencyAmount pvProduct =
        productPricer.presentValueWithZSpread(trade.getProduct(), provider, zSpread, periodic, periodsPerYear);
    return presentValueFromProductPresentValue(trade, provider, pvProduct);
  }

  /**
   * Calculates the present value of the fixed coupon bond trade from the present value of the underlying fixed
   * coupon bond product. 
   * <p>
   * It is assumed that the payment currency of the bond product is the same as the currency of the up-front payment of 
   * the trade. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param productPresentValue  the present value of the product
   * @return  the present value of the fixed coupon bond trade
   */
  public CurrencyAmount presentValueFromProductPresentValue(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      CurrencyAmount productPresentValue) {
    CurrencyAmount pvProduct = productPresentValue.multipliedBy(trade.getQuantity());
    CurrencyAmount pvPayment = presentValuePayment(trade, provider);
    return pvProduct.plus(pvPayment);
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
    PointSensitivityBuilder sensiProduct = productPricer.presentValueSensitivity(trade.getProduct(), provider);
    return presnetValueSensitivityFromProductPresentValueSensitivity(trade, provider, sensiProduct);
  }

  /**
   * Calculates the present value sensitivity of the fixed coupon bond trade with z-spread.
   * <p>
   * The present value sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic
   * compounded rates of the discounting curve.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param zSpread  the z-spread
   * @param periodic  if true, the spread is added to periodic compounded rates,
   *  if false, the spread is added to continuously compounded rates
   * @param periodsPerYear  the number of periods per year
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivityBuilder presentValueSensitivityWithZSpread(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodsPerYear) {
    PointSensitivityBuilder sensiProduct = productPricer
        .presentValueSensitivityWithZSpread(trade.getProduct(), provider, zSpread, periodic, periodsPerYear);
    return presnetValueSensitivityFromProductPresentValueSensitivity(trade, provider, sensiProduct);
  }

  /**
   * Calculates the present value sensitivity of the fixed coupon bond trade from present value sensitivity of the 
   * underlying fixed coupon product. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param productPresnetValueSensitivity  the present value sensitivity of the product
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivityBuilder presnetValueSensitivityFromProductPresentValueSensitivity(
      FixedCouponBondTrade trade,
      LegalEntityDiscountingProvider provider,
      PointSensitivityBuilder productPresnetValueSensitivity) {
    PointSensitivityBuilder sensiProduct = productPresnetValueSensitivity.multipliedBy(trade.getQuantity());
    PointSensitivityBuilder sensiPayment = presentValueSensitivityPayment(trade, provider);
    return sensiProduct.combinedWith(sensiPayment);
  }

  //-------------------------------------------------------------------------
  private CurrencyAmount presentValuePayment(FixedCouponBondTrade trade, LegalEntityDiscountingProvider provider) {
    FixedCouponBond product = trade.getProduct();
    RepoCurveDiscountFactors discountFactors = provider.repoCurveDiscountFactors(
        product.getLegalEntityId(), trade.getSecurity().getStandardId(), product.getCurrency());
    return paymentPricer.presentValue(trade.getPayment(), discountFactors.getDiscountFactors());
  }

  private PointSensitivityBuilder presentValueSensitivityPayment(FixedCouponBondTrade trade,
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
