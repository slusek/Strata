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

import org.apache.commons.lang.NotImplementedException;

import com.opengamma.analytics.financial.provider.sensitivity.multicurve.MulticurveSensitivity;
import com.opengamma.basics.currency.CurrencyPair;
import com.opengamma.collect.ArgChecker;
import com.opengamma.collect.tuple.Pair;
import com.opengamma.platform.finance.rate.FixedRate;
import com.opengamma.platform.finance.rate.Rate;
import com.opengamma.platform.finance.swap.RateAccrualPeriod;
import com.opengamma.platform.finance.swap.RatePaymentPeriod;
import com.opengamma.platform.pricer.PricingEnvironment;
import com.opengamma.platform.pricer.impl.rate.DefaultRateProviderFn;
import com.opengamma.platform.pricer.rate.RateProviderFn;
import com.opengamma.platform.pricer.swap.PaymentPeriodPricerFn;
import com.opengamma.util.tuple.DoublesPair;

/**
 * Pricer implementation for swap payment periods based on a rate.
 * <p>
 * The value of a payment period is calculated by combining the value of each accrual period.
 * Where necessary, the accrual periods are compounded.
 */
public class DefaultRatePaymentPeriodPricerFn
    implements PaymentPeriodPricerFn<RatePaymentPeriod> {

  /**
   * Default implementation.
   */
  public static final DefaultRatePaymentPeriodPricerFn DEFAULT = new DefaultRatePaymentPeriodPricerFn(
      DefaultRateProviderFn.DEFAULT);

  /**
   * Rate provider.
   */
  private final RateProviderFn<Rate> rateProviderFn;

  /**
   * Creates an instance.
   * 
   * @param rateProviderFn  the rate provider
   */
  public DefaultRatePaymentPeriodPricerFn(
      RateProviderFn<Rate> rateProviderFn) {
    this.rateProviderFn = ArgChecker.notNull(rateProviderFn, "rateProviderFn");
  }

  //-------------------------------------------------------------------------
  @Override
  public double presentValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      RatePaymentPeriod period) {
    // futureValue * discountFactor
    double df = env.discountFactor(period.getCurrency(), valuationDate, period.getPaymentDate());
    return futureValue(env, valuationDate, period) * df;
  }

  //-------------------------------------------------------------------------
  @Override
  public double futureValue(
      PricingEnvironment env,
      LocalDate valuationDate,
      RatePaymentPeriod period) {
    // historic payments have zero pv
    if (period.getPaymentDate().isBefore(valuationDate)) {
      return 0;
    }
    // find FX rate, using 1 if no FX reset occurs
    double fxRate = 1d;
    if (period.getFxReset() != null) {
      CurrencyPair pair = CurrencyPair.of(period.getFxReset().getReferenceCurrency(), period.getCurrency());
      fxRate = env.fxRate(period.getFxReset().getIndex(), pair, valuationDate, period.getFxReset().getFixingDate());
    }
    double notional = period.getNotional() * fxRate;
    // handle compounding
    double unitAccrual; 
    if (period.isCompounding()) {
      unitAccrual = unitNotionalCompounded(env, valuationDate, period);
    } else {
      unitAccrual = unitNotionalNoCompounding(env, valuationDate, period);
    }
    return notional * unitAccrual;
  }

  //-------------------------------------------------------------------------
  public Pair<Double,MulticurveSensitivity> futureValueCurveSensitivity(
      PricingEnvironment env,
      LocalDate valuationDate,
      RatePaymentPeriod period) {
    // historic payments have zero sensi
    if (period.getPaymentDate().isBefore(valuationDate)) {
      return Pair.of(0.0D, new MulticurveSensitivity());
    }
    // find FX rate, using 1 if no FX reset occurs
    double fxRate = 1d;
    if (period.getFxReset() != null) { // FX Reset not yet implemented
      throw new NotImplementedException("FX Reset not yet implemented for futureValueCurveSensitivity");
    }
    double notional = period.getNotional() * fxRate;
    // handle compounding
    Pair<Double,MulticurveSensitivity> unitAccrual;
    if (period.isCompounding()) {
      throw new NotImplementedException("compounding not yet implemented for futureValueCurveSensitivity");
    } else {
      unitAccrual = unitNotionalSensiNoCompounding(env, valuationDate, period);
    }
    return Pair.of(notional * unitAccrual.getFirst(), unitAccrual.getSecond().multipliedBy(notional));
  }

  //-------------------------------------------------------------------------
  @Override
  public Pair<Double,MulticurveSensitivity> presentValueCurveSensitivity(
      PricingEnvironment env,
      LocalDate valuationDate,
      RatePaymentPeriod period) {
    // futureValue * discountFactor
    double paymentTime = env.relativeTime(valuationDate, period.getPaymentDate());
    double df = env.discountFactor(period.getCurrency(), valuationDate, period.getPaymentDate());
    Pair<Double,MulticurveSensitivity> fvSensitivity = futureValueCurveSensitivity(env, valuationDate, period);
    double pv = fvSensitivity.getFirst() * df;
    final Map<String, List<DoublesPair>> mapDsc = new HashMap<>();
    final List<DoublesPair> listDiscounting = new ArrayList<>();
    listDiscounting.add(DoublesPair.of(paymentTime, -paymentTime * df * fvSensitivity.getFirst()));
    mapDsc.put(env.getMulticurve().getName(env.currency(period.getCurrency())), listDiscounting);
    MulticurveSensitivity sensi = MulticurveSensitivity.ofYieldDiscounting(mapDsc);
    sensi = sensi.plus(fvSensitivity.getSecond().multipliedBy(df));
    return Pair.of(pv, sensi);
  }

  //-------------------------------------------------------------------------
  @Override
  public double pvbpQuote(
      PricingEnvironment env,
      LocalDate valuationDate,
      RatePaymentPeriod period) {
    // historic payments have zero pv
    if (period.getPaymentDate().isBefore(valuationDate)) {
      return 0;
    }
    // find FX rate, using 1 if no FX reset occurs
    double fxRate = 1d;
    if (period.getFxReset() != null) {
      CurrencyPair pair = CurrencyPair.of(period.getFxReset().getReferenceCurrency(), period.getCurrency());
      fxRate = env.fxRate(period.getFxReset().getIndex(), pair, valuationDate, period.getFxReset().getFixingDate());
    }
    double notional = period.getNotional() * fxRate;
    // handle compounding
    double unitAccrual; 
    if (period.isCompounding()) {
      throw new NotImplementedException("Compounding not handled yet for pvbp");
    } else {
      unitAccrual = period.getAccrualPeriods().stream()
          .mapToDouble(accrualPeriod -> (accrualPeriod.getRate() instanceof FixedRate) ?
              accrualPeriod.getGearing() * accrualPeriod.getYearFraction():
              accrualPeriod.getYearFraction()).sum();
    }
    double df = env.discountFactor(period.getCurrency(), valuationDate, period.getPaymentDate());
    return notional * unitAccrual * df;
  }

  //-------------------------------------------------------------------------
  // no compounding needed
  private double unitNotionalNoCompounding(PricingEnvironment env, LocalDate valuationDate, RatePaymentPeriod period) {
    return period.getAccrualPeriods().stream()
        .mapToDouble(accrualPeriod -> unitNotionalAccrual(env, valuationDate, accrualPeriod, accrualPeriod.getSpread()))
        .sum();
  }
  
  // no compounding needed
  private Pair<Double, MulticurveSensitivity> unitNotionalSensiNoCompounding(PricingEnvironment env, LocalDate valuationDate, RatePaymentPeriod period) {
    double un = 0.0d;
    MulticurveSensitivity sensi = new MulticurveSensitivity();
    for (RateAccrualPeriod accrualPeriod : period.getAccrualPeriods()) {
      Pair<Double, MulticurveSensitivity> pair = unitNotionalSensiAccrual(env, valuationDate, accrualPeriod, accrualPeriod.getSpread());
      un += pair.getFirst();
      sensi = sensi.plus(pair.getSecond());
    }
    return Pair.of(un, sensi);
  }

  // apply compounding
  private double unitNotionalCompounded(PricingEnvironment env, LocalDate valuationDate, RatePaymentPeriod period) {
    switch (period.getCompoundingMethod()) {
      case STRAIGHT:
        return compoundedStraight(env, valuationDate, period);
      case FLAT:
        return compoundedFlat(env, valuationDate, period);
      case SPREAD_EXCLUSIVE:
        return compoundedSpreadExclusive(env, valuationDate, period);
      case NONE:
      default:
        // NONE is handled in unitNotionalNoCompounding()
        throw new IllegalArgumentException("Unknown CompoundingMethod");
    }
  }

  // straight compounding
  private double compoundedStraight(PricingEnvironment env, LocalDate valuationDate, RatePaymentPeriod period) {
    double notional = 1d;
    double notionalAccrued = notional;
    for (RateAccrualPeriod accrualPeriod : period.getAccrualPeriods()) {
      double unitAccrual = unitNotionalAccrual(env, valuationDate, accrualPeriod, accrualPeriod.getSpread());
      double investFactor = 1 + unitAccrual;
      notionalAccrued *= investFactor;
    }
    return (notionalAccrued - notional);
  }

  // flat compounding
  private double compoundedFlat(PricingEnvironment env, LocalDate valuationDate, RatePaymentPeriod period) {
    // FIXME: deal with gearing.
    double cpaAccumulated = 0d;
    for (RateAccrualPeriod accrualPeriod : period.getAccrualPeriods()) {
      double rate = rateProviderFn.rate(env, valuationDate, accrualPeriod.getRate(), accrualPeriod.getStartDate(), accrualPeriod.getEndDate());
      cpaAccumulated += cpaAccumulated * rate * accrualPeriod.getYearFraction() 
          + (rate + accrualPeriod.getSpread()) * accrualPeriod.getYearFraction();
    }
    return cpaAccumulated;
  }

  // spread exclusive compounding
  private double compoundedSpreadExclusive(PricingEnvironment env, LocalDate valuationDate, RatePaymentPeriod period) {
    double notional = 1d;
    double notionalAccrued = notional;
    double spreadAccrued = 0;
    for (RateAccrualPeriod accrualPeriod : period.getAccrualPeriods()) {
      double unitAccrual = unitNotionalAccrual(env, valuationDate, accrualPeriod, 0);
      double investFactor = 1 + unitAccrual;
      notionalAccrued *= investFactor;
      spreadAccrued += accrualPeriod.getSpread() * accrualPeriod.getYearFraction();
    }
    return (notionalAccrued - notional) + spreadAccrued;
  }

  // calculate the accrual for a unit notional
  private double unitNotionalAccrual(
      PricingEnvironment env,
      LocalDate valuationDate,
      RateAccrualPeriod period,
      double spread) {
    double rate = rateProviderFn.rate(env, valuationDate, period.getRate(), period.getStartDate(), period.getEndDate());
    double treatedRate = rate * period.getGearing() + spread;
    return period.getNegativeRateMethod().adjust(treatedRate * period.getYearFraction());
  }

  // calculate the accrual for a unit notional
  private Pair<Double, MulticurveSensitivity> unitNotionalSensiAccrual(
      PricingEnvironment env,
      LocalDate valuationDate,
      RateAccrualPeriod period,
      double spread) {
    Pair<Double, MulticurveSensitivity> pair = rateProviderFn.rateMulticurveSensitivity(env, valuationDate, period.getRate(), period.getStartDate(), period.getEndDate());
    double treatedRate = pair.getFirst() * period.getGearing() + spread;
    double unitNotionalAccrual = period.getNegativeRateMethod().adjust(treatedRate * period.getYearFraction());
    // Backward sweep.
    // FIXME: need to adjust also the sensitivity
    return Pair.of(unitNotionalAccrual, pair.getSecond().multipliedBy(period.getGearing()));
  }

}
