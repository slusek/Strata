/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.fra;

import java.time.temporal.ChronoUnit;

import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.collect.ArgChecker;
import com.opengamma.platform.finance.fra.ExpandedFra;
import com.opengamma.platform.finance.fra.FraDiscountingMethod;
import com.opengamma.platform.finance.observation.RateObservation;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.fra.FraProductPricerFn;
import com.opengamma.platform.pricer.impl.observation.DispatchingRateObservationFn;
import com.opengamma.platform.pricer.observation.RateObservationFn;

/**
 * Pricer implementation for forward rate agreements.
 * <p>
 * The forward rate agreement is priced by examining the forward rate agreement legs.
 */
public class DiscountingExpandedFraPricerFn
    implements FraProductPricerFn<ExpandedFra> {

  /**
   * Default implementation.
   */
  public static final DiscountingExpandedFraPricerFn DEFAULT = new DiscountingExpandedFraPricerFn(
      DispatchingRateObservationFn.DEFAULT);

  /**
   * Rate observation.
   */
  private final RateObservationFn<RateObservation> rateObservationFn;

  /**
   * Creates an instance.
   * 
   * @param rateObservationFn  the rate observation function
   */
  public DiscountingExpandedFraPricerFn(
      RateObservationFn<RateObservation> rateObservationFn) {
    this.rateObservationFn = ArgChecker.notNull(rateObservationFn, "rateObservationFn");
  }

  //-------------------------------------------------------------------------
  @Override
  public MultiCurrencyAmount presentValue(PricingEnvironment env, ExpandedFra fra) {
    // TODO: this should be (futureValue() * discountFactor)
    double notional = fra.getNotional();
    Currency currency = fra.getCurrency();
    switch (fra.getDiscounting()) {
      case ISDA:
        return MultiCurrencyAmount.of(currency, notional * unitValueDiscounting(env, fra));
      case AFMA:
        return MultiCurrencyAmount.of(currency, notional * unitValueYieldDiscounting(env, fra));
      case NONE:
        return MultiCurrencyAmount.of(currency, notional * unitValueNoDiscounting(env, fra));
      default:
        throw new IllegalArgumentException("Unknown FraDiscounting value: " + fra.getDiscounting());
    }
  }

  @Override
  public MultiCurrencyAmount futureValue(PricingEnvironment env, ExpandedFra fra) {
    // TODO: this should handle all types
    if (fra.getDiscounting() == FraDiscountingMethod.ISDA) {
      return MultiCurrencyAmount.of(fra.getCurrency(), fra.getNotional() * unitValueNoDiscounting(env, fra));
    }
    throw new IllegalArgumentException("value: " + fra.getDiscounting() + "not supported");
  }

  //-------------------------------------------------------------------------
  // ISDA discounting method
  private double unitValueDiscounting(PricingEnvironment env, ExpandedFra fra) {
    double df = env.discountFactor(fra.getCurrency(), fra.getPaymentDate());
    return df * unitValueNoDiscounting(env, fra);
  }

  // NONE discounting method
  private double unitValueNoDiscounting(PricingEnvironment env, ExpandedFra fra) {
    double fixedRate = fra.getFixedRate();
    double forwardRate = forwardRate(env, fra);
    double yearFraction = fra.getYearFraction();
    return (forwardRate - fixedRate) * yearFraction;
  }

  // ACMA discounting method
  private double unitValueYieldDiscounting(PricingEnvironment env, ExpandedFra fra) {
    double fixedRate = fra.getFixedRate();
    double forwardRate = forwardRate(env, fra);
    double yearFraction = ChronoUnit.DAYS.between(fra.getStartDate(), fra.getEndDate()) / 365.0;
    return 1.0 / (1.0 + fixedRate * yearFraction) - 1.0 / (1.0 + forwardRate * yearFraction);
  }

  // query the forward rate
  private double forwardRate(PricingEnvironment env, ExpandedFra fra) {
    return rateObservationFn.rate(env, fra.getFloatingRate(), fra.getStartDate(), fra.getEndDate());
  }

}
