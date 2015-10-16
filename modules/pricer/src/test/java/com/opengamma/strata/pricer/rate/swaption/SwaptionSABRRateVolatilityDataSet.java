/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.pricer.rate.swaption;

import static com.opengamma.strata.basics.currency.Currency.USD;
import static com.opengamma.strata.basics.date.DayCounts.ACT_ACT_ISDA;
import static com.opengamma.strata.basics.index.IborIndices.USD_LIBOR_3M;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import com.opengamma.strata.basics.currency.FxMatrix;
import com.opengamma.strata.basics.interpolator.CurveInterpolator;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConventions;
import com.opengamma.strata.market.curve.CurveMetadata;
import com.opengamma.strata.market.curve.CurveName;
import com.opengamma.strata.market.curve.Curves;
import com.opengamma.strata.market.curve.InterpolatedNodalCurve;
import com.opengamma.strata.market.surface.ConstantNodalSurface;
import com.opengamma.strata.market.surface.DefaultSurfaceMetadata;
import com.opengamma.strata.market.surface.InterpolatedNodalSurface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceName;
import com.opengamma.strata.market.surface.SurfaceParameterMetadata;
import com.opengamma.strata.market.surface.SwaptionSurfaceExpiryTenorNodeMetadata;
import com.opengamma.strata.market.value.ValueType;
import com.opengamma.strata.math.impl.interpolation.CombinedInterpolatorExtrapolatorFactory;
import com.opengamma.strata.math.impl.interpolation.GridInterpolator2D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1D;
import com.opengamma.strata.math.impl.interpolation.Interpolator1DFactory;
import com.opengamma.strata.pricer.impl.option.SABRInterestRateParameters;
import com.opengamma.strata.pricer.impl.volatility.smile.function.SABRHaganVolatilityFunctionProvider;
import com.opengamma.strata.pricer.rate.ImmutableRatesProvider;

/**
 * Data sets for testing SABR model for swaptions.
 */
public class SwaptionSABRRateVolatilityDataSet {

  /*
   * Data set used to test the pricers for physical delivery swaption. 
   */
  static final FixedIborSwapConvention SWAP_CONVENTION = FixedIborSwapConventions.USD_FIXED_6M_LIBOR_3M;
  private static final CurveInterpolator INTERPOLATOR = Interpolator1DFactory.LINEAR_INSTANCE;
  private static final double[] TIME_DSC = new double[] {0.0027397260273972603, 0.005479452054794521,
    0.0958904109589041, 0.1726027397260274, 0.26301369863013696, 0.5123287671232877, 0.7643835616438356,
    1.0164383561643835, 2.0135040047907777, 3.010958904109589, 4.010958904109589, 5.016438356164383, 6.016236245227937,
    7.013698630136986, 8.01095890410959, 9.01095890410959, 10.010771764353619 };
  private static final double[] RATE_DSC = new double[] {0.0017743012430444162, 0.0016475657039787027,
    8.00944979276571E-4, 7.991342366517293E-4, 7.769429292812209E-4, 8.011052753850106E-4, 8.544769819435054E-4,
    0.0010101196182894087, 0.0025295133435066005, 0.005928027386129847, 0.009984669002766438, 0.013910233828705014,
    0.017362472692574276, 0.02026566836808523, 0.02272069332675379, 0.024782351990410997, 0.026505391310201288 };
  private static final CurveName NAME_DSC = CurveName.of("USD-DSCON");
  static final CurveMetadata META_DSC = Curves.zeroRates(NAME_DSC, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve CURVE_DSC =
      InterpolatedNodalCurve.of(META_DSC, TIME_DSC, RATE_DSC, INTERPOLATOR);
  private static final double[] TIME_FWD = new double[] {0.25205479452054796, 0.5013698630136987, 0.7534246575342466,
    1.010958904109589, 2.0107717643536196, 3.0054794520547947, 4.005479452054795, 5.005479452054795, 7.010958904109589,
    10.005307283479302, 12.01095890410959, 15.005479452054795, 20.005479452054793, 25.008219178082193, 30.01077176435362 };
  private static final double[] RATE_FWD = new double[] {0.002377379439054076, 0.002418692953929592,
    0.002500627386941208, 0.002647539893522339, 0.0044829589913700256, 0.008123927669512542, 0.012380488135102518,
    0.01644838699856555, 0.023026212753825423, 0.02933978147314773, 0.03208786808445587, 0.03475307015968317,
    0.03689179443401795, 0.03776622232525561, 0.03810645431268746 };
  private static final CurveName NAME_FWD = CurveName.of("USD-LIBOR3M");
  static final CurveMetadata META_FWD = Curves.zeroRates(NAME_FWD, ACT_ACT_ISDA);
  private static final InterpolatedNodalCurve CURVE_FWD =
      InterpolatedNodalCurve.of(META_FWD, TIME_FWD, RATE_FWD, INTERPOLATOR);

  private static final Interpolator1D LINEAR_FLAT = CombinedInterpolatorExtrapolatorFactory.getInterpolator(
      Interpolator1DFactory.LINEAR,
      Interpolator1DFactory.FLAT_EXTRAPOLATOR,
      Interpolator1DFactory.FLAT_EXTRAPOLATOR);
  private static final GridInterpolator2D INTERPOLATOR_2D = new GridInterpolator2D(LINEAR_FLAT, LINEAR_FLAT);
  private static final double[] EXPIRY_NODE = new double[]
  {0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10, 0.0, 0.5, 1, 2, 5, 10 };
  private static final double[] TENOR_NODE = new double[]
  {1, 1, 1, 1, 1, 1, 5, 5, 5, 5, 5, 5, 10, 10, 10, 10, 10, 10 };
  private static final double[] ALPHA_NODE = new double[] {
    0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.05, 0.06, 0.06, 0.06, 0.06, 0.06, 0.06 };
  private static final double[] BETA_NODE = new double[] {
    0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5 };
  private static final double[] RHO_NODE = new double[] {
    -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, -0.25, 0.0, 0.0, 0.0, 0.0 };
  private static final double[] NU_NODE = new double[] {
    0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.3, 0.3, 0.3, 0.3 };
  static final SurfaceMetadata META_ALPHA = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("ALPHA"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_ALPHA =
      InterpolatedNodalSurface.of(META_ALPHA, EXPIRY_NODE, TENOR_NODE, ALPHA_NODE, INTERPOLATOR_2D);
  private static final List<SurfaceParameterMetadata> PARAMETER_META_LIST;
  static {
    int n = EXPIRY_NODE.length;
    PARAMETER_META_LIST = new ArrayList<SurfaceParameterMetadata>(n);
    for (int i = 0; i < n; ++i) {
      PARAMETER_META_LIST.add(SwaptionSurfaceExpiryTenorNodeMetadata.of(EXPIRY_NODE[i], TENOR_NODE[i]));
    }
  }
  static final SurfaceMetadata META_BETA = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("BETA"))
      .parameterMetadata(PARAMETER_META_LIST)
      .build();
  private static final InterpolatedNodalSurface SURFACE_BETA =
      InterpolatedNodalSurface.of(META_BETA, EXPIRY_NODE, TENOR_NODE, BETA_NODE, INTERPOLATOR_2D);
  static final SurfaceMetadata META_RHO = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("RHO"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_RHO =
      InterpolatedNodalSurface.of(META_RHO, EXPIRY_NODE, TENOR_NODE, RHO_NODE, INTERPOLATOR_2D);
  static final SurfaceMetadata META_NU = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("SABRParameter"))
      .surfaceName(SurfaceName.of("NU"))
      .build();
  private static final InterpolatedNodalSurface SURFACE_NU =
      InterpolatedNodalSurface.of(META_NU, EXPIRY_NODE, TENOR_NODE, NU_NODE, INTERPOLATOR_2D);
  static final SABRInterestRateParameters SABR_PARAM = SABRInterestRateParameters.of(
      SURFACE_ALPHA, SURFACE_BETA, SURFACE_RHO, SURFACE_NU, SABRHaganVolatilityFunctionProvider.DEFAULT);

  static final double SHIFT = 0.025;
  private static final SurfaceMetadata META_SHIFT = DefaultSurfaceMetadata.builder()
      .xValueType(ValueType.YEAR_FRACTION)
      .yValueType(ValueType.of("Tenor"))
      .zValueType(ValueType.of("Shift"))
      .surfaceName(SurfaceName.of("SHIFT"))
      .build();
  private static final ConstantNodalSurface SURFACE_SHIFT = ConstantNodalSurface.of(META_SHIFT, SHIFT);
  static final SABRInterestRateParameters SABR_PARAM_SHIFT = SABRInterestRateParameters.of(
      SURFACE_ALPHA, SURFACE_BETA, SURFACE_RHO, SURFACE_NU, SABRHaganVolatilityFunctionProvider.DEFAULT, SURFACE_SHIFT);

  /**
   * Obtains {@code ImmutableRatesProvider} for specified valuation date. 
   * 
   * @param valuationDate  the valuation date
   * @return the rates provider
   */
  public static ImmutableRatesProvider getRatesProvider(LocalDate valuationDate) {
    return ImmutableRatesProvider.builder()
        .discountCurves(ImmutableMap.of(USD, CURVE_DSC))
        .indexCurves(ImmutableMap.of(USD_LIBOR_3M, CURVE_FWD))
        .fxMatrix(FxMatrix.empty())
        .valuationDate(valuationDate)
        .build();
  }

  /**
   * Obtains {@code SABRVolatilitySwaptionProvider} for specified valuation date. 
   * 
   * @param valuationDate  the valuation date
   * @param shift  nonzero shift if true, zero shift otherwise
   * @return the volatility provider
   */
  public static SABRVolatilitySwaptionProvider getVolatilityProvider(LocalDate valuationDate, boolean shift) {
    return shift ?
        SABRVolatilitySwaptionProvider.of(SABR_PARAM_SHIFT, SWAP_CONVENTION, ACT_ACT_ISDA, valuationDate) :
        SABRVolatilitySwaptionProvider.of(SABR_PARAM, SWAP_CONVENTION, ACT_ACT_ISDA, valuationDate);
  }
}
