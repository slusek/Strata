/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.swap.NotionalExchange;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.swap.PaymentEventPricerFn;
import com.opengamma.util.tuple.DoublesPair;

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

  @Override
  public Pair<Double, MulticurveSensitivity> presentValueCurveSensitivity(
      PricingEnvironment env, 
      LocalDate valuationDate, 
      NotionalExchange event) {
    double paymentTime = env.relativeTime(valuationDate, event.getPaymentDate());
    double df = env.discountFactor(event.getPaymentAmount().getCurrency(), valuationDate, event.getPaymentDate());
    final Map<String, List<DoublesPair>> mapDsc = new HashMap<>();
    final List<DoublesPair> listDiscounting = new ArrayList<>();
    listDiscounting.add(DoublesPair.of(paymentTime, -paymentTime * df * event.getPaymentAmount().getAmount()));
    mapDsc.put(env.getMulticurve().getName(env.currency(event.getCurrency())), listDiscounting);
    MulticurveSensitivity sensi = MulticurveSensitivity.ofYieldDiscounting(mapDsc);
    return Pair.of(event.getPaymentAmount().getAmount() * df, sensi);
  }

}
