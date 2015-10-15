package com.opengamma.strata.pricer.rate.swaption;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.primitives.Doubles;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.date.DayCount;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.tuple.DoublesPair;
import com.opengamma.strata.finance.rate.swap.type.FixedIborSwapConvention;
import com.opengamma.strata.market.sensitivity.SurfaceCurrencyParameterSensitivities;
import com.opengamma.strata.market.sensitivity.SurfaceCurrencyParameterSensitivity;
import com.opengamma.strata.market.sensitivity.SwaptionSABRSensitivity;
import com.opengamma.strata.market.surface.NodalSurface;
import com.opengamma.strata.market.surface.SurfaceMetadata;
import com.opengamma.strata.market.surface.SurfaceParameterMetadata;
import com.opengamma.strata.market.surface.SwaptionVolatilitySurfaceExpiryTenorNodeMetadata;
import com.opengamma.strata.pricer.impl.option.SABRInterestRateParameters;

@BeanDefinition(builderScope = "private")
public final class SABRVolatilitySwaptionProvider
    implements  ImmutableBean, Serializable {

  /** 
   * The SABR model parameters. 
   * <p>
   * Each model parameter of SABR model is a surface in the expiry/swap tenor dimensions. 
   */
  @PropertyDefinition(validate = "notNull")
  private final SABRInterestRateParameters parameters;
  /** 
   * The swap convention. 
   * <p>
   * The data must valid in terms of this swap convention. 
   */
  @PropertyDefinition(validate = "notNull")
  private final FixedIborSwapConvention convention;
  /** 
   * The day count applicable to the model. 
   */
  @PropertyDefinition(validate = "notNull")
  private final DayCount dayCount;
  /** 
   * The valuation date-time. 
   * <p>
   * All data items in this environment are calibrated for this date-time. 
   */
  @PropertyDefinition(validate = "notNull")
  private final ZonedDateTime valuationDateTime;

  //-------------------------------------------------------------------------
  /**
   * Creates a provider from the SABR model parameters and the date-time for which it is valid.
   * 
   * @param parameters  the SABR model parameters
   * @param convention  the swap convention for which the data is valid
   * @param dayCount  the day count applicable to the model
   * @param valuationDateTime  the valuation date-time
   * @return the provider
   */
  public static SABRVolatilitySwaptionProvider of(
      SABRInterestRateParameters parameters,
      FixedIborSwapConvention convention,
      DayCount dayCount,
      ZonedDateTime valuationDateTime) {

    return new SABRVolatilitySwaptionProvider(parameters, convention, dayCount, valuationDateTime);
  }

  /**
   * Creates a provider from the SABR model parameters and the date, time and zone for which it is valid.
   * 
   * @param parameters  the SABR model parameters
   * @param convention  the swap convention for which the data is valid
   * @param dayCount  the day count applicable to the model
   * @param valuationDate  the valuation date
   * @param valuationTime  the valuation time
   * @param valuationZone  the valuation time zone
   * @return the provider
   */
  public static SABRVolatilitySwaptionProvider of(
      SABRInterestRateParameters parameters,
      FixedIborSwapConvention convention,
      DayCount dayCount,
      LocalDate valuationDate,
      LocalTime valuationTime,
      ZoneId valuationZone) {

    return of(parameters, convention, dayCount, valuationDate.atTime(valuationTime).atZone(valuationZone));
  }

  /**
   * Creates a provider from the SABR model parameters and the date. 
   * <p>
   * The valuation time and zone are defaulted to noon UTC.
   * 
   * @param parameters  the SABR model parameters
   * @param convention  the swap convention for which the data is valid
   * @param dayCount  the day count applicable to the model
   * @param valuationDate  the valuation date
   * @return the provider
   */
  public static SABRVolatilitySwaptionProvider of(
      SABRInterestRateParameters parameters,
      FixedIborSwapConvention convention,
      DayCount dayCount,
      LocalDate valuationDate) {

    return of(parameters, convention, dayCount, valuationDate.atTime(LocalTime.NOON).atZone(ZoneOffset.UTC));
  }

  //-------------------------------------------------------------------------
  public double getVolatility(ZonedDateTime expiryDate, double tenor, double strike, double forwardRate) {
    double expiryTime = relativeTime(expiryDate);
    return parameters.getVolatility(expiryTime, tenor, strike, forwardRate);
  }

  public double[] getVolatilityAdjoint(ZonedDateTime expiryDate, double tenor, double strike, double forwardRate) {
    double expiryTime = relativeTime(expiryDate);
    return parameters.getVolatilityAdjoint(expiryTime, tenor, strike, forwardRate);
  }

  public double relativeTime(ZonedDateTime dateTime) {
    ArgChecker.notNull(dateTime, "dateTime");
    LocalDate valuationDate = valuationDateTime.toLocalDate();
    LocalDate date = dateTime.toLocalDate();
    boolean timeIsNegative = valuationDate.isAfter(date);
    if (timeIsNegative) {
      return -dayCount.yearFraction(date, valuationDate);
    }
    return dayCount.yearFraction(valuationDate, date);
  }

  public double tenor(LocalDate startDate, LocalDate endDate) {
    // rounded number of months. the rounding is to ensure that an integer number of year even with holidays/leap year
    return Math.round((endDate.toEpochDay() - startDate.toEpochDay()) / 365.25 * 12) / 12;
  }

  public SurfaceCurrencyParameterSensitivities surfaceCurrencyParameterSensitivity(SwaptionSABRSensitivity point) {
    ArgChecker.isTrue(point.getConvention().equals(convention),
        "Swap convention of provider should be the same as swap convention of swaption sensitivity");
    double expiry = relativeTime(point.getExpiry());
    double tenor = point.getTenor();
    DoublesPair expiryTenor = DoublesPair.of(expiry, tenor);
    SurfaceCurrencyParameterSensitivity alphaSensi = surfaceCurrencyParameterSensitivity(
        parameters.getAlphaSurface(), point.getCurrency(), point.getAlphaSensitivity(), expiryTenor);
    SurfaceCurrencyParameterSensitivity betaSensi = surfaceCurrencyParameterSensitivity(
        parameters.getBetaSurface(), point.getCurrency(), point.getBetaSensitivity(), expiryTenor);
    SurfaceCurrencyParameterSensitivity rhoSensi = surfaceCurrencyParameterSensitivity(
        parameters.getRhoSurface(), point.getCurrency(), point.getRhoSensitivity(), expiryTenor);
    SurfaceCurrencyParameterSensitivity nuSensi = surfaceCurrencyParameterSensitivity(
        parameters.getNuSurface(), point.getCurrency(), point.getNuSensitivity(), expiryTenor);
    return SurfaceCurrencyParameterSensitivities.of(alphaSensi, betaSensi, rhoSensi, nuSensi);
  }
  
  private SurfaceCurrencyParameterSensitivity surfaceCurrencyParameterSensitivity(
      NodalSurface surface, Currency currency, double factor, DoublesPair expiryTenor) {
    Map<DoublesPair, Double> sensiMap = surface.zValueParameterSensitivity(expiryTenor);
    return SurfaceCurrencyParameterSensitivity.of(
        updateSurfaceMetadata(surface.getMetadata(), sensiMap.keySet()), currency,
        Doubles.toArray(sensiMap.values().parallelStream().map((p) -> (p) * factor).collect(Collectors.toList())));
  }

  private SurfaceMetadata updateSurfaceMetadata(SurfaceMetadata surfaceMetadata, Set<DoublesPair> pairs) {
    List<SurfaceParameterMetadata> sortedMetaList = new ArrayList<SurfaceParameterMetadata>();
    if (surfaceMetadata.getParameterMetadata().isPresent()) {
      List<SurfaceParameterMetadata> metaList =
          new ArrayList<SurfaceParameterMetadata>(surfaceMetadata.getParameterMetadata().get());
      for (DoublesPair pair : pairs) {
        metadataLoop:
        for (SurfaceParameterMetadata parameterMetadata : metaList) {
          ArgChecker.isTrue(parameterMetadata instanceof SwaptionVolatilitySurfaceExpiryTenorNodeMetadata,
              "surface parameter metadata must be instance of SwaptionVolatilitySurfaceExpiryTenorNodeMetadata");
          SwaptionVolatilitySurfaceExpiryTenorNodeMetadata casted =
              (SwaptionVolatilitySurfaceExpiryTenorNodeMetadata) parameterMetadata;
          if (pair.getFirst() == casted.getYearFraction() && pair.getSecond() == casted.getTenor()) {
            sortedMetaList.add(casted);
            metaList.remove(parameterMetadata);
            break metadataLoop;
          }
        }
      }
      ArgChecker.isTrue(metaList.size() == 0, "mismatch between surface parameter metadata list and doubles pair list");
    } else {
      for (DoublesPair pair : pairs) {
        SwaptionVolatilitySurfaceExpiryTenorNodeMetadata parameterMetadata =
            SwaptionVolatilitySurfaceExpiryTenorNodeMetadata.of(pair.getFirst(), pair.getSecond());
        sortedMetaList.add(parameterMetadata);
      }
    }
    return surfaceMetadata.withParameterMetadata(sortedMetaList);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code SABRVolatilitySwaptionProvider}.
   * @return the meta-bean, not null
   */
  public static SABRVolatilitySwaptionProvider.Meta meta() {
    return SABRVolatilitySwaptionProvider.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(SABRVolatilitySwaptionProvider.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  private SABRVolatilitySwaptionProvider(
      SABRInterestRateParameters parameters,
      FixedIborSwapConvention convention,
      DayCount dayCount,
      ZonedDateTime valuationDateTime) {
    JodaBeanUtils.notNull(parameters, "parameters");
    JodaBeanUtils.notNull(convention, "convention");
    JodaBeanUtils.notNull(dayCount, "dayCount");
    JodaBeanUtils.notNull(valuationDateTime, "valuationDateTime");
    this.parameters = parameters;
    this.convention = convention;
    this.dayCount = dayCount;
    this.valuationDateTime = valuationDateTime;
  }

  @Override
  public SABRVolatilitySwaptionProvider.Meta metaBean() {
    return SABRVolatilitySwaptionProvider.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the SABR model parameters.
   * <p>
   * Each model parameter of SABR model is a surface in the expiry/swap tenor dimensions.
   * @return the value of the property, not null
   */
  public SABRInterestRateParameters getParameters() {
    return parameters;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the swap convention.
   * <p>
   * The data must valid in terms of this swap convention.
   * @return the value of the property, not null
   */
  public FixedIborSwapConvention getConvention() {
    return convention;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day count applicable to the model.
   * @return the value of the property, not null
   */
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the valuation date-time.
   * <p>
   * All data items in this environment are calibrated for this date-time.
   * @return the value of the property, not null
   */
  public ZonedDateTime getValuationDateTime() {
    return valuationDateTime;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      SABRVolatilitySwaptionProvider other = (SABRVolatilitySwaptionProvider) obj;
      return JodaBeanUtils.equal(getParameters(), other.getParameters()) &&
          JodaBeanUtils.equal(getConvention(), other.getConvention()) &&
          JodaBeanUtils.equal(getDayCount(), other.getDayCount()) &&
          JodaBeanUtils.equal(getValuationDateTime(), other.getValuationDateTime());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getParameters());
    hash = hash * 31 + JodaBeanUtils.hashCode(getConvention());
    hash = hash * 31 + JodaBeanUtils.hashCode(getDayCount());
    hash = hash * 31 + JodaBeanUtils.hashCode(getValuationDateTime());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("SABRVolatilitySwaptionProvider{");
    buf.append("parameters").append('=').append(getParameters()).append(',').append(' ');
    buf.append("convention").append('=').append(getConvention()).append(',').append(' ');
    buf.append("dayCount").append('=').append(getDayCount()).append(',').append(' ');
    buf.append("valuationDateTime").append('=').append(JodaBeanUtils.toString(getValuationDateTime()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code SABRVolatilitySwaptionProvider}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code parameters} property.
     */
    private final MetaProperty<SABRInterestRateParameters> parameters = DirectMetaProperty.ofImmutable(
        this, "parameters", SABRVolatilitySwaptionProvider.class, SABRInterestRateParameters.class);
    /**
     * The meta-property for the {@code convention} property.
     */
    private final MetaProperty<FixedIborSwapConvention> convention = DirectMetaProperty.ofImmutable(
        this, "convention", SABRVolatilitySwaptionProvider.class, FixedIborSwapConvention.class);
    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", SABRVolatilitySwaptionProvider.class, DayCount.class);
    /**
     * The meta-property for the {@code valuationDateTime} property.
     */
    private final MetaProperty<ZonedDateTime> valuationDateTime = DirectMetaProperty.ofImmutable(
        this, "valuationDateTime", SABRVolatilitySwaptionProvider.class, ZonedDateTime.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "parameters",
        "convention",
        "dayCount",
        "valuationDateTime");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return parameters;
        case 2039569265:  // convention
          return convention;
        case 1905311443:  // dayCount
          return dayCount;
        case -949589828:  // valuationDateTime
          return valuationDateTime;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends SABRVolatilitySwaptionProvider> builder() {
      return new SABRVolatilitySwaptionProvider.Builder();
    }

    @Override
    public Class<? extends SABRVolatilitySwaptionProvider> beanType() {
      return SABRVolatilitySwaptionProvider.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code parameters} property.
     * @return the meta-property, not null
     */
    public MetaProperty<SABRInterestRateParameters> parameters() {
      return parameters;
    }

    /**
     * The meta-property for the {@code convention} property.
     * @return the meta-property, not null
     */
    public MetaProperty<FixedIborSwapConvention> convention() {
      return convention;
    }

    /**
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    /**
     * The meta-property for the {@code valuationDateTime} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZonedDateTime> valuationDateTime() {
      return valuationDateTime;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return ((SABRVolatilitySwaptionProvider) bean).getParameters();
        case 2039569265:  // convention
          return ((SABRVolatilitySwaptionProvider) bean).getConvention();
        case 1905311443:  // dayCount
          return ((SABRVolatilitySwaptionProvider) bean).getDayCount();
        case -949589828:  // valuationDateTime
          return ((SABRVolatilitySwaptionProvider) bean).getValuationDateTime();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      metaProperty(propertyName);
      if (quiet) {
        return;
      }
      throw new UnsupportedOperationException("Property cannot be written: " + propertyName);
    }

  }

  //-----------------------------------------------------------------------
  /**
   * The bean-builder for {@code SABRVolatilitySwaptionProvider}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<SABRVolatilitySwaptionProvider> {

    private SABRInterestRateParameters parameters;
    private FixedIborSwapConvention convention;
    private DayCount dayCount;
    private ZonedDateTime valuationDateTime;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          return parameters;
        case 2039569265:  // convention
          return convention;
        case 1905311443:  // dayCount
          return dayCount;
        case -949589828:  // valuationDateTime
          return valuationDateTime;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 458736106:  // parameters
          this.parameters = (SABRInterestRateParameters) newValue;
          break;
        case 2039569265:  // convention
          this.convention = (FixedIborSwapConvention) newValue;
          break;
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        case -949589828:  // valuationDateTime
          this.valuationDateTime = (ZonedDateTime) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public SABRVolatilitySwaptionProvider build() {
      return new SABRVolatilitySwaptionProvider(
          parameters,
          convention,
          dayCount,
          valuationDateTime);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("SABRVolatilitySwaptionProvider.Builder{");
      buf.append("parameters").append('=').append(JodaBeanUtils.toString(parameters)).append(',').append(' ');
      buf.append("convention").append('=').append(JodaBeanUtils.toString(convention)).append(',').append(' ');
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("valuationDateTime").append('=').append(JodaBeanUtils.toString(valuationDateTime));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
