/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import java.time.LocalDate;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.collect.ArgChecker;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.swap.NotionalExchange;
import com.opengamma.platform.finance.swap.PaymentEvent;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.swap.PaymentEventPricerFn;

/**
 * Multiple dispatch for {@code AccrualPeriodPricerFn}.
 * <p>
 * Dispatches the pricer request to the correct implementation.
 */
public class DefaultPaymentEventPricerFn
    implements PaymentEventPricerFn<PaymentEvent> {

  /**
   * Default implementation.
   */
  public static final DefaultPaymentEventPricerFn DEFAULT = new DefaultPaymentEventPricerFn(
      DefaultNotionalExchangePricerFn.DEFAULT);

  //-------------------------------------------------------------------------
  /**
   * Handle {@link NotionalExchange}.
   */
  private PaymentEventPricerFn<NotionalExchange> notionalExchangeFn;

  /**
   * Creates an instance.
   * 
   * @param notionalExchangeFn  the rate provider for {@link NotionalExchange}
   */
  public DefaultPaymentEventPricerFn(
      PaymentEventPricerFn<NotionalExchange> notionalExchangeFn) {
    super();
    this.notionalExchangeFn = ArgChecker.notNull(notionalExchangeFn, "notionalExchangeFn");
  }

  //-------------------------------------------------------------------------
  @Override
  public double presentValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      PaymentEvent event) {
    // dispatch by runtime type
    if (event instanceof NotionalExchange) {
      return notionalExchangeFn.presentValue(env, valuationDate, (NotionalExchange) event);
    } else {
      throw new IllegalArgumentException("Unknown PaymentEvent type: " + event.getClass().getSimpleName());
    }
  }

  @Override
  public double futureValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      PaymentEvent event) {
    // dispatch by runtime type
    if (event instanceof NotionalExchange) {
      return notionalExchangeFn.futureValue(env, valuationDate, (NotionalExchange) event);
    } else {
      throw new IllegalArgumentException("Unknown PaymentEvent type: " + event.getClass().getSimpleName());
    }
  }
  
  @Override
  public Pair<Double, MulticurveSensitivity> presentValueCurveSensitivity(
      PricingEnvironment env,
      LocalDate valuationDate,
      PaymentEvent event) {
    // dispatch by runtime type
    if (event instanceof NotionalExchange) {
      return notionalExchangeFn.presentValueCurveSensitivity(env, valuationDate, (NotionalExchange) event);
    } else {
      throw new IllegalArgumentException("Unknown PaymentEvent type: " + event.getClass().getSimpleName());
    }
  }

}
