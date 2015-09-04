package com.opengamma.strata.pricer.rate.bond;

import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.finance.rate.bond.BondFuture;
import com.opengamma.strata.finance.rate.bond.BondFutureTrade;
import com.opengamma.strata.market.sensitivity.PointSensitivities;
import com.opengamma.strata.pricer.rate.LegalEntityDiscountingProvider;

/**
 * Pricer implementation for bond future trades.
 * <p>
 * This function provides the ability to price a {@link BondFutureTrade}.
 */
public final class DiscountingBondFutureTradetPricer extends AbstractBondFutureTradePricer {

  /**
   * Default implementation.
   */
  public static final DiscountingBondFutureTradetPricer DEFAULT =
      new DiscountingBondFutureTradetPricer(DiscountingBondFutureProductPricer.DEFAULT);

  /**
   * Underlying pricer.
   */
  private final DiscountingBondFutureProductPricer productPricer;

  /**
   * Creates an instance.
   * 
   * @param productPricer  the pricer for {@link BondFuture}
   */
  public DiscountingBondFutureTradetPricer(DiscountingBondFutureProductPricer productPricer) {
    this.productPricer = ArgChecker.notNull(productPricer, "productPricer");
  }

  //-------------------------------------------------------------------------
  @Override
  protected DiscountingBondFutureProductPricer getProductPricer() {
    return productPricer;
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the price of the bond future trade.
   * <p>
   * The price of the trade is the price on the valuation date.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the price of the trade, in decimal form
   */
  public double price(BondFutureTrade trade, LegalEntityDiscountingProvider provider) {
    return productPricer.price(trade.getSecurity().getProduct(), provider);
  }

  /**
   * Calculates the price of the bond future trade with z-spread.
   * <p>
   * The price of the trade is the price on the valuation date.
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
   * @return the price of the trade, in decimal form
   */
  public double priceWithSpread(
      BondFutureTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    return productPricer.priceWithSpread(trade.getSecurity().getProduct(), provider, zSpread, periodic, periodPerYear);
  }

  /**
   * Calculates the present value of the bond future trade.
   * <p>
   * The present value of the product is the value on the valuation date.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param referencePrice  the price with respect to which the margining should be done. The reference price is
   *   the trade date before any margining has taken place and the price used for the last margining otherwise.
   * @return the present value
   */
  public CurrencyAmount presentValue(BondFutureTrade trade, LegalEntityDiscountingProvider provider,
      double referencePrice) {
    double price = price(trade, provider);
    return presentValue(trade, price, referencePrice);
  }

  /**
   * Calculates the present value of the bond future trade with z-spread.
   * <p>
   * The present value of the product is the value on the valuation date.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates 
   * of the issuer discounting curve. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param referencePrice  the price with respect to which the margining should be done. The reference price is
   *   the trade date before any margining has taken place and the price used for the last margining otherwise.
   * @param zSpread  the z-spread
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the present value
   */
  public CurrencyAmount presentValueWithSpread(
      BondFutureTrade trade,
      LegalEntityDiscountingProvider provider,
      double referencePrice,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    double price = priceWithSpread(trade, provider, zSpread, periodic, periodPerYear);
    return presentValue(trade, price, referencePrice);
  }

  /**
   * Calculates the present value sensitivity of the bond future trade.
   * <p>
   * The present value sensitivity of the trade is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the present value curve sensitivity of the trade
   */
  public PointSensitivities presentValueSensitivity(BondFutureTrade trade, LegalEntityDiscountingProvider provider) {
    BondFuture product = trade.getSecurity().getProduct();
    PointSensitivities priceSensi = productPricer.priceSensitivity(product, provider);
    PointSensitivities marginIndexSensi = productPricer.marginIndexSensitivity(product, priceSensi);
    return marginIndexSensi.multipliedBy(trade.getQuantity());
  }

  /**
   * Calculates the present value sensitivity of the bond future trade with z-spread.
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
  public PointSensitivities presentValueSensitivityWithSpread(
      BondFutureTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    BondFuture product = trade.getSecurity().getProduct();
    PointSensitivities priceSensi =
        productPricer.priceSensitivityWithSpread(product, provider, zSpread, periodic, periodPerYear);
    PointSensitivities marginIndexSensi = productPricer.marginIndexSensitivity(product, priceSensi);
    return marginIndexSensi.multipliedBy(trade.getQuantity());
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the par spread of the bond future trade.
   * <p>
   * The par spread is defined in the following way. When the reference price (or market quote)
   * is increased by the par spread, the present value of the trade is zero.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param referencePrice  the price with respect to which the margining should be done. The reference price is
   *   the trade date before any margining has taken place and the price used for the last margining otherwise.
   * @return the par spread.
   */
  public double parSpread(BondFutureTrade trade, LegalEntityDiscountingProvider provider, double referencePrice) {
    return price(trade, provider) - referencePrice;
  }

  /**
   * Calculates the par spread of the bond future trade with z-spread.
   * <p>
   * The par spread is defined in the following way. When the reference price (or market quote)
   * is increased by the par spread, the present value of the trade is zero.
   * <p>
   * The z-spread is a parallel shift applied to continuously compounded rates or periodic compounded rates 
   * of the issuer discounting curve. 
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @param referencePrice  the price with respect to which the margining should be done. The reference price is
   *   the trade date before any margining has taken place and the price used for the last margining otherwise.
   * @param zSpread  the z-spread
   * @param periodic  If true, the spread is added to periodic compounded rates. If false, the spread is added to 
   * continuously compounded rates
   * @param periodPerYear  the number of periods per year
   * @return the par spread.
   */
  public double parSpreadWithSpread(
      BondFutureTrade trade,
      LegalEntityDiscountingProvider provider,
      double referencePrice,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    return priceWithSpread(trade, provider, zSpread, periodic, periodPerYear) - referencePrice;
  }

  /**
   * Calculates the par spread sensitivity of the bond future trade.
   * <p>
   * The par spread sensitivity of the trade is the sensitivity of the par spread to
   * the underlying curves.
   * 
   * @param trade  the trade to price
   * @param provider  the rates provider
   * @return the par spread curve sensitivity of the trade
   */
  public PointSensitivities parSpreadSensitivity(BondFutureTrade trade, LegalEntityDiscountingProvider provider) {
    return productPricer.priceSensitivity(trade.getSecurity().getProduct(), provider);
  }

  /**
   * Calculates the par spread sensitivity of the bond future trade with z-spread.
   * <p>
   * The par spread sensitivity of the trade is the sensitivity of the par spread to
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
   * @return the par spread curve sensitivity of the trade
   */
  public PointSensitivities parSpreadSensitivityWithSpread(
      BondFutureTrade trade,
      LegalEntityDiscountingProvider provider,
      double zSpread,
      boolean periodic,
      int periodPerYear) {
    return productPricer.priceSensitivityWithSpread(
        trade.getSecurity().getProduct(), provider, zSpread, periodic, periodPerYear);
  }

}
