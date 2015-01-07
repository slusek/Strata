/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.platform.pricer.impl;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import org.joda.beans.Bean;
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

import com.google.common.collect.ImmutableMap;
import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.analytics.financial.instrument.index.IndexIborMaster;
import com.opengamma.analytics.financial.instrument.index.IndexON;
import com.opengamma.analytics.financial.instrument.index.IndexONMaster;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderInterface;
import com.opengamma.basics.currency.Currency;
import com.opengamma.basics.currency.CurrencyAmount;
import com.opengamma.basics.currency.CurrencyPair;
import com.opengamma.basics.currency.MultiCurrencyAmount;
import com.opengamma.basics.date.DayCount;
import com.opengamma.basics.date.Tenor;
import com.opengamma.basics.index.FxIndex;
import com.opengamma.basics.index.IborIndex;
import com.opengamma.basics.index.Index;
import com.opengamma.basics.index.OvernightIndex;
import com.opengamma.collect.ArgChecker;
import com.opengamma.collect.timeseries.LocalDateDoubleTimeSeries;
import com.opengamma.platform.pricer.PricingEnvironment;

/**
 * Immutable pricing environment.
 */
@BeanDefinition
public class ImmutablePricingEnvironmentDayCount
    implements PricingEnvironment, ImmutableBean, Serializable {

  /** Serialization version. */
  private static final long serialVersionUID = 1L;

  /**
   * The multi-curve bundle.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final MulticurveProviderInterface multicurve;
  /**
   * The time-series.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<Index, LocalDateDoubleTimeSeries> timeSeries;
  /**
   * The day counts applicable to the discounting.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<Currency, DayCount> dayCountsDiscounting;
  /**
   * The day counts applicable to the discounting.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<IborIndex, DayCount> dayCountsIbor;
  /**
   * The day counts applicable to the discounting.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<OvernightIndex, DayCount> dayCountsOvernight;

  //-------------------------------------------------------------------------
  @Override
  public LocalDateDoubleTimeSeries getTimeSeries(Index index) {
    ArgChecker.notNull(index, "index");
    return Optional.ofNullable(timeSeries.get(index))
        .orElseThrow(() -> new IllegalArgumentException("Unknown index: " + index.getName()));
  }

  @Override
  public double discountFactor(Currency currency, LocalDate valuationDate, LocalDate date) {
    ArgChecker.notNull(currency, "currency");
    ArgChecker.notNull(valuationDate, "valuationDate");
    ArgChecker.notNull(date, "date");
    return multicurve.getDiscountFactor(currency(currency), relativeTime(currency, valuationDate, date));
  }

  @Override
  public com.opengamma.util.money.Currency currency(Currency currency) {
    return com.opengamma.util.money.Currency.of(currency.getCode());
  }

  @Override
  public double indexRate(
      IborIndex index,
      LocalDate valuationDate,
      LocalDate fixingDate) {
    ArgChecker.notNull(index, "index");
    ArgChecker.notNull(valuationDate, "valuationDate");
    ArgChecker.notNull(fixingDate, "fixingDate");
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      OptionalDouble fixedRate = getTimeSeries(index).get(fixingDate);
      if (fixedRate.isPresent()) {
        return fixedRate.getAsDouble();
      } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
        throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
      }
    }
    // forward rate
    LocalDate fixingStartDate = index.calculateEffectiveFromFixing(fixingDate);
    LocalDate fixingEndDate = index.calculateMaturityFromEffective(fixingStartDate);
    double fixingYearFraction = index.getDayCount().yearFraction(fixingStartDate, fixingEndDate);
    return getMulticurve().getSimplyCompoundForwardRate(
        convert(index),
        relativeTime(valuationDate, fixingStartDate),
        relativeTime(valuationDate, fixingEndDate),
        fixingYearFraction);
  }

  @Override
  public double fxRate(CurrencyPair currencyPair) {
    ArgChecker.notNull(currencyPair, "currencyPair");
    return getMulticurve().getFxRate(currency(currencyPair.getBase()), currency(currencyPair.getCounter()));
  }

  @Override
  public double fxRate(FxIndex index, CurrencyPair currencyPair, LocalDate valuationDate, LocalDate fixingDate) {
    ArgChecker.notNull(index, "index");
    ArgChecker.notNull(currencyPair, "currencyPair");
    ArgChecker.notNull(valuationDate, "valuationDate");
    ArgChecker.notNull(fixingDate, "fixingDate");
    ArgChecker.isTrue(currencyPair.equals(index.getCurrencyPair()) || currencyPair.isInverse(index.getCurrencyPair()),
        "CurrencyPair must match FxIndex");
    // historic rate
    if (!fixingDate.isAfter(valuationDate)) {
      OptionalDouble fixedRate = getTimeSeries(index).get(fixingDate);
      if (fixedRate.isPresent()) {
        return fixFxRate(index, currencyPair, fixedRate.getAsDouble());
      } else if (fixingDate.isBefore(valuationDate)) { // the fixing is required
        throw new OpenGammaRuntimeException("Could not get fixing value for date " + fixingDate);
      }
    }
    // forward rate
    // derive from discount factors
    LocalDate maturityDate = index.calculateMaturityFromFixing(fixingDate);
    double maturityTime = relativeTime(valuationDate, maturityDate);
    double dfCcyReferenceAtDelivery = multicurve.getDiscountFactor(currency(currencyPair.getBase()), maturityTime);
    double dfCcyPaymentAtDelivery = multicurve.getDiscountFactor(currency(currencyPair.getCounter()), maturityTime);
    double fxRate = dfCcyReferenceAtDelivery / dfCcyPaymentAtDelivery;
    return fixFxRate(index, currencyPair, fxRate);
  }

  // if the index is the inverse of the desired pair, then invert it
  private double fixFxRate(FxIndex index, CurrencyPair desiredPair, double fxRate) {
    return (desiredPair.isInverse(index.getCurrencyPair()) ? 1d / fxRate : fxRate);
  }
  
  // convert a MultiCurrencyAmount with a conversion into a OG-Analytics MultipleCurrencyAmount
  @Override
  public CurrencyAmount convert(MultiCurrencyAmount mca, Currency ccy) {
    double amountCcy = mca.stream().
        mapToDouble(ca -> multicurve.getFxRate(com.opengamma.util.money.Currency.of(ca.getCurrency().toString()), 
            com.opengamma.util.money.Currency.of(ccy.toString())) * ca.getAmount()).sum();
    return CurrencyAmount.of(ccy, amountCcy);
  }
  
  private static final IndexON USDFEDFUND = IndexONMaster.getInstance().getIndex("FED FUND");
  @Override
  public IndexON convert(OvernightIndex index) {
    if(index.getCurrency().equals(Currency.USD)) {
      return USDFEDFUND;
    }
    throw new OpenGammaRuntimeException("Could not get an overnight index for currency " + index.getCurrency());
  }

  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USDLIBOR1M =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR1M);
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USDLIBOR3M =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR3M);
  private static final com.opengamma.analytics.financial.instrument.index.IborIndex USDLIBOR6M =
      IndexIborMaster.getInstance().getIndex(IndexIborMaster.USDLIBOR6M);
  @Override
  public com.opengamma.analytics.financial.instrument.index.IborIndex convert(IborIndex index) {
    com.opengamma.analytics.financial.instrument.index.IborIndex idx = USDLIBOR3M;
    if (index.getTenor().equals(Tenor.TENOR_6M)) {
      idx = USDLIBOR6M;
    } else if (index.getTenor().equals(Tenor.TENOR_1M)) {
      idx = USDLIBOR1M;
    }
    return idx;
  }

  @Override
  public double relativeTime(LocalDate baseDate, LocalDate date) {
    throw new OpenGammaRuntimeException("relative time without currency/index not relevant for this implementation");
  }

  @Override
  public double relativeTime(Currency currency, LocalDate baseDate, LocalDate date) {
    DayCount dayCount = dayCountsDiscounting.get(currency);
    if (date.isBefore(baseDate)) {
      return -dayCount.yearFraction(date, baseDate);
    }
    return dayCount.yearFraction(baseDate, date);
  }

  @Override
  public double relativeTime(IborIndex index, LocalDate baseDate, LocalDate date) {
    DayCount dayCount = dayCountsIbor.get(index);
    if (date.isBefore(baseDate)) {
      return -dayCount.yearFraction(date, baseDate);
    }
    return dayCount.yearFraction(baseDate, date);
  }

  @Override
  public double relativeTime(OvernightIndex index, LocalDate baseDate, LocalDate date) {
    DayCount dayCount = dayCountsOvernight.get(index);
    if (date.isBefore(baseDate)) {
      return -dayCount.yearFraction(date, baseDate);
    }
    return dayCount.yearFraction(baseDate, date);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ImmutablePricingEnvironmentDayCount}.
   * @return the meta-bean, not null
   */
  public static ImmutablePricingEnvironmentDayCount.Meta meta() {
    return ImmutablePricingEnvironmentDayCount.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ImmutablePricingEnvironmentDayCount.Meta.INSTANCE);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static ImmutablePricingEnvironmentDayCount.Builder builder() {
    return new ImmutablePricingEnvironmentDayCount.Builder();
  }

  /**
   * Restricted constructor.
   * @param builder  the builder to copy from, not null
   */
  protected ImmutablePricingEnvironmentDayCount(ImmutablePricingEnvironmentDayCount.Builder builder) {
    JodaBeanUtils.notNull(builder.multicurve, "multicurve");
    JodaBeanUtils.notNull(builder.timeSeries, "timeSeries");
    JodaBeanUtils.notNull(builder.dayCountsDiscounting, "dayCountsDiscounting");
    JodaBeanUtils.notNull(builder.dayCountsIbor, "dayCountsIbor");
    JodaBeanUtils.notNull(builder.dayCountsOvernight, "dayCountsOvernight");
    this.multicurve = builder.multicurve;
    this.timeSeries = ImmutableMap.copyOf(builder.timeSeries);
    this.dayCountsDiscounting = ImmutableMap.copyOf(builder.dayCountsDiscounting);
    this.dayCountsIbor = ImmutableMap.copyOf(builder.dayCountsIbor);
    this.dayCountsOvernight = ImmutableMap.copyOf(builder.dayCountsOvernight);
  }

  @Override
  public ImmutablePricingEnvironmentDayCount.Meta metaBean() {
    return ImmutablePricingEnvironmentDayCount.Meta.INSTANCE;
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
   * Gets the multi-curve bundle.
   * @return the value of the property, not null
   */
  @Override
  public MulticurveProviderInterface getMulticurve() {
    return multicurve;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-series.
   * @return the value of the property, not null
   */
  public ImmutableMap<Index, LocalDateDoubleTimeSeries> getTimeSeries() {
    return timeSeries;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day counts applicable to the discounting.
   * @return the value of the property, not null
   */
  public ImmutableMap<Currency, DayCount> getDayCountsDiscounting() {
    return dayCountsDiscounting;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day counts applicable to the discounting.
   * @return the value of the property, not null
   */
  public ImmutableMap<IborIndex, DayCount> getDayCountsIbor() {
    return dayCountsIbor;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the day counts applicable to the discounting.
   * @return the value of the property, not null
   */
  public ImmutableMap<OvernightIndex, DayCount> getDayCountsOvernight() {
    return dayCountsOvernight;
  }

  //-----------------------------------------------------------------------
  /**
   * Returns a builder that allows this bean to be mutated.
   * @return the mutable builder, not null
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      ImmutablePricingEnvironmentDayCount other = (ImmutablePricingEnvironmentDayCount) obj;
      return JodaBeanUtils.equal(getMulticurve(), other.getMulticurve()) &&
          JodaBeanUtils.equal(getTimeSeries(), other.getTimeSeries()) &&
          JodaBeanUtils.equal(getDayCountsDiscounting(), other.getDayCountsDiscounting()) &&
          JodaBeanUtils.equal(getDayCountsIbor(), other.getDayCountsIbor()) &&
          JodaBeanUtils.equal(getDayCountsOvernight(), other.getDayCountsOvernight());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash += hash * 31 + JodaBeanUtils.hashCode(getMulticurve());
    hash += hash * 31 + JodaBeanUtils.hashCode(getTimeSeries());
    hash += hash * 31 + JodaBeanUtils.hashCode(getDayCountsDiscounting());
    hash += hash * 31 + JodaBeanUtils.hashCode(getDayCountsIbor());
    hash += hash * 31 + JodaBeanUtils.hashCode(getDayCountsOvernight());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(192);
    buf.append("ImmutablePricingEnvironmentDayCount{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("multicurve").append('=').append(JodaBeanUtils.toString(getMulticurve())).append(',').append(' ');
    buf.append("timeSeries").append('=').append(JodaBeanUtils.toString(getTimeSeries())).append(',').append(' ');
    buf.append("dayCountsDiscounting").append('=').append(JodaBeanUtils.toString(getDayCountsDiscounting())).append(',').append(' ');
    buf.append("dayCountsIbor").append('=').append(JodaBeanUtils.toString(getDayCountsIbor())).append(',').append(' ');
    buf.append("dayCountsOvernight").append('=').append(JodaBeanUtils.toString(getDayCountsOvernight())).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutablePricingEnvironmentDayCount}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code multicurve} property.
     */
    private final MetaProperty<MulticurveProviderInterface> multicurve = DirectMetaProperty.ofImmutable(
        this, "multicurve", ImmutablePricingEnvironmentDayCount.class, MulticurveProviderInterface.class);
    /**
     * The meta-property for the {@code timeSeries} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<Index, LocalDateDoubleTimeSeries>> timeSeries = DirectMetaProperty.ofImmutable(
        this, "timeSeries", ImmutablePricingEnvironmentDayCount.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code dayCountsDiscounting} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<Currency, DayCount>> dayCountsDiscounting = DirectMetaProperty.ofImmutable(
        this, "dayCountsDiscounting", ImmutablePricingEnvironmentDayCount.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code dayCountsIbor} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<IborIndex, DayCount>> dayCountsIbor = DirectMetaProperty.ofImmutable(
        this, "dayCountsIbor", ImmutablePricingEnvironmentDayCount.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code dayCountsOvernight} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<OvernightIndex, DayCount>> dayCountsOvernight = DirectMetaProperty.ofImmutable(
        this, "dayCountsOvernight", ImmutablePricingEnvironmentDayCount.class, (Class) ImmutableMap.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "multicurve",
        "timeSeries",
        "dayCountsDiscounting",
        "dayCountsIbor",
        "dayCountsOvernight");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1253345110:  // multicurve
          return multicurve;
        case 779431844:  // timeSeries
          return timeSeries;
        case 647994081:  // dayCountsDiscounting
          return dayCountsDiscounting;
        case -1346647844:  // dayCountsIbor
          return dayCountsIbor;
        case 977178532:  // dayCountsOvernight
          return dayCountsOvernight;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public ImmutablePricingEnvironmentDayCount.Builder builder() {
      return new ImmutablePricingEnvironmentDayCount.Builder();
    }

    @Override
    public Class<? extends ImmutablePricingEnvironmentDayCount> beanType() {
      return ImmutablePricingEnvironmentDayCount.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code multicurve} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<MulticurveProviderInterface> multicurve() {
      return multicurve;
    }

    /**
     * The meta-property for the {@code timeSeries} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableMap<Index, LocalDateDoubleTimeSeries>> timeSeries() {
      return timeSeries;
    }

    /**
     * The meta-property for the {@code dayCountsDiscounting} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableMap<Currency, DayCount>> dayCountsDiscounting() {
      return dayCountsDiscounting;
    }

    /**
     * The meta-property for the {@code dayCountsIbor} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableMap<IborIndex, DayCount>> dayCountsIbor() {
      return dayCountsIbor;
    }

    /**
     * The meta-property for the {@code dayCountsOvernight} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<ImmutableMap<OvernightIndex, DayCount>> dayCountsOvernight() {
      return dayCountsOvernight;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1253345110:  // multicurve
          return ((ImmutablePricingEnvironmentDayCount) bean).getMulticurve();
        case 779431844:  // timeSeries
          return ((ImmutablePricingEnvironmentDayCount) bean).getTimeSeries();
        case 647994081:  // dayCountsDiscounting
          return ((ImmutablePricingEnvironmentDayCount) bean).getDayCountsDiscounting();
        case -1346647844:  // dayCountsIbor
          return ((ImmutablePricingEnvironmentDayCount) bean).getDayCountsIbor();
        case 977178532:  // dayCountsOvernight
          return ((ImmutablePricingEnvironmentDayCount) bean).getDayCountsOvernight();
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
   * The bean-builder for {@code ImmutablePricingEnvironmentDayCount}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<ImmutablePricingEnvironmentDayCount> {

    private MulticurveProviderInterface multicurve;
    private Map<Index, LocalDateDoubleTimeSeries> timeSeries = new HashMap<Index, LocalDateDoubleTimeSeries>();
    private Map<Currency, DayCount> dayCountsDiscounting = new HashMap<Currency, DayCount>();
    private Map<IborIndex, DayCount> dayCountsIbor = new HashMap<IborIndex, DayCount>();
    private Map<OvernightIndex, DayCount> dayCountsOvernight = new HashMap<OvernightIndex, DayCount>();

    /**
     * Restricted constructor.
     */
    protected Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(ImmutablePricingEnvironmentDayCount beanToCopy) {
      this.multicurve = beanToCopy.getMulticurve();
      this.timeSeries = new HashMap<Index, LocalDateDoubleTimeSeries>(beanToCopy.getTimeSeries());
      this.dayCountsDiscounting = new HashMap<Currency, DayCount>(beanToCopy.getDayCountsDiscounting());
      this.dayCountsIbor = new HashMap<IborIndex, DayCount>(beanToCopy.getDayCountsIbor());
      this.dayCountsOvernight = new HashMap<OvernightIndex, DayCount>(beanToCopy.getDayCountsOvernight());
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1253345110:  // multicurve
          return multicurve;
        case 779431844:  // timeSeries
          return timeSeries;
        case 647994081:  // dayCountsDiscounting
          return dayCountsDiscounting;
        case -1346647844:  // dayCountsIbor
          return dayCountsIbor;
        case 977178532:  // dayCountsOvernight
          return dayCountsOvernight;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 1253345110:  // multicurve
          this.multicurve = (MulticurveProviderInterface) newValue;
          break;
        case 779431844:  // timeSeries
          this.timeSeries = (Map<Index, LocalDateDoubleTimeSeries>) newValue;
          break;
        case 647994081:  // dayCountsDiscounting
          this.dayCountsDiscounting = (Map<Currency, DayCount>) newValue;
          break;
        case -1346647844:  // dayCountsIbor
          this.dayCountsIbor = (Map<IborIndex, DayCount>) newValue;
          break;
        case 977178532:  // dayCountsOvernight
          this.dayCountsOvernight = (Map<OvernightIndex, DayCount>) newValue;
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
    public ImmutablePricingEnvironmentDayCount build() {
      return new ImmutablePricingEnvironmentDayCount(this);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the {@code multicurve} property in the builder.
     * @param multicurve  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder multicurve(MulticurveProviderInterface multicurve) {
      JodaBeanUtils.notNull(multicurve, "multicurve");
      this.multicurve = multicurve;
      return this;
    }

    /**
     * Sets the {@code timeSeries} property in the builder.
     * @param timeSeries  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder timeSeries(Map<Index, LocalDateDoubleTimeSeries> timeSeries) {
      JodaBeanUtils.notNull(timeSeries, "timeSeries");
      this.timeSeries = timeSeries;
      return this;
    }

    /**
     * Sets the {@code dayCountsDiscounting} property in the builder.
     * @param dayCountsDiscounting  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCountsDiscounting(Map<Currency, DayCount> dayCountsDiscounting) {
      JodaBeanUtils.notNull(dayCountsDiscounting, "dayCountsDiscounting");
      this.dayCountsDiscounting = dayCountsDiscounting;
      return this;
    }

    /**
     * Sets the {@code dayCountsIbor} property in the builder.
     * @param dayCountsIbor  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCountsIbor(Map<IborIndex, DayCount> dayCountsIbor) {
      JodaBeanUtils.notNull(dayCountsIbor, "dayCountsIbor");
      this.dayCountsIbor = dayCountsIbor;
      return this;
    }

    /**
     * Sets the {@code dayCountsOvernight} property in the builder.
     * @param dayCountsOvernight  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCountsOvernight(Map<OvernightIndex, DayCount> dayCountsOvernight) {
      JodaBeanUtils.notNull(dayCountsOvernight, "dayCountsOvernight");
      this.dayCountsOvernight = dayCountsOvernight;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(192);
      buf.append("ImmutablePricingEnvironmentDayCount.Builder{");
      int len = buf.length();
      toString(buf);
      if (buf.length() > len) {
        buf.setLength(buf.length() - 2);
      }
      buf.append('}');
      return buf.toString();
    }

    protected void toString(StringBuilder buf) {
      buf.append("multicurve").append('=').append(JodaBeanUtils.toString(multicurve)).append(',').append(' ');
      buf.append("timeSeries").append('=').append(JodaBeanUtils.toString(timeSeries)).append(',').append(' ');
      buf.append("dayCountsDiscounting").append('=').append(JodaBeanUtils.toString(dayCountsDiscounting)).append(',').append(' ');
      buf.append("dayCountsIbor").append('=').append(JodaBeanUtils.toString(dayCountsIbor)).append(',').append(' ');
      buf.append("dayCountsOvernight").append('=').append(JodaBeanUtils.toString(dayCountsOvernight)).append(',').append(' ');
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
