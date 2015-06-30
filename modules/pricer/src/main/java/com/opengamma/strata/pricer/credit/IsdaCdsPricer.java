/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.credit;

import com.google.common.collect.Lists;
import com.opengamma.analytics.financial.credit.isdastandardmodel.ISDACompliantCreditCurve;
import com.opengamma.analytics.financial.credit.isdastandardmodel.ISDACompliantYieldCurve;
import com.opengamma.strata.basics.currency.CurrencyAmount;
import com.opengamma.strata.basics.date.Tenor;
import com.opengamma.strata.finance.credit.ExpandedCds;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.IsdaCreditCurveParRates;
import com.opengamma.strata.market.curve.IsdaYieldCurveParRates;
import com.opengamma.strata.market.curve.NodalCurve;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.CurveCurrencyParameterSensitivity;
import org.joda.beans.MetaBean;
import org.joda.beans.Property;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Set;

/**
 * Pricer for for CDS products using the ISDA methodology.
 * <p>
 * This function provides the ability to price a {@link ExpandedCds}.
 * Both single name and index swaps can be priced.
 */
public class IsdaCdsPricer {

  /**
   * Default implementation
   */
  public static final IsdaCdsPricer DEFAULT = new IsdaCdsPricer();

  /**
   * Standard one basis point for applying shifts
   */
  private static final double ONE_BPS = 0.0001d;

  //-------------------------------------------------------------------------

  /**
   * Calculates the present value of the expanded CDS product.
   * <p>
   * The present value of the CDS is the present value of all cashflows as of the valuation date.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @param scalingFactor       linear scaling factor associated with underlying index, or 1 in case of CDS
   * @return present value of fee leg and any up front fee
   */
  public CurrencyAmount presentValue(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    ISDACompliantYieldCurve yieldCurve = IsdaCdsHelper.createIsdaDiscountCurve(valuationDate, yieldCurveParRates);
    ISDACompliantCreditCurve creditCurve = IsdaCdsHelper.createIsdaCreditCurve(valuationDate, creditCurveParRates, yieldCurve, recoveryRate);

    return IsdaCdsHelper.price(valuationDate, product, convert(yieldCurve), convert(creditCurve), recoveryRate, scalingFactor);
  }

  /**
   * Calculates the par rate of the expanded CDS product.
   * <p>
   * The par rate of the CDS is the coupon rate that will make present value of all cashflows
   * equal zero as of the valuation date.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @return par rate for the credit default swap
   */
  public double parRate(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate) {

    ISDACompliantYieldCurve yieldCurve = IsdaCdsHelper.createIsdaDiscountCurve(valuationDate, yieldCurveParRates);
    ISDACompliantCreditCurve creditCurve = IsdaCdsHelper.createIsdaCreditCurve(valuationDate, creditCurveParRates, yieldCurve, recoveryRate);

    return IsdaCdsHelper.parSpread(valuationDate, product, convert(yieldCurve), convert(creditCurve), recoveryRate);
  }

  private NodalCurve convert(ISDACompliantYieldCurve yieldCurve) {
    return new NodalCurve() {
      @Override
      public double[] getXValues() {
        return yieldCurve.getT();
      }

      @Override
      public double[] getYValues() {
        return yieldCurve.getRt();
      }

      @Override
      public NodalCurve withYValues(double[] values) {
        return null;
      }

      @Override
      public CurveMetadata getMetadata() {
        return null;
      }

      @Override
      public int getParameterCount() {
        return 0;
      }

      @Override
      public double yValue(double x) {
        return 0;
      }

      @Override
      public double[] yValueParameterSensitivity(double x) {
        return new double[0];
      }

      @Override
      public double firstDerivative(double x) {
        return 0;
      }

      @Override
      public MetaBean metaBean() {
        return null;
      }

      @Override
      public <R> Property<R> property(String s) {
        return null;
      }

      @Override
      public Set<String> propertyNames() {
        return null;
      }
    };
  }

  private NodalCurve convert(ISDACompliantCreditCurve creditCurve) {
    return new NodalCurve() {
      @Override
      public double[] getXValues() {
        return creditCurve.getT();
      }

      @Override
      public double[] getYValues() {
        return creditCurve.getRt();
      }

      @Override
      public NodalCurve withYValues(double[] values) {
        return null;
      }

      @Override
      public CurveMetadata getMetadata() {
        return null;
      }

      @Override
      public int getParameterCount() {
        return 0;
      }

      @Override
      public double yValue(double x) {
        return 0;
      }

      @Override
      public double[] yValueParameterSensitivity(double x) {
        return new double[0];
      }

      @Override
      public double firstDerivative(double x) {
        return 0;
      }

      @Override
      public MetaBean metaBean() {
        return null;
      }

      @Override
      public <R> Property<R> property(String s) {
        return null;
      }

      @Override
      public Set<String> propertyNames() {
        return null;
      }
    };
  }

  //-------------------------------------------------------------------------

  /**
   * Calculates the scalar PV change to a 1 basis point shift in par interest rates.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @param scalingFactor       linear scaling factor associated with underlying index, or 1 in case of CDS
   * @return present value of fee leg and any up front fee
   */
  public CurrencyAmount ir01ParallelPar(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    CurrencyAmount basePrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    IsdaYieldCurveParRates bumpedCurve = yieldCurveParRates.parallelShiftParRatesinBps(ONE_BPS);
    CurrencyAmount bumpedPrice = presentValue(product, bumpedCurve, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    return bumpedPrice.minus(basePrice);
  }

  /**
   * Calculates the vector PV change to a series of 1 basis point shifts in par interest rates at each curve node.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @return present value of fee leg and any up front fee
   */
  public CurveCurrencyParameterSensitivities ir01BucketedPar(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    CurrencyAmount basePrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    int points = yieldCurveParRates.getNumberOfPoints();
    double[] paramSensitivities = new double[points];
    List<TenorCurveNodeMetadata> metaData = Lists.newArrayList();
    for (int i = 0; i < points; i++) {
      IsdaYieldCurveParRates bumpedCurve = yieldCurveParRates.bucketedShiftParRatesinBps(i, ONE_BPS);
      CurrencyAmount bumpedPrice = presentValue(product, bumpedCurve, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
      CurrencyAmount sensitivity = bumpedPrice.minus(basePrice);
      paramSensitivities[i] = sensitivity.getAmount();
      Period period = yieldCurveParRates.getYieldCurvePoints()[i];
      LocalDate pointDate = valuationDate.plus(period);
      metaData.add(TenorCurveNodeMetadata.of(pointDate, Tenor.of(period)));
    }
    CurveMetadata curveMetadata = CurveMetadata.of(yieldCurveParRates.getName(), metaData);
    return CurveCurrencyParameterSensitivities.of(
        CurveCurrencyParameterSensitivity.of(curveMetadata, product.getCurrency(), paramSensitivities));
  }

  //-------------------------------------------------------------------------

  /**
   * Calculates the scalar PV change to a 1 basis point shift in par credit spread rates.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @param scalingFactor       linear scaling factor associated with underlying index, or 1 in case of CDS
   * @return present value of fee leg and any up front fee
   */
  public CurrencyAmount cs01ParallelPar(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    CurrencyAmount basePrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    IsdaCreditCurveParRates bumpedCurve = creditCurveParRates.parallelShiftParRatesinBps(ONE_BPS);
    CurrencyAmount bumpedPrice = presentValue(product, yieldCurveParRates, bumpedCurve, valuationDate, recoveryRate, scalingFactor);
    return bumpedPrice.minus(basePrice);
  }

  /**
   * Calculates the vector PV change to a series of 1 basis point shifts in par credit spread rates at each curve node.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @param scalingFactor       linear scaling factor associated with underlying index, or 1 in case of CDS
   * @return present value of fee leg and any up front fee
   */
  public CurveCurrencyParameterSensitivities cs01BucketedPar(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    CurrencyAmount basePrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    int points = creditCurveParRates.getNumberOfPoints();
    double[] paramSensitivities = new double[points];
    List<TenorCurveNodeMetadata> metaData = Lists.newArrayList();
    for (int i = 0; i < points; i++) {
      IsdaCreditCurveParRates bumpedCurve = creditCurveParRates.bucketedShiftParRatesinBps(i, ONE_BPS);
      CurrencyAmount bumpedPrice = presentValue(product, yieldCurveParRates, bumpedCurve, valuationDate, recoveryRate, scalingFactor);
      CurrencyAmount sensitivity = bumpedPrice.minus(basePrice);
      paramSensitivities[i] = sensitivity.getAmount();
      Period period = creditCurveParRates.getCreditCurvePoints()[i];
      LocalDate pointDate = valuationDate.plus(period);
      metaData.add(TenorCurveNodeMetadata.of(pointDate, Tenor.of(period)));
    }
    CurveMetadata curveMetadata = CurveMetadata.of(creditCurveParRates.getName(), metaData);
    return CurveCurrencyParameterSensitivities.of(
        CurveCurrencyParameterSensitivity.of(curveMetadata, product.getCurrency(), paramSensitivities));
  }

  //-------------------------------------------------------------------------

  /**
   * Calculates the scalar PV change to a 1 basis point shift in recovery rate.
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @param scalingFactor       linear scaling factor associated with underlying index, or 1 in case of CDS
   * @return present value of fee leg and any up front fee
   */
  public CurrencyAmount recovery01(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    CurrencyAmount basePrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    CurrencyAmount bumpedPrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate + ONE_BPS, scalingFactor);
    return bumpedPrice.minus(basePrice);
  }

  //-------------------------------------------------------------------------

  /**
   * Calculates the risk of default by subtracting from current MTM the Notional amount times Recovery Rate - 1
   *
   * @param product             expanded CDS product
   * @param yieldCurveParRates  par rate curve points of the ISDA discount curve to use
   * @param creditCurveParRates par spread rate curve points of the ISDA spread curve to use
   * @param valuationDate       date to use when calibrating curves and calculating the result
   * @param recoveryRate        recovery rate associate with underlying issue or index
   * @param scalingFactor       linear scaling factor associated with underlying index, or 1 in case of CDS
   * @return present value of fee leg and any up front fee
   */
  public CurrencyAmount jumpToDefault(
      ExpandedCds product,
      IsdaYieldCurveParRates yieldCurveParRates,
      IsdaCreditCurveParRates creditCurveParRates,
      LocalDate valuationDate,
      double recoveryRate,
      double scalingFactor) {

    CurrencyAmount basePrice = presentValue(product, yieldCurveParRates, creditCurveParRates, valuationDate, recoveryRate, scalingFactor);
    CurrencyAmount expectedLoss = CurrencyAmount.of(product.getCurrency(), product.getNotional() * (recoveryRate - 1));
    return expectedLoss.minus(basePrice);
  }

}
