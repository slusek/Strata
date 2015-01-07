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
 * Immutable pricing environment. Fixing for Ibor curves are stored.
 */
@BeanDefinition
public class ImmutableStoredPricingEnvironment
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
   * The day count applicable to the models.
   */
  @PropertyDefinition(validate = "notNull")
  private final DayCount dayCount;
  /**
   * The full map of data.
   */
  @PropertyDefinition(validate = "notNull")
  private final Map<IborIndex, Map<LocalDate, Double>> iborRate;

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
    return multicurve.getDiscountFactor(currency(currency), relativeTime(valuationDate, date));
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
    return iborRate.get(index).get(fixingDate);
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
    if (date.isBefore(baseDate)) {
      return -dayCount.yearFraction(date, baseDate);
    }
    return dayCount.yearFraction(baseDate, date);
  }

  @Override
  public double relativeTime(Currency currency, LocalDate baseDate, LocalDate date) {
    return relativeTime(baseDate, date);
  }

  @Override
  public double relativeTime(IborIndex index, LocalDate baseDate, LocalDate date) {
    return relativeTime(baseDate, date);
  }

  @Override
  public double relativeTime(OvernightIndex index, LocalDate baseDate, LocalDate date) {
    return relativeTime(baseDate, date);
  }
  
  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ImmutableStoredPricingEnvironment}.
   * @return the meta-bean, not null
   */
  public static ImmutableStoredPricingEnvironment.Meta meta() {
    return ImmutableStoredPricingEnvironment.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ImmutableStoredPricingEnvironment.Meta.INSTANCE);
  }

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static ImmutableStoredPricingEnvironment.Builder builder() {
    return new ImmutableStoredPricingEnvironment.Builder();
  }

  /**
   * Restricted constructor.
   * @param builder  the builder to copy from, not null
   */
  protected ImmutableStoredPricingEnvironment(ImmutableStoredPricingEnvironment.Builder builder) {
    JodaBeanUtils.notNull(builder.multicurve, "multicurve");
    JodaBeanUtils.notNull(builder.timeSeries, "timeSeries");
    JodaBeanUtils.notNull(builder.dayCount, "dayCount");
    JodaBeanUtils.notNull(builder.iborRate, "iborRate");
    this.multicurve = builder.multicurve;
    this.timeSeries = ImmutableMap.copyOf(builder.timeSeries);
    this.dayCount = builder.dayCount;
    this.iborRate = ImmutableMap.copyOf(builder.iborRate);
  }

  @Override
  public ImmutableStoredPricingEnvironment.Meta metaBean() {
    return ImmutableStoredPricingEnvironment.Meta.INSTANCE;
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
   * Gets the day count applicable to the models.
   * @return the value of the property, not null
   */
  public DayCount getDayCount() {
    return dayCount;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the full map of data.
   * @return the value of the property, not null
   */
  public Map<IborIndex, Map<LocalDate, Double>> getIborRate() {
    return iborRate;
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
      ImmutableStoredPricingEnvironment other = (ImmutableStoredPricingEnvironment) obj;
      return JodaBeanUtils.equal(getMulticurve(), other.getMulticurve()) &&
          JodaBeanUtils.equal(getTimeSeries(), other.getTimeSeries()) &&
          JodaBeanUtils.equal(getDayCount(), other.getDayCount()) &&
          JodaBeanUtils.equal(getIborRate(), other.getIborRate());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash += hash * 31 + JodaBeanUtils.hashCode(getMulticurve());
    hash += hash * 31 + JodaBeanUtils.hashCode(getTimeSeries());
    hash += hash * 31 + JodaBeanUtils.hashCode(getDayCount());
    hash += hash * 31 + JodaBeanUtils.hashCode(getIborRate());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("ImmutableStoredPricingEnvironment{");
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
    buf.append("dayCount").append('=').append(JodaBeanUtils.toString(getDayCount())).append(',').append(' ');
    buf.append("iborRate").append('=').append(JodaBeanUtils.toString(getIborRate())).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutableStoredPricingEnvironment}.
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
        this, "multicurve", ImmutableStoredPricingEnvironment.class, MulticurveProviderInterface.class);
    /**
     * The meta-property for the {@code timeSeries} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<Index, LocalDateDoubleTimeSeries>> timeSeries = DirectMetaProperty.ofImmutable(
        this, "timeSeries", ImmutableStoredPricingEnvironment.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code dayCount} property.
     */
    private final MetaProperty<DayCount> dayCount = DirectMetaProperty.ofImmutable(
        this, "dayCount", ImmutableStoredPricingEnvironment.class, DayCount.class);
    /**
     * The meta-property for the {@code iborRate} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Map<IborIndex, Map<LocalDate, Double>>> iborRate = DirectMetaProperty.ofImmutable(
        this, "iborRate", ImmutableStoredPricingEnvironment.class, (Class) Map.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "multicurve",
        "timeSeries",
        "dayCount",
        "iborRate");

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
        case 1905311443:  // dayCount
          return dayCount;
        case -1621804100:  // iborRate
          return iborRate;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public ImmutableStoredPricingEnvironment.Builder builder() {
      return new ImmutableStoredPricingEnvironment.Builder();
    }

    @Override
    public Class<? extends ImmutableStoredPricingEnvironment> beanType() {
      return ImmutableStoredPricingEnvironment.class;
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
     * The meta-property for the {@code dayCount} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<DayCount> dayCount() {
      return dayCount;
    }

    /**
     * The meta-property for the {@code iborRate} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Map<IborIndex, Map<LocalDate, Double>>> iborRate() {
      return iborRate;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 1253345110:  // multicurve
          return ((ImmutableStoredPricingEnvironment) bean).getMulticurve();
        case 779431844:  // timeSeries
          return ((ImmutableStoredPricingEnvironment) bean).getTimeSeries();
        case 1905311443:  // dayCount
          return ((ImmutableStoredPricingEnvironment) bean).getDayCount();
        case -1621804100:  // iborRate
          return ((ImmutableStoredPricingEnvironment) bean).getIborRate();
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
   * The bean-builder for {@code ImmutableStoredPricingEnvironment}.
   */
  public static class Builder extends DirectFieldsBeanBuilder<ImmutableStoredPricingEnvironment> {

    private MulticurveProviderInterface multicurve;
    private Map<Index, LocalDateDoubleTimeSeries> timeSeries = new HashMap<Index, LocalDateDoubleTimeSeries>();
    private DayCount dayCount;
    private Map<IborIndex, Map<LocalDate, Double>> iborRate = new HashMap<IborIndex, Map<LocalDate, Double>>();

    /**
     * Restricted constructor.
     */
    protected Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    protected Builder(ImmutableStoredPricingEnvironment beanToCopy) {
      this.multicurve = beanToCopy.getMulticurve();
      this.timeSeries = new HashMap<Index, LocalDateDoubleTimeSeries>(beanToCopy.getTimeSeries());
      this.dayCount = beanToCopy.getDayCount();
      this.iborRate = new HashMap<IborIndex, Map<LocalDate, Double>>(beanToCopy.getIborRate());
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 1253345110:  // multicurve
          return multicurve;
        case 779431844:  // timeSeries
          return timeSeries;
        case 1905311443:  // dayCount
          return dayCount;
        case -1621804100:  // iborRate
          return iborRate;
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
        case 1905311443:  // dayCount
          this.dayCount = (DayCount) newValue;
          break;
        case -1621804100:  // iborRate
          this.iborRate = (Map<IborIndex, Map<LocalDate, Double>>) newValue;
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
    public ImmutableStoredPricingEnvironment build() {
      return new ImmutableStoredPricingEnvironment(this);
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
     * Sets the {@code dayCount} property in the builder.
     * @param dayCount  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder dayCount(DayCount dayCount) {
      JodaBeanUtils.notNull(dayCount, "dayCount");
      this.dayCount = dayCount;
      return this;
    }

    /**
     * Sets the {@code iborRate} property in the builder.
     * @param iborRate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder iborRate(Map<IborIndex, Map<LocalDate, Double>> iborRate) {
      JodaBeanUtils.notNull(iborRate, "iborRate");
      this.iborRate = iborRate;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("ImmutableStoredPricingEnvironment.Builder{");
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
      buf.append("dayCount").append('=').append(JodaBeanUtils.toString(dayCount)).append(',').append(' ');
      buf.append("iborRate").append('=').append(JodaBeanUtils.toString(iborRate)).append(',').append(' ');
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
