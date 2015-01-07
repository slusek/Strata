/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import java.time.LocalDate;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MultipleCurrencyMulticurveSensitivity;
import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.currency.CurrencyAmount;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.collect.ArgChecker;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.rate.FixedRate;
import com.opengamma.platform.finance.swap.ExpandedSwapLeg;
import com.opengamma.platform.finance.swap.PaymentEvent;
import com.opengamma.platform.finance.swap.PaymentPeriod;
import com.opengamma.platform.finance.swap.RatePaymentPeriod;
import com.opengamma.platform.finance.swap.Swap;
import com.opengamma.platform.finance.swap.SwapLeg;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3;
import com.opengamma.platform.pricer.results.MulticurveSensitivity3LD;
import com.opengamma.platform.pricer.swap.PaymentEventPricerFn;
import com.opengamma.platform.pricer.swap.PaymentPeriodPricerFn;
import com.opengamma.platform.pricer.swap.SwapLegPricerFn;
import com.opengamma.platform.pricer.swap.SwapPricerFn;
import com.opengamma.util.ArgumentChecker;

/**
 * Pricer for swaps.
 */
public class DefaultSwapPricerFn implements SwapPricerFn {

  /**
   * Default implementation.
   */
  public static final DefaultSwapPricerFn DEFAULT = new DefaultSwapPricerFn(
      DefaultPaymentPeriodPricerFn.DEFAULT,
      DefaultPaymentEventPricerFn.DEFAULT,
      DefaultSwapLegPricerFn.DEFAULT);

  /**
   * Payment period pricer.
   */
  private final PaymentPeriodPricerFn<PaymentPeriod> paymentPeriodPricerFn;
  /**
   * Payment event pricer.
   */
  private final PaymentEventPricerFn<PaymentEvent> paymentEventPricerFn;
  /**
  * Payment period pricer.
  */
  private final SwapLegPricerFn<SwapLeg> swapLegPricerFn;

  /**
   * Creates an instance.
   * 
   * @param paymentPeriodPricerFn  the pricer for {@link PaymentPeriod}
   */
  public DefaultSwapPricerFn(
      PaymentPeriodPricerFn<PaymentPeriod> paymentPeriodPricerFn,
      PaymentEventPricerFn<PaymentEvent> paymentEventPricerFn,
      SwapLegPricerFn<SwapLeg> swapLegPricerFn) {
    this.paymentPeriodPricerFn = ArgChecker.notNull(paymentPeriodPricerFn, "paymentPeriodPricerFn");
    this.paymentEventPricerFn = ArgChecker.notNull(paymentEventPricerFn, "paymentEventPricerFn");
    this.swapLegPricerFn = ArgChecker.notNull(swapLegPricerFn, "swapLegPricerFn");
  }

  //-------------------------------------------------------------------------
  @Override
  public MultiCurrencyAmount presentValue(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    return swap.getLegs().stream()
        .map(leg -> CurrencyAmount.of(leg.getCurrency(), swapLegPricerFn.presentValue(env, valuationDate, leg)))
        .reduce(MultiCurrencyAmount.of(), MultiCurrencyAmount::plus, MultiCurrencyAmount::plus);
  }
  
  @Override
  public MultiCurrencyAmount[] presentValue(PricingEnvironment[] env, LocalDate valuationDate, Swap swap) {
    int nbEnv = env.length;
    MultiCurrencyAmount[] result = new MultiCurrencyAmount[nbEnv];
    for(int i = 0; i< nbEnv; i++) {
      result[i] = MultiCurrencyAmount.of();
    }
    for(SwapLeg leg : swap.getLegs()) {
      double[] legPv = swapLegPricerFn.presentValue(env, valuationDate, leg);
      for(int i = 0; i< nbEnv; i++) {
        result[i] = result[i].plus(leg.getCurrency(), legPv[i]);
      }
    }
    return result;
  }

  @Override
  public MultiCurrencyAmount futureValue(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    return swap.getLegs().stream()
        .map(leg -> CurrencyAmount.of(leg.getCurrency(), swapLegPricerFn.futureValue(env, valuationDate, leg)))
        .reduce(MultiCurrencyAmount.of(), MultiCurrencyAmount::plus, MultiCurrencyAmount::plus);
  }

  @Override
  public Pair<MultiCurrencyAmount, MultipleCurrencyMulticurveSensitivity> presentValueCurveSensitivity(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    MultipleCurrencyMulticurveSensitivity sensi = new MultipleCurrencyMulticurveSensitivity();
    MultiCurrencyAmount pv = MultiCurrencyAmount.of();
    for(SwapLeg leg: swap.getLegs()) {
      Pair<Double, MulticurveSensitivity> pair = 
          swapLegPricerFn.presentValueCurveSensitivity(env, valuationDate, leg);
      pv = pv.plus(leg.getCurrency(), pair.getFirst());
      sensi = sensi.plus(env.currency(leg.getCurrency()), pair.getSecond());      
    }
    return Pair.of(pv, sensi);
  }

  @Override
  public Pair<MultiCurrencyAmount, MulticurveSensitivity3> presentValueCurveSensitivity3(
      PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    MulticurveSensitivity3 sensi = new MulticurveSensitivity3();
    MultiCurrencyAmount pv = MultiCurrencyAmount.of();
    for(SwapLeg leg: swap.getLegs()) {
      Pair<Double, MulticurveSensitivity3> pair = 
          swapLegPricerFn.presentValueCurveSensitivity3(env, valuationDate, leg);
      pv = pv.plus(leg.getCurrency(), pair.getFirst());
      sensi.add(pair.getSecond());      
    }
    return Pair.of(pv, sensi);
  }

  @Override
  public Pair<MultiCurrencyAmount, MulticurveSensitivity3LD> presentValueCurveSensitivity3LD(
      PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    MulticurveSensitivity3LD sensi = new MulticurveSensitivity3LD();
    MultiCurrencyAmount pv = MultiCurrencyAmount.of();
    for(SwapLeg leg: swap.getLegs()) {
      Pair<Double, MulticurveSensitivity3LD> pair = 
          swapLegPricerFn.presentValueCurveSensitivity3LD(env, valuationDate, leg);
      pv = pv.plus(leg.getCurrency(), pair.getFirst());
      sensi.add(pair.getSecond());      
    }
    return Pair.of(pv, sensi);
  }

  @Override
  public double parRate(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    ExpandedSwapLeg leg0 = swap.getLeg(0).toExpanded();
    Currency ccy = leg0.getCurrency();
    // Check it is a fixed leg
    leg0.getPaymentPeriods().stream().map(p -> fixedLegCheck(env, valuationDate, p));
    double pvbp = leg0.getPaymentPeriods().stream()
        .mapToDouble(p -> paymentPeriodPricerFn.pvbpQuote(env, valuationDate, p)).sum();
    double pvPayment0 = leg0.getPaymentEvents().stream()
        .mapToDouble(p -> paymentEventPricerFn.presentValue(env, valuationDate, p)).sum();
    double pvOtherLegs = 0.0;
    for (int i = 1; i < swap.getLegs().size(); i++) {
      ArgumentChecker.isTrue(swap.getLeg(i).getCurrency().equals(ccy), "all legs should be in the same currency");
      pvOtherLegs += swapLegPricerFn.presentValue(env, valuationDate, swap.getLeg(i));
    }
    return -(pvPayment0 + pvOtherLegs) / pvbp;
  }

  @Override
  public double parSpread(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    // PV of the swap converted in leg 0 currency
    Currency ccy = swap.getLeg(0).getCurrency();
    MultiCurrencyAmount pv = presentValue(env, valuationDate, swap);
    double pvConverted = env.convert(pv, ccy).getAmount();
    // PVBP
    double pvbpLeg0 = swap.getLeg(0).toExpanded().getPaymentPeriods().stream()
        .mapToDouble(p -> paymentPeriodPricerFn.pvbpQuote(env, valuationDate, p)).sum(); //TODO: leg0 expanded twice
    // par spread
    return -pvConverted / pvbpLeg0;
  }

  private boolean fixedLegCheck(PricingEnvironment env, LocalDate valuationDate, PaymentPeriod p) {
    ArgumentChecker.isTrue(p instanceof RatePaymentPeriod, "rate payment period");
    RatePaymentPeriod r = (RatePaymentPeriod) p;
    ArgumentChecker.isTrue(r.getAccrualPeriods().size() == 1, "should be one accrual period");
    ArgumentChecker.isTrue(r.getAccrualPeriod(0).getRate() instanceof FixedRate, "should be fixed rate");
    ArgumentChecker.isTrue(r.getAccrualPeriod(0).getGearing() == 1.0, "should be gearing 1");
    return true;
  }

}
