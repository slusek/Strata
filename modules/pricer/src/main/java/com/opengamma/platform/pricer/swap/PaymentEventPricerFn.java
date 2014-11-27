/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.swap;

import java.time.LocalDate;

import com.opengamma.platform.finance.swap.PaymentEvent;
import com.opengamma.platform.pricer.PricingEnvironment;

/**
 * Pricer for a single rate payment event.
 * <p>
 * Defines the values that can be calculated on a swap leg payment event.
 * <p>
 * Implementations must be immutable and thread-safe functions.
 * 
 * @param <T>  the type of event
 */
public interface PaymentEventPricerFn<T extends PaymentEvent> {

  /**
   * Calculates the present value of a single payment event. The amount is in the currency of the payment.
   * <p>
   * This returns the value of the payment with discounting.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param event  the event to price
   * @return the present value of the event
   */
  public abstract double presentValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      T event);

  /**
   * Calculates the future value of a single payment event. The amount is in the currency of the payment.
   * <p>
   * This returns the value of the event without discounting.
   * 
   * @param env  the pricing environment
   * @param valuationDate  the valuation date
   * @param event  the event to price
   * @return the present value of the event
   */
  public abstract double futureValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      T event);

}
