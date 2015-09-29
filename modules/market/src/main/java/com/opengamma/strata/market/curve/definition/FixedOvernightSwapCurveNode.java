/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.market.curve.definition;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
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

import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.BuySell;
import com.opengamma.strata.basics.market.ObservableKey;
import com.opengamma.strata.basics.market.ObservableValues;
import com.opengamma.strata.finance.rate.swap.SwapTrade;
import com.opengamma.strata.finance.rate.swap.type.FixedOvernightSwapTemplate;
import com.opengamma.strata.market.curve.DatedCurveParameterMetadata;
import com.opengamma.strata.market.curve.TenorCurveNodeMetadata;

/**
 * A curve node whose instrument is a Fixed-Overnight interest rate swap.
 */
@BeanDefinition
public final class FixedOvernightSwapCurveNode
    implements CurveNode, ImmutableBean, Serializable {

  /**
   * The template for the swap associated with this node.
   */
  @PropertyDefinition(validate = "notNull")
  private final FixedOvernightSwapTemplate template;
  /**
   * The key identifying the market data value which provides the rate.
   */
  @PropertyDefinition(validate = "notNull")
  private final ObservableKey rateKey;
  /**
   * The spread added to the rate.
   */
  @PropertyDefinition
  private final double spread;

  //-------------------------------------------------------------------------
  /**
   * Returns a curve node for a Fixed-Overnight interest rate swap using the specified instrument template and rate.
   *
   * @param template  the template used for building the instrument for the node
   * @param rateKey  the key identifying the market rate used when building the instrument for the node
   * @return a node whose instrument is built from the template using a market rate
   */
  public static FixedOvernightSwapCurveNode of(FixedOvernightSwapTemplate template, ObservableKey rateKey) {
    return new FixedOvernightSwapCurveNode(template, rateKey, 0);
  }

  /**
   * Returns a curve node for a Fixed-Overnight interest rate swap using the specified instrument template, rate key and spread.
   *
   * @param template  the template defining the node instrument
   * @param rateKey  the key identifying the market data providing the rate for the node instrument
   * @param spread  the spread amount added to the rate
   * @return a node whose instrument is built from the template using a market rate
   */
  public static FixedOvernightSwapCurveNode of(FixedOvernightSwapTemplate template, ObservableKey rateKey, double spread) {
    return new FixedOvernightSwapCurveNode(template, rateKey, spread);
  }

  //-------------------------------------------------------------------------
  @Override
  public Set<ObservableKey> requirements() {
    return ImmutableSet.of(rateKey);
  }

  @Override
  public SwapTrade trade(LocalDate valuationDate, ObservableValues marketData) {
    double fixedRate = marketData.getValue(rateKey) + spread;
    return template.toTrade(valuationDate, BuySell.BUY, 1, fixedRate);
  }

  @Override
  public DatedCurveParameterMetadata metadata(LocalDate valuationDate) {
    SwapTrade trade = template.toTrade(valuationDate, BuySell.BUY, 1, 1);
    return TenorCurveNodeMetadata.of(trade.getProduct().getEndDate(), template.getTenor());
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code FixedOvernightSwapCurveNode}.
   * @return the meta-bean, not null
   */
  public static FixedOvernightSwapCurveNode.Meta meta() {
    return FixedOvernightSwapCurveNode.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(FixedOvernightSwapCurveNode.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static FixedOvernightSwapCurveNode.Builder builder() {
    return new FixedOvernightSwapCurveNode.Builder();
  }

  private FixedOvernightSwapCurveNode(
      FixedOvernightSwapTemplate template,
      ObservableKey rateKey,
      double spread) {
    JodaBeanUtils.notNull(template, "template");
    JodaBeanUtils.notNull(rateKey, "rateKey");
    this.template = template;
    this.rateKey = rateKey;
    this.spread = spread;
  }

  @Override
  public FixedOvernightSwapCurveNode.Meta metaBean() {
    return FixedOvernightSwapCurveNode.Meta.INSTANCE;
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
   * Gets the template for the swap associated with this node.
   * @return the value of the property, not null
   */
  public FixedOvernightSwapTemplate getTemplate() {
    return template;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the key identifying the market data value which provides the rate.
   * @return the value of the property, not null
   */
  public ObservableKey getRateKey() {
    return rateKey;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the spread added to the rate.
   * @return the value of the property
   */
  public double getSpread() {
    return spread;
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
      FixedOvernightSwapCurveNode other = (FixedOvernightSwapCurveNode) obj;
      return JodaBeanUtils.equal(getTemplate(), other.getTemplate()) &&
          JodaBeanUtils.equal(getRateKey(), other.getRateKey()) &&
          JodaBeanUtils.equal(getSpread(), other.getSpread());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getTemplate());
    hash = hash * 31 + JodaBeanUtils.hashCode(getRateKey());
    hash = hash * 31 + JodaBeanUtils.hashCode(getSpread());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("FixedOvernightSwapCurveNode{");
    buf.append("template").append('=').append(getTemplate()).append(',').append(' ');
    buf.append("rateKey").append('=').append(getRateKey()).append(',').append(' ');
    buf.append("spread").append('=').append(JodaBeanUtils.toString(getSpread()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code FixedOvernightSwapCurveNode}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code template} property.
     */
    private final MetaProperty<FixedOvernightSwapTemplate> template = DirectMetaProperty.ofImmutable(
        this, "template", FixedOvernightSwapCurveNode.class, FixedOvernightSwapTemplate.class);
    /**
     * The meta-property for the {@code rateKey} property.
     */
    private final MetaProperty<ObservableKey> rateKey = DirectMetaProperty.ofImmutable(
        this, "rateKey", FixedOvernightSwapCurveNode.class, ObservableKey.class);
    /**
     * The meta-property for the {@code spread} property.
     */
    private final MetaProperty<Double> spread = DirectMetaProperty.ofImmutable(
        this, "spread", FixedOvernightSwapCurveNode.class, Double.TYPE);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "template",
        "rateKey",
        "spread");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          return template;
        case 983444831:  // rateKey
          return rateKey;
        case -895684237:  // spread
          return spread;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public FixedOvernightSwapCurveNode.Builder builder() {
      return new FixedOvernightSwapCurveNode.Builder();
    }

    @Override
    public Class<? extends FixedOvernightSwapCurveNode> beanType() {
      return FixedOvernightSwapCurveNode.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code template} property.
     * @return the meta-property, not null
     */
    public MetaProperty<FixedOvernightSwapTemplate> template() {
      return template;
    }

    /**
     * The meta-property for the {@code rateKey} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ObservableKey> rateKey() {
      return rateKey;
    }

    /**
     * The meta-property for the {@code spread} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> spread() {
      return spread;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          return ((FixedOvernightSwapCurveNode) bean).getTemplate();
        case 983444831:  // rateKey
          return ((FixedOvernightSwapCurveNode) bean).getRateKey();
        case -895684237:  // spread
          return ((FixedOvernightSwapCurveNode) bean).getSpread();
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
   * The bean-builder for {@code FixedOvernightSwapCurveNode}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<FixedOvernightSwapCurveNode> {

    private FixedOvernightSwapTemplate template;
    private ObservableKey rateKey;
    private double spread;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(FixedOvernightSwapCurveNode beanToCopy) {
      this.template = beanToCopy.getTemplate();
      this.rateKey = beanToCopy.getRateKey();
      this.spread = beanToCopy.getSpread();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          return template;
        case 983444831:  // rateKey
          return rateKey;
        case -895684237:  // spread
          return spread;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -1321546630:  // template
          this.template = (FixedOvernightSwapTemplate) newValue;
          break;
        case 983444831:  // rateKey
          this.rateKey = (ObservableKey) newValue;
          break;
        case -895684237:  // spread
          this.spread = (Double) newValue;
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
    public FixedOvernightSwapCurveNode build() {
      return new FixedOvernightSwapCurveNode(
          template,
          rateKey,
          spread);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the template for the swap associated with this node.
     * @param template  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder template(FixedOvernightSwapTemplate template) {
      JodaBeanUtils.notNull(template, "template");
      this.template = template;
      return this;
    }

    /**
     * Sets the key identifying the market data value which provides the rate.
     * @param rateKey  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder rateKey(ObservableKey rateKey) {
      JodaBeanUtils.notNull(rateKey, "rateKey");
      this.rateKey = rateKey;
      return this;
    }

    /**
     * Sets the spread added to the rate.
     * @param spread  the new value
     * @return this, for chaining, not null
     */
    public Builder spread(double spread) {
      this.spread = spread;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("FixedOvernightSwapCurveNode.Builder{");
      buf.append("template").append('=').append(JodaBeanUtils.toString(template)).append(',').append(' ');
      buf.append("rateKey").append('=').append(JodaBeanUtils.toString(rateKey)).append(',').append(' ');
      buf.append("spread").append('=').append(JodaBeanUtils.toString(spread));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
