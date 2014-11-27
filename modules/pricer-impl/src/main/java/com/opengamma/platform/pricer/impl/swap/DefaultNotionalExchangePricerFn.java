/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import java.time.LocalDate;

import com.opengamma.platform.finance.swap.NotionalExchange;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.swap.PaymentEventPricerFn;

/**
 * Pricer implementation for swap payment event based on a notional exchange.
 * <p>
 * The value of a payment event is calculated from the amounts.
 */
public class DefaultNotionalExchangePricerFn
    implements PaymentEventPricerFn<NotionalExchange> {

  /**
   * Default implementation.
   */
  public static final DefaultNotionalExchangePricerFn DEFAULT = new DefaultNotionalExchangePricerFn();
  
  @Override
  public double presentValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      NotionalExchange event) {
    double df = env.discountFactor(event.getPaymentAmount().getCurrency(), valuationDate, event.getPaymentDate());
    return event.getPaymentAmount().getAmount() * df;
  }
  
  @Override
  public double futureValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      NotionalExchange event) {
    return event.getPaymentAmount().getAmount();
  }

}
