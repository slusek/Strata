/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.finance.rate.bond;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableDefaults;
import org.joda.beans.ImmutableValidator;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.strata.basics.PutCall;
import com.opengamma.strata.basics.value.Rounding;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.id.LinkResolutionException;
import com.opengamma.strata.collect.id.LinkResolver;
import com.opengamma.strata.collect.id.Resolvable;
import com.opengamma.strata.collect.id.StandardId;
import com.opengamma.strata.finance.Product;
import com.opengamma.strata.finance.Security;
import com.opengamma.strata.finance.SecurityLink;
import com.opengamma.strata.finance.common.FutureOptionPremiumStyle;

/**
 * A futures option contract, based on bonds.
 * <p>
 * A bond future option is a financial instrument that provides an option based on the future value of
 * fixed coupon bonds. The option is American, exercised at any point up to the exercise time.
 * It handles options with either daily margining or upfront premium.
 * <p>
 * This class represents the structure of a single option contract.
 */
@BeanDefinition
public final class BondFutureOption
    implements Product, Resolvable<BondFutureOption>, ImmutableBean, Serializable {

  /**
   * Whether the option is put or call.
   * <p>
   * A call gives the owner the right, but not obligation, to buy the underlying at
   * an agreed price in the future. A put gives a similar option to sell.
   */
  @PropertyDefinition
  private final PutCall putCall;
  /**
   * The strike price, represented in decimal form.
   * <p>
   * This is the price at which the option applies and refers to the price of the underlying future.
   * This must be represented in decimal form, {@code (1.0 - decimalRate)}. 
   * As such, the common market price of 99.3 for a 0.7% rate must be input as 0.993.
   * The rate implied by the strike can take negative values.
   */
  @PropertyDefinition
  private final double strikePrice;
  /**
   * The expiration date of the option.  
   * <p>
   * The expiration date is related to the expiration time and time-zone.
   * The date must not be after last trade date of the underlying future. 
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalDate expirationDate;
  /**
   * The expiration time of the option.  
   * <p>
   * The expiration time is related to the expiration date and time-zone.
   */
  @PropertyDefinition(validate = "notNull")
  private final LocalTime expirationTime;
  /**
   * The time-zone of the expiration time.  
   * <p>
   * The expiration time-zone is related to the expiration date and time.
   */
  @PropertyDefinition(validate = "notNull")
  private final ZoneId expirationZone;
  /**
   * The style of the option premium.
   * <p>
   * The two options are daily margining and upfront premium.
   */
  @PropertyDefinition(validate = "notNull")
  private final FutureOptionPremiumStyle premiumStyle;
  /**
   * The definition of how to round the option price, defaulted to no rounding.
   * <p>
   * The price is represented in decimal form, not percentage form.
   * As such, the decimal places expressed by the rounding refers to this decimal form.
   * For example, the common market price of 99.7125 is represented as 0.997125 which
   * has 6 decimal places.
   */
  @PropertyDefinition(validate = "notNull")
  private final Rounding rounding;
  /**
   * The link to the underlying future.
   * <p>
   * This property returns a link to the security via a {@link StandardId}.
   * See {@link #getUnderlying()} and {@link SecurityLink} for more details.
   */
  @PropertyDefinition(validate = "notNull")
  private final SecurityLink<BondFuture> underlyingLink;

  //-------------------------------------------------------------------------
  @ImmutableValidator
  private void validate() {
    if (underlyingLink.isResolved()) {
      LocalDate lastTradeDate = underlyingLink.resolve(null).getProduct().getLastTradeDate();
      ArgChecker.inOrderOrEqual(expirationDate, lastTradeDate, "expirationDate", "lastTradeDate");
    }
  }

  @ImmutableDefaults
  private static void applyDefaults(Builder builder) {
    builder.rounding(Rounding.none());
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the expiration date-time.
   * <p>
   * The option expires at this date and time.
   * <p>
   * The result is returned by combining the expiration date, time and time-zone.
   * 
   * @return the expiration date and time
   */
  public ZonedDateTime getExpiration() {
    return expirationDate.atTime(expirationTime).atZone(expirationZone);
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the underlying bond future security that was traded, throwing an exception if not resolved.
   * <p>
   * This method accesses the security via the {@link #getUnderlyingLink() underlyingLink} property.
   * The link has two states, resolvable and resolved.
   * <p>
   * In the resolved state, the security is known and available for use.
   * The security object will be directly embedded in the link held within this trade.
   * <p>
   * In the resolvable state, only the identifier and type of the security are known.
   * These act as a pointer to the security, and as such the security is not directly available.
   * The link must be resolved before use.
   * This can be achieved by calling {@link #resolveLinks(LinkResolver)} on this trade.
   * If the trade has not been resolved, then this method will throw a {@link LinkResolutionException}.
   * 
   * @return full details of the security
   * @throws LinkResolutionException if the security is not resolved
   */
  public Security<BondFuture> getUnderlyingSecurity() {
    return underlyingLink.resolvedTarget();
  }

  /**
   * Gets the underlying bond future that was traded, throwing an exception if not resolved.
   * <p>
   * Returns the underlying product that captures the contracted financial details of the trade.
   * This method accesses the security via the {@link #getUnderlyingLink() underlyingLink} property.
   * The link has two states, resolvable and resolved.
   * <p>
   * In the resolved state, the security is known and available for use.
   * The security object will be directly embedded in the link held within this trade.
   * <p>
   * In the resolvable state, only the identifier and type of the security are known.
   * These act as a pointer to the security, and as such the security is not directly available.
   * The link must be resolved before use.
   * This can be achieved by calling {@link #resolveLinks(LinkResolver)} on this trade.
   * If the trade has not been resolved, then this method will throw a {@link LinkResolutionException}.
   * 
   * @return the product underlying the option
   * @throws LinkResolutionException if the security is not resolved
   */
  public BondFuture getUnderlying() {
    return getUnderlyingSecurity().getProduct();
  }

  //-------------------------------------------------------------------------
  @Override
  public BondFutureOption resolveLinks(LinkResolver resolver) {
    return resolver.resolveLinksIn(this, underlyingLink, resolved -> toBuilder().underlyingLink(resolved).build());
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code BondFutureOption}.
   * @return the meta-bean, not null
   */
  public static BondFutureOption.Meta meta() {
    return BondFutureOption.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(BondFutureOption.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static BondFutureOption.Builder builder() {
    return new BondFutureOption.Builder();
  }

  private BondFutureOption(
      PutCall putCall,
      double strikePrice,
      LocalDate expirationDate,
      LocalTime expirationTime,
      ZoneId expirationZone,
      FutureOptionPremiumStyle premiumStyle,
      Rounding rounding,
      SecurityLink<BondFuture> underlyingLink) {
    JodaBeanUtils.notNull(expirationDate, "expirationDate");
    JodaBeanUtils.notNull(expirationTime, "expirationTime");
    JodaBeanUtils.notNull(expirationZone, "expirationZone");
    JodaBeanUtils.notNull(premiumStyle, "premiumStyle");
    JodaBeanUtils.notNull(rounding, "rounding");
    JodaBeanUtils.notNull(underlyingLink, "underlyingLink");
    this.putCall = putCall;
    this.strikePrice = strikePrice;
    this.expirationDate = expirationDate;
    this.expirationTime = expirationTime;
    this.expirationZone = expirationZone;
    this.premiumStyle = premiumStyle;
    this.rounding = rounding;
    this.underlyingLink = underlyingLink;
    validate();
  }

  @Override
  public BondFutureOption.Meta metaBean() {
    return BondFutureOption.Meta.INSTANCE;
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
   * Gets whether the option is put or call.
   * <p>
   * A call gives the owner the right, but not obligation, to buy the underlying at
   * an agreed price in the future. A put gives a similar option to sell.
   * @return the value of the property
   */
  public PutCall getPutCall() {
    return putCall;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the strike price, represented in decimal form.
   * <p>
   * This is the price at which the option applies and refers to the price of the underlying future.
   * This must be represented in decimal form, {@code (1.0 - decimalRate)}.
   * As such, the common market price of 99.3 for a 0.7% rate must be input as 0.993.
   * The rate implied by the strike can take negative values.
   * @return the value of the property
   */
  public double getStrikePrice() {
    return strikePrice;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the expiration date of the option.
   * <p>
   * The expiration date is related to the expiration time and time-zone.
   * The date must not be after last trade date of the underlying future.
   * @return the value of the property, not null
   */
  public LocalDate getExpirationDate() {
    return expirationDate;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the expiration time of the option.
   * <p>
   * The expiration time is related to the expiration date and time-zone.
   * @return the value of the property, not null
   */
  public LocalTime getExpirationTime() {
    return expirationTime;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the time-zone of the expiration time.
   * <p>
   * The expiration time-zone is related to the expiration date and time.
   * @return the value of the property, not null
   */
  public ZoneId getExpirationZone() {
    return expirationZone;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the style of the option premium.
   * <p>
   * The two options are daily margining and upfront premium.
   * @return the value of the property, not null
   */
  public FutureOptionPremiumStyle getPremiumStyle() {
    return premiumStyle;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the definition of how to round the option price, defaulted to no rounding.
   * <p>
   * The price is represented in decimal form, not percentage form.
   * As such, the decimal places expressed by the rounding refers to this decimal form.
   * For example, the common market price of 99.7125 is represented as 0.997125 which
   * has 6 decimal places.
   * @return the value of the property, not null
   */
  public Rounding getRounding() {
    return rounding;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the link to the underlying future.
   * <p>
   * This property returns a link to the security via a {@link StandardId}.
   * See {@link #getUnderlying()} and {@link SecurityLink} for more details.
   * @return the value of the property, not null
   */
  public SecurityLink<BondFuture> getUnderlyingLink() {
    return underlyingLink;
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
      BondFutureOption other = (BondFutureOption) obj;
      return JodaBeanUtils.equal(getPutCall(), other.getPutCall()) &&
          JodaBeanUtils.equal(getStrikePrice(), other.getStrikePrice()) &&
          JodaBeanUtils.equal(getExpirationDate(), other.getExpirationDate()) &&
          JodaBeanUtils.equal(getExpirationTime(), other.getExpirationTime()) &&
          JodaBeanUtils.equal(getExpirationZone(), other.getExpirationZone()) &&
          JodaBeanUtils.equal(getPremiumStyle(), other.getPremiumStyle()) &&
          JodaBeanUtils.equal(getRounding(), other.getRounding()) &&
          JodaBeanUtils.equal(getUnderlyingLink(), other.getUnderlyingLink());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getPutCall());
    hash = hash * 31 + JodaBeanUtils.hashCode(getStrikePrice());
    hash = hash * 31 + JodaBeanUtils.hashCode(getExpirationDate());
    hash = hash * 31 + JodaBeanUtils.hashCode(getExpirationTime());
    hash = hash * 31 + JodaBeanUtils.hashCode(getExpirationZone());
    hash = hash * 31 + JodaBeanUtils.hashCode(getPremiumStyle());
    hash = hash * 31 + JodaBeanUtils.hashCode(getRounding());
    hash = hash * 31 + JodaBeanUtils.hashCode(getUnderlyingLink());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(288);
    buf.append("BondFutureOption{");
    buf.append("putCall").append('=').append(getPutCall()).append(',').append(' ');
    buf.append("strikePrice").append('=').append(getStrikePrice()).append(',').append(' ');
    buf.append("expirationDate").append('=').append(getExpirationDate()).append(',').append(' ');
    buf.append("expirationTime").append('=').append(getExpirationTime()).append(',').append(' ');
    buf.append("expirationZone").append('=').append(getExpirationZone()).append(',').append(' ');
    buf.append("premiumStyle").append('=').append(getPremiumStyle()).append(',').append(' ');
    buf.append("rounding").append('=').append(getRounding()).append(',').append(' ');
    buf.append("underlyingLink").append('=').append(JodaBeanUtils.toString(getUnderlyingLink()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code BondFutureOption}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code putCall} property.
     */
    private final MetaProperty<PutCall> putCall = DirectMetaProperty.ofImmutable(
        this, "putCall", BondFutureOption.class, PutCall.class);
    /**
     * The meta-property for the {@code strikePrice} property.
     */
    private final MetaProperty<Double> strikePrice = DirectMetaProperty.ofImmutable(
        this, "strikePrice", BondFutureOption.class, Double.TYPE);
    /**
     * The meta-property for the {@code expirationDate} property.
     */
    private final MetaProperty<LocalDate> expirationDate = DirectMetaProperty.ofImmutable(
        this, "expirationDate", BondFutureOption.class, LocalDate.class);
    /**
     * The meta-property for the {@code expirationTime} property.
     */
    private final MetaProperty<LocalTime> expirationTime = DirectMetaProperty.ofImmutable(
        this, "expirationTime", BondFutureOption.class, LocalTime.class);
    /**
     * The meta-property for the {@code expirationZone} property.
     */
    private final MetaProperty<ZoneId> expirationZone = DirectMetaProperty.ofImmutable(
        this, "expirationZone", BondFutureOption.class, ZoneId.class);
    /**
     * The meta-property for the {@code premiumStyle} property.
     */
    private final MetaProperty<FutureOptionPremiumStyle> premiumStyle = DirectMetaProperty.ofImmutable(
        this, "premiumStyle", BondFutureOption.class, FutureOptionPremiumStyle.class);
    /**
     * The meta-property for the {@code rounding} property.
     */
    private final MetaProperty<Rounding> rounding = DirectMetaProperty.ofImmutable(
        this, "rounding", BondFutureOption.class, Rounding.class);
    /**
     * The meta-property for the {@code underlyingLink} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<SecurityLink<BondFuture>> underlyingLink = DirectMetaProperty.ofImmutable(
        this, "underlyingLink", BondFutureOption.class, (Class) SecurityLink.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "putCall",
        "strikePrice",
        "expirationDate",
        "expirationTime",
        "expirationZone",
        "premiumStyle",
        "rounding",
        "underlyingLink");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -219971059:  // putCall
          return putCall;
        case 50946231:  // strikePrice
          return strikePrice;
        case -668811523:  // expirationDate
          return expirationDate;
        case -668327396:  // expirationTime
          return expirationTime;
        case -668142853:  // expirationZone
          return expirationZone;
        case -1257652838:  // premiumStyle
          return premiumStyle;
        case -142444:  // rounding
          return rounding;
        case 1497199863:  // underlyingLink
          return underlyingLink;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BondFutureOption.Builder builder() {
      return new BondFutureOption.Builder();
    }

    @Override
    public Class<? extends BondFutureOption> beanType() {
      return BondFutureOption.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code putCall} property.
     * @return the meta-property, not null
     */
    public MetaProperty<PutCall> putCall() {
      return putCall;
    }

    /**
     * The meta-property for the {@code strikePrice} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Double> strikePrice() {
      return strikePrice;
    }

    /**
     * The meta-property for the {@code expirationDate} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalDate> expirationDate() {
      return expirationDate;
    }

    /**
     * The meta-property for the {@code expirationTime} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LocalTime> expirationTime() {
      return expirationTime;
    }

    /**
     * The meta-property for the {@code expirationZone} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ZoneId> expirationZone() {
      return expirationZone;
    }

    /**
     * The meta-property for the {@code premiumStyle} property.
     * @return the meta-property, not null
     */
    public MetaProperty<FutureOptionPremiumStyle> premiumStyle() {
      return premiumStyle;
    }

    /**
     * The meta-property for the {@code rounding} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Rounding> rounding() {
      return rounding;
    }

    /**
     * The meta-property for the {@code underlyingLink} property.
     * @return the meta-property, not null
     */
    public MetaProperty<SecurityLink<BondFuture>> underlyingLink() {
      return underlyingLink;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -219971059:  // putCall
          return ((BondFutureOption) bean).getPutCall();
        case 50946231:  // strikePrice
          return ((BondFutureOption) bean).getStrikePrice();
        case -668811523:  // expirationDate
          return ((BondFutureOption) bean).getExpirationDate();
        case -668327396:  // expirationTime
          return ((BondFutureOption) bean).getExpirationTime();
        case -668142853:  // expirationZone
          return ((BondFutureOption) bean).getExpirationZone();
        case -1257652838:  // premiumStyle
          return ((BondFutureOption) bean).getPremiumStyle();
        case -142444:  // rounding
          return ((BondFutureOption) bean).getRounding();
        case 1497199863:  // underlyingLink
          return ((BondFutureOption) bean).getUnderlyingLink();
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
   * The bean-builder for {@code BondFutureOption}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<BondFutureOption> {

    private PutCall putCall;
    private double strikePrice;
    private LocalDate expirationDate;
    private LocalTime expirationTime;
    private ZoneId expirationZone;
    private FutureOptionPremiumStyle premiumStyle;
    private Rounding rounding;
    private SecurityLink<BondFuture> underlyingLink;

    /**
     * Restricted constructor.
     */
    private Builder() {
      applyDefaults(this);
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(BondFutureOption beanToCopy) {
      this.putCall = beanToCopy.getPutCall();
      this.strikePrice = beanToCopy.getStrikePrice();
      this.expirationDate = beanToCopy.getExpirationDate();
      this.expirationTime = beanToCopy.getExpirationTime();
      this.expirationZone = beanToCopy.getExpirationZone();
      this.premiumStyle = beanToCopy.getPremiumStyle();
      this.rounding = beanToCopy.getRounding();
      this.underlyingLink = beanToCopy.getUnderlyingLink();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -219971059:  // putCall
          return putCall;
        case 50946231:  // strikePrice
          return strikePrice;
        case -668811523:  // expirationDate
          return expirationDate;
        case -668327396:  // expirationTime
          return expirationTime;
        case -668142853:  // expirationZone
          return expirationZone;
        case -1257652838:  // premiumStyle
          return premiumStyle;
        case -142444:  // rounding
          return rounding;
        case 1497199863:  // underlyingLink
          return underlyingLink;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -219971059:  // putCall
          this.putCall = (PutCall) newValue;
          break;
        case 50946231:  // strikePrice
          this.strikePrice = (Double) newValue;
          break;
        case -668811523:  // expirationDate
          this.expirationDate = (LocalDate) newValue;
          break;
        case -668327396:  // expirationTime
          this.expirationTime = (LocalTime) newValue;
          break;
        case -668142853:  // expirationZone
          this.expirationZone = (ZoneId) newValue;
          break;
        case -1257652838:  // premiumStyle
          this.premiumStyle = (FutureOptionPremiumStyle) newValue;
          break;
        case -142444:  // rounding
          this.rounding = (Rounding) newValue;
          break;
        case 1497199863:  // underlyingLink
          this.underlyingLink = (SecurityLink<BondFuture>) newValue;
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
    public BondFutureOption build() {
      return new BondFutureOption(
          putCall,
          strikePrice,
          expirationDate,
          expirationTime,
          expirationZone,
          premiumStyle,
          rounding,
          underlyingLink);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets whether the option is put or call.
     * <p>
     * A call gives the owner the right, but not obligation, to buy the underlying at
     * an agreed price in the future. A put gives a similar option to sell.
     * @param putCall  the new value
     * @return this, for chaining, not null
     */
    public Builder putCall(PutCall putCall) {
      this.putCall = putCall;
      return this;
    }

    /**
     * Sets the strike price, represented in decimal form.
     * <p>
     * This is the price at which the option applies and refers to the price of the underlying future.
     * This must be represented in decimal form, {@code (1.0 - decimalRate)}.
     * As such, the common market price of 99.3 for a 0.7% rate must be input as 0.993.
     * The rate implied by the strike can take negative values.
     * @param strikePrice  the new value
     * @return this, for chaining, not null
     */
    public Builder strikePrice(double strikePrice) {
      this.strikePrice = strikePrice;
      return this;
    }

    /**
     * Sets the expiration date of the option.
     * <p>
     * The expiration date is related to the expiration time and time-zone.
     * The date must not be after last trade date of the underlying future.
     * @param expirationDate  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder expirationDate(LocalDate expirationDate) {
      JodaBeanUtils.notNull(expirationDate, "expirationDate");
      this.expirationDate = expirationDate;
      return this;
    }

    /**
     * Sets the expiration time of the option.
     * <p>
     * The expiration time is related to the expiration date and time-zone.
     * @param expirationTime  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder expirationTime(LocalTime expirationTime) {
      JodaBeanUtils.notNull(expirationTime, "expirationTime");
      this.expirationTime = expirationTime;
      return this;
    }

    /**
     * Sets the time-zone of the expiration time.
     * <p>
     * The expiration time-zone is related to the expiration date and time.
     * @param expirationZone  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder expirationZone(ZoneId expirationZone) {
      JodaBeanUtils.notNull(expirationZone, "expirationZone");
      this.expirationZone = expirationZone;
      return this;
    }

    /**
     * Sets the style of the option premium.
     * <p>
     * The two options are daily margining and upfront premium.
     * @param premiumStyle  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder premiumStyle(FutureOptionPremiumStyle premiumStyle) {
      JodaBeanUtils.notNull(premiumStyle, "premiumStyle");
      this.premiumStyle = premiumStyle;
      return this;
    }

    /**
     * Sets the definition of how to round the option price, defaulted to no rounding.
     * <p>
     * The price is represented in decimal form, not percentage form.
     * As such, the decimal places expressed by the rounding refers to this decimal form.
     * For example, the common market price of 99.7125 is represented as 0.997125 which
     * has 6 decimal places.
     * @param rounding  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder rounding(Rounding rounding) {
      JodaBeanUtils.notNull(rounding, "rounding");
      this.rounding = rounding;
      return this;
    }

    /**
     * Sets the link to the underlying future.
     * <p>
     * This property returns a link to the security via a {@link StandardId}.
     * See {@link #getUnderlying()} and {@link SecurityLink} for more details.
     * @param underlyingLink  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder underlyingLink(SecurityLink<BondFuture> underlyingLink) {
      JodaBeanUtils.notNull(underlyingLink, "underlyingLink");
      this.underlyingLink = underlyingLink;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(288);
      buf.append("BondFutureOption.Builder{");
      buf.append("putCall").append('=').append(JodaBeanUtils.toString(putCall)).append(',').append(' ');
      buf.append("strikePrice").append('=').append(JodaBeanUtils.toString(strikePrice)).append(',').append(' ');
      buf.append("expirationDate").append('=').append(JodaBeanUtils.toString(expirationDate)).append(',').append(' ');
      buf.append("expirationTime").append('=').append(JodaBeanUtils.toString(expirationTime)).append(',').append(' ');
      buf.append("expirationZone").append('=').append(JodaBeanUtils.toString(expirationZone)).append(',').append(' ');
      buf.append("premiumStyle").append('=').append(JodaBeanUtils.toString(premiumStyle)).append(',').append(' ');
      buf.append("rounding").append('=').append(JodaBeanUtils.toString(rounding)).append(',').append(' ');
      buf.append("underlyingLink").append('=').append(JodaBeanUtils.toString(underlyingLink));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
