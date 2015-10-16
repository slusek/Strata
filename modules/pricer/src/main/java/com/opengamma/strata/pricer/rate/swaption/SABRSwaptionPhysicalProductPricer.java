package com.opengamma.strata.pricer.rate.swaption;

import java.time.ZonedDateTime;
import java.util.List;

import com.opengamma.strata.basics.LongShort;
import com.opengamma.strata.basics.PayReceive;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.currency.MultiCurrencyAmount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.DoublesPair;
import com.opengamma.strata.finance.rate.swap.ExpandedSwap;
import com.opengamma.strata.finance.rate.swap.ExpandedSwapLeg;
import com.opengamma.strata.finance.rate.swap.Swap;
import com.opengamma.strata.finance.rate.swap.SwapLegType;
import com.opengamma.strata.finance.rate.swap.SwapProduct;
import com.opengamma.strata.finance.rate.swaption.ExpandedSwaption;
import com.opengamma.strata.finance.rate.swaption.SettlementType;
import com.opengamma.strata.finance.rate.swaption.SwaptionProduct;
import com.opengamma.strata.market.sensitivity.PointSensitivityBuilder;
import com.opengamma.strata.market.sensitivity.SwaptionSABRSensitivity;
import com.opengamma.strata.pricer.impl.option.BlackFormulaRepository;
import com.opengamma.strata.pricer.rate.RatesProvider;
import com.opengamma.strata.pricer.rate.swap.DiscountingSwapProductPricer;

public class SABRSwaptionPhysicalProductPricer {

  /**
   * Default implementation.
   */
  public static final SABRSwaptionPhysicalProductPricer DEFAULT =
      new SABRSwaptionPhysicalProductPricer(DiscountingSwapProductPricer.DEFAULT);
  /** 
   * Pricer for {@link SwapProduct}. 
   */
  private final DiscountingSwapProductPricer swapPricer;

  /**
   * Creates an instance.
   * 
   * @param swapPricer  the pricer for {@link Swap}
   */
  public SABRSwaptionPhysicalProductPricer(DiscountingSwapProductPricer swapPricer) {
    this.swapPricer = ArgChecker.notNull(swapPricer, "swap pricer");
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value of the swaption product.
   * <p>
   * The result is expressed using the currency of the swapion.
   * 
   * @param swaption  the product to price
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the SABR volatility provider
   * @return the present value of the swaption product
   */
  public CurrencyAmount presentValue(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SABRVolatilitySwaptionProvider volatilityProvider) {

    ExpandedSwaption expanded = swaption.expand();
    validate(ratesProvider, expanded, volatilityProvider);
    ZonedDateTime expiryDateTime = expanded.getExpiryDateTime();
    double expiry = volatilityProvider.relativeTime(expiryDateTime);
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return CurrencyAmount.of(fixedLeg.getCurrency(), 0d);
    }
    double tenor = volatilityProvider.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double shift = volatilityProvider.getParameters().getShift(DoublesPair.of(expiry, tenor));
    double forward = swapPricer.parRate(underlying, ratesProvider);
    double pvbp = swapPricer.getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double volatility = volatilityProvider.getVolatility(expiryDateTime, tenor, strike, forward);
    boolean isCall = (fixedLeg.getPayReceive() == PayReceive.PAY);
    // Payer at strike is exercise when rate > strike, i.e. call on rate
    double price =
        Math.abs(pvbp) * BlackFormulaRepository.price(forward + shift, strike + shift, expiry, volatility, isCall);
    double pv = price * ((expanded.getLongShort() == LongShort.LONG) ? 1d : -1d);
    return CurrencyAmount.of(fixedLeg.getCurrency(), pv);
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the currency exposure of the swaption product.
   * 
   * @param swaption  the swaption to price
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the Black volatility provider
   * @return the present value of the swaption product
   */
  public MultiCurrencyAmount currencyExposure(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SABRVolatilitySwaptionProvider volatilityProvider) {
    return MultiCurrencyAmount.of(presentValue(swaption, ratesProvider, volatilityProvider));
  }

  //-------------------------------------------------------------------------
  /**
   * Computes the implied Black volatility of the swaption.
   * 
   * @param swaption  the product to price
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the Black volatility provider
   * @return the Black implied volatility associated to the swaption
   */
  public double impliedVolatility(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SABRVolatilitySwaptionProvider volatilityProvider) {

    ExpandedSwaption expanded = swaption.expand();
    validate(ratesProvider, expanded, volatilityProvider);
    ZonedDateTime expiryDateTime = expanded.getExpiryDateTime();
    double expiry = volatilityProvider.relativeTime(expiryDateTime);
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    ArgChecker.isTrue(expiry >= 0d, "option should be before expiry to compute an implied volatility");
    double forward = swapPricer.parRate(underlying, ratesProvider);
    double pvbp = swapPricer.getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double tenor = volatilityProvider.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    return volatilityProvider.getVolatility(expiryDateTime, tenor, strike, forward);
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity of the swaption product.
   * <p>
   * The present value sensitivity of the product is the sensitivity of the present value to
   * the underlying curves.
   * 
   * @param swaption  the swaption product
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the Black volatility provider
   * @return the present value curve sensitivity of the swap product
   */
  public PointSensitivityBuilder presentValueSensitivity(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SABRVolatilitySwaptionProvider volatilityProvider) {

    ExpandedSwaption expanded = swaption.expand();
    validate(ratesProvider, expanded, volatilityProvider);
    ZonedDateTime expiryDateTime = expanded.getExpiryDateTime();
    double expiry = volatilityProvider.relativeTime(expiryDateTime);
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    if (expiry < 0d) { // Option has expired already
      return PointSensitivityBuilder.none();
    }
    double tenor = volatilityProvider.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double shift = volatilityProvider.getParameters().getShift(DoublesPair.of(expiry, tenor));
    double forward = swapPricer.parRate(underlying, ratesProvider);
    double pvbp = swapPricer.getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    double[] volatilityAdj = volatilityProvider.getParameters().getVolatilityAdjoint(expiry, tenor, strike, forward);
    boolean isCall = (fixedLeg.getPayReceive() == PayReceive.PAY);
    // Payer at strike is exercise when rate > strike, i.e. call on rate
    // Backward sweep
    PointSensitivityBuilder pvbpDr = swapPricer.getLegPricer().pvbpSensitivity(fixedLeg, ratesProvider);
    PointSensitivityBuilder forwardDr = swapPricer.parRateSensitivity(underlying, ratesProvider);
    double shiftedForward = forward + shift;
    double shiftedStrike = strike + shift;
    double price = BlackFormulaRepository.price(shiftedForward, shiftedStrike, expiry, volatilityAdj[0], isCall);
    double delta = BlackFormulaRepository.delta(shiftedForward, shiftedStrike, expiry, volatilityAdj[0], isCall);
    double vega = BlackFormulaRepository.vega(shiftedForward, shiftedStrike, expiry, volatilityAdj[0]);
    double sign = (expanded.getLongShort() == LongShort.LONG) ? 1d : -1d;
    return pvbpDr.multipliedBy(price * sign * Math.signum(pvbp))
        .combinedWith(forwardDr.multipliedBy((delta + vega * volatilityAdj[1]) * Math.abs(pvbp) * sign));
  }

  //-------------------------------------------------------------------------
  /**
   * Calculates the present value sensitivity to the SABR model parameters of the swaption product.
   * <p>
   * The sensitivity of the present value to the SABR model parameters, alpha, beta, rho and nu.
   * 
   * @param swaption  the swaption product
   * @param ratesProvider  the rates provider
   * @param volatilityProvider  the Black volatility provider
   * @return the point sensitivity to the Black volatility
   */
  public SwaptionSABRSensitivity presentValueSABRParameterSensitivity(
      SwaptionProduct swaption,
      RatesProvider ratesProvider,
      SABRVolatilitySwaptionProvider volatilityProvider) {

    ExpandedSwaption expanded = swaption.expand();
    validate(ratesProvider, expanded, volatilityProvider);
    ZonedDateTime expiryDateTime = expanded.getExpiryDateTime();
    double expiry = volatilityProvider.relativeTime(expiryDateTime);
    ExpandedSwap underlying = expanded.getUnderlying();
    ExpandedSwapLeg fixedLeg = fixedLeg(underlying);
    double tenor = volatilityProvider.tenor(fixedLeg.getStartDate(), fixedLeg.getEndDate());
    double shift = volatilityProvider.getParameters().getShift(DoublesPair.of(expiry, tenor));
    double pvbp = swapPricer.getLegPricer().pvbp(fixedLeg, ratesProvider);
    double strike = swapPricer.getLegPricer().couponEquivalent(fixedLeg, ratesProvider, pvbp);
    if (expiry < 0d) { // Option has expired already
      return SwaptionSABRSensitivity.of(volatilityProvider.getConvention(),
          expiryDateTime, tenor, strike, 0d, fixedLeg.getCurrency(), 0d, 0d, 0d, 0d);
    }
    double forward = swapPricer.parRate(underlying, ratesProvider);
    double volatility = volatilityProvider.getVolatility(expiryDateTime, tenor, strike, forward);
    double[] volatilityModelAdj =
        volatilityProvider.getParameters().getVolatilityModelAdjoint(expiry, tenor, strike, forward);
    // Backward sweep
    double vega = Math.abs(pvbp) * BlackFormulaRepository.vega(forward + shift, strike + shift, expiry, volatility)
        * ((expanded.getLongShort() == LongShort.LONG) ? 1d : -1d);
    return SwaptionSABRSensitivity.of(volatilityProvider.getConvention(), expiryDateTime, tenor, strike, forward,
        fixedLeg.getCurrency(), vega * volatilityModelAdj[0], vega * volatilityModelAdj[1],
        vega * volatilityModelAdj[2], vega * volatilityModelAdj[3]);
  }

  // check that one leg is fixed and return it
  private ExpandedSwapLeg fixedLeg(ExpandedSwap swap) {
    ArgChecker.isFalse(swap.isCrossCurrency(), "swap should be single currency");
    // find fixed leg
    List<ExpandedSwapLeg> fixedLegs = swap.getLegs(SwapLegType.FIXED);
    if (fixedLegs.isEmpty()) {
      throw new IllegalArgumentException("Swap must contain a fixed leg");
    }
    return fixedLegs.get(0);
  }

  // validate that the rates and volatilities providers are coherent
  private void validate(RatesProvider ratesProvider, ExpandedSwaption swaption,
      SABRVolatilitySwaptionProvider volatilityProvider) {
    ArgChecker.isTrue(volatilityProvider.getValuationDateTime().toLocalDate().equals(ratesProvider.getValuationDate()),
        "volatility and rate data should be for the same date");
    ArgChecker.isFalse(swaption.getUnderlying().isCrossCurrency(), "underlying swap should be single currency");
    ArgChecker.isTrue(swaption.getSwaptionSettlement().getSettlementType().equals(SettlementType.PHYSICAL),
        "swaption should be physical settlement");
  }

}
