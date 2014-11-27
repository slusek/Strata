/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl.swap;

import java.time.LocalDate;

import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.collect.ArgChecker;
import com.opengamma.platform.finance.rate.FixedRate;
import com.opengamma.platform.finance.swap.ExpandedSwapLeg;
import com.opengamma.platform.finance.swap.PaymentEvent;
import com.opengamma.platform.finance.swap.PaymentPeriod;
import com.opengamma.platform.finance.swap.RatePaymentPeriod;
import com.opengamma.platform.finance.swap.Swap;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.swap.PaymentEventPricerFn;
import com.opengamma.platform.pricer.swap.PaymentPeriodPricerFn;
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
  public DefaultSwapPricerFn(
      PaymentPeriodPricerFn<PaymentPeriod> paymentPeriodPricerFn,
      PaymentEventPricerFn<PaymentEvent> paymentEventPricerFn) {
    this.paymentPeriodPricerFn = ArgChecker.notNull(paymentPeriodPricerFn, "paymentPeriodPricerFn");
    this.paymentEventPricerFn = ArgChecker.notNull(paymentEventPricerFn, "paymentEventPricerFn");
  }

  //-------------------------------------------------------------------------
  @Override
  public MultiCurrencyAmount presentValue(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    int nbLeg = swap.getLegs().size();
    MultiCurrencyAmount pvSwap = MultiCurrencyAmount.of();
    for (int i = 0; i < nbLeg; i++) {
      ExpandedSwapLeg expandedLeg = swap.getLeg(i).toExpanded();
      double pvPeriods = expandedLeg.getPaymentPeriods().stream()
          .mapToDouble(p -> paymentPeriodPricerFn.presentValue(env, valuationDate, p))
          .sum();
      double pvEvent = expandedLeg.getPaymentEvents().stream()
          .mapToDouble(p -> paymentEventPricerFn.presentValue(env, valuationDate, p))
          .sum();
      pvSwap = pvSwap.plus(MultiCurrencyAmount.of(expandedLeg.getCurrency(), pvPeriods + pvEvent));
    }
    return pvSwap;
  }

  @Override
  public MultiCurrencyAmount futureValue(PricingEnvironment env, LocalDate valuationDate, Swap swap) {
    int nbLeg = swap.getLegs().size();
    MultiCurrencyAmount fvSwap = MultiCurrencyAmount.of();
    for (int i = 0; i < nbLeg; i++) {
      ExpandedSwapLeg expandedLeg = swap.getLeg(i).toExpanded();
      double fvPeriods = expandedLeg.getPaymentPeriods().stream()
          .mapToDouble(p -> paymentPeriodPricerFn.futureValue(env, valuationDate, p))
          .sum();
      double fvEvent = expandedLeg.getPaymentEvents().stream()
          .mapToDouble(p -> paymentEventPricerFn.futureValue(env, valuationDate, p))
          .sum();
      fvSwap = fvSwap.plus(MultiCurrencyAmount.of(expandedLeg.getCurrency(), fvPeriods + fvEvent));
    }
    return fvSwap;
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
      pvOtherLegs += swap.getLeg(i).toExpanded().getPaymentPeriods().stream()
          .mapToDouble(p -> paymentPeriodPricerFn.presentValue(env, valuationDate, p)).sum();
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