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
import com.opengamma.platform.finance.swap.ExpandedSwapLeg;
import com.opengamma.platform.finance.swap.PaymentEvent;
import com.opengamma.platform.finance.swap.PaymentPeriod;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.swap.PaymentEventPricerFn;
import com.opengamma.platform.pricer.swap.PaymentPeriodPricerFn;
import com.opengamma.platform.pricer.swap.SwapLegPricerFn;

/**
 * Pricer for expanded swap legs.
 */
public class DefaultExpandedSwapLegPricerFn implements SwapLegPricerFn<ExpandedSwapLeg> {

  /**
   * Default implementation.
   */
  public static final DefaultExpandedSwapLegPricerFn DEFAULT = new DefaultExpandedSwapLegPricerFn(
      DefaultPaymentPeriodPricerFn.DEFAULT,
      DefaultPaymentEventPricerFn.DEFAULT);

  /**
   * Payment period pricer.
   */
  private final PaymentPeriodPricerFn<PaymentPeriod> paymentPeriodPricerFn;
  /**
   * Payment event pricer.
   */
  private final PaymentEventPricerFn<PaymentEvent> paymentEventPricerFn;

  /**
   * Creates an instance.
   * 
   * @param paymentPeriodPricerFn  the pricer for {@link PaymentPeriod}
   */
  public DefaultExpandedSwapLegPricerFn(
      PaymentPeriodPricerFn<PaymentPeriod> paymentPeriodPricerFn,
      PaymentEventPricerFn<PaymentEvent> paymentEventPricerFn) {
    this.paymentPeriodPricerFn = ArgChecker.notNull(paymentPeriodPricerFn, "paymentPeriodPricerFn");
    this.paymentEventPricerFn = ArgChecker.notNull(paymentEventPricerFn, "paymentEventPricerFn");
  }

  //-------------------------------------------------------------------------
  @Override
  public double presentValue(PricingEnvironment env, LocalDate valuationDate, ExpandedSwapLeg swapLeg) {
    double pvPayments = swapLeg.getPaymentPeriods().stream()
        .mapToDouble(p -> paymentPeriodPricerFn.presentValue(env, valuationDate, p))
        .sum();
    double pvEvents = swapLeg.getPaymentEvents().stream()
      .mapToDouble(p -> paymentEventPricerFn.presentValue(env, valuationDate, p))
      .sum();
     return pvPayments + pvEvents;
  }

  @Override
  public double futureValue(PricingEnvironment env, LocalDate valuationDate, ExpandedSwapLeg swapLeg) {
    double fvPayments = swapLeg.getPaymentPeriods().stream()
        .mapToDouble(p -> paymentPeriodPricerFn.futureValue(env, valuationDate, p))
        .sum();
    double fvEvents = swapLeg.getPaymentEvents().stream()
      .mapToDouble(p -> paymentEventPricerFn.futureValue(env, valuationDate, p))
      .sum();
     return fvPayments + fvEvents;
  }

  @Override
  public Pair<Double, MulticurveSensitivity> presentValueCurveSensitivity(
      PricingEnvironment env, LocalDate valuationDate, ExpandedSwapLeg swapLeg) {
    MulticurveSensitivity sensi = new MulticurveSensitivity();
    double pv = 0.0;
    for(PaymentPeriod payment: swapLeg.getPaymentPeriods()) {
      Pair<Double, MulticurveSensitivity> pair = 
          paymentPeriodPricerFn.presentValueCurveSensitivity(env, valuationDate, payment);
      pv += pair.getFirst();
      sensi = sensi.plus(pair.getSecond());      
    }
    return Pair.of(pv, sensi);
  }

}
