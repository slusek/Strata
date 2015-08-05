package com.opengamma.strata.pricer.rate;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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

import com.opengamma.analytics.financial.legalentity.LegalEntity;
import com.opengamma.strata.basics.currency.Currency;
import com.opengamma.strata.basics.currency.CurrencyPair;
import com.opengamma.strata.basics.index.FxIndex;
import com.opengamma.strata.basics.index.IborIndex;
import com.opengamma.strata.basics.index.OvernightIndex;
import com.opengamma.strata.basics.index.PriceIndex;
import com.opengamma.strata.basics.market.MarketDataKey;
import com.opengamma.strata.market.curve.Curve;
import com.opengamma.strata.market.value.DiscountFactors;
import com.opengamma.strata.market.value.FxForwardRates;
import com.opengamma.strata.market.value.FxIndexRates;
import com.opengamma.strata.market.value.IborIndexRates;
import com.opengamma.strata.market.value.IssuerCurveProviders;
import com.opengamma.strata.market.value.OvernightIndexRates;
import com.opengamma.strata.market.value.PriceIndexValues;
import com.opengamma.strata.market.value.ZeroRateDiscountFactors;

@BeanDefinition(builderScope = "private")
public final class DecoratedRatesProvider extends AbstractRatesProvider implements ImmutableBean, Serializable {

  @PropertyDefinition(validate = "notNull")
  private final RatesProvider underlyingRatesProvider;
  @PropertyDefinition(validate = "notNull")
  private final Currency decoratedCurrency;
  @PropertyDefinition(validate = "notNull")
  private final LegalEntity decoratingIssuer;

  public static DecoratedRatesProvider of(RatesProvider underlyingRatesProvider, Currency decoratedCurrency,
      LegalEntity decoratingIssuer) {
    return new DecoratedRatesProvider(underlyingRatesProvider, decoratedCurrency, decoratingIssuer);
  }

  @Override
  public LocalDate getValuationDate() {
    return underlyingRatesProvider.getValuationDate();
  }

  @Override
  public <T> T data(MarketDataKey<T> key) {
    return underlyingRatesProvider.data(key);
  }

  @Override
  public double fxRate(Currency baseCurrency, Currency counterCurrency) {
    return underlyingRatesProvider.fxRate(baseCurrency, counterCurrency);
  }

  @Override
  public DiscountFactors discountFactors(Currency currency) {
    if (currency.equals(decoratedCurrency)) {
      IssuerCurveProviders curves = underlyingRatesProvider.data(IssuerCurveProviders.class);
      Curve curve = curves.getIssuerCurves().get(decoratingIssuer);
      return ZeroRateDiscountFactors.of(currency, getValuationDate(), curve);
    }
    return underlyingRatesProvider.discountFactors(currency);
  }

  // TODO check curveParameterSensitivity properly works

  @Override
  public <T> T data(Class<T> type) {
    return underlyingRatesProvider.data(type);
  }

  @Override
  public FxIndexRates fxIndexRates(FxIndex index) {
    return underlyingRatesProvider.fxIndexRates(index);
  }

  @Override
  public FxForwardRates fxForwardRates(CurrencyPair currencyPair) {
    return underlyingRatesProvider.fxForwardRates(currencyPair);
  }

  @Override
  public IborIndexRates iborIndexRates(IborIndex index) {
    return underlyingRatesProvider.iborIndexRates(index);
  }

  @Override
  public OvernightIndexRates overnightIndexRates(OvernightIndex index) {
    return underlyingRatesProvider.overnightIndexRates(index);
  }

  @Override
  public PriceIndexValues priceIndexValues(PriceIndex index) {
    return underlyingRatesProvider.priceIndexValues(index);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code DecoratedRatesProvider}.
   * @return the meta-bean, not null
   */
  public static DecoratedRatesProvider.Meta meta() {
    return DecoratedRatesProvider.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(DecoratedRatesProvider.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  private DecoratedRatesProvider(
      RatesProvider underlyingRatesProvider,
      Currency decoratedCurrency,
      LegalEntity decoratingIssuer) {
    JodaBeanUtils.notNull(underlyingRatesProvider, "underlyingRatesProvider");
    JodaBeanUtils.notNull(decoratedCurrency, "decoratedCurrency");
    JodaBeanUtils.notNull(decoratingIssuer, "decoratingIssuer");
    this.underlyingRatesProvider = underlyingRatesProvider;
    this.decoratedCurrency = decoratedCurrency;
    this.decoratingIssuer = decoratingIssuer;
  }

  @Override
  public DecoratedRatesProvider.Meta metaBean() {
    return DecoratedRatesProvider.Meta.INSTANCE;
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
   * Gets the underlyingRatesProvider.
   * @return the value of the property, not null
   */
  public RatesProvider getUnderlyingRatesProvider() {
    return underlyingRatesProvider;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the decoratedCurrency.
   * @return the value of the property, not null
   */
  public Currency getDecoratedCurrency() {
    return decoratedCurrency;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the decoratingIssuer.
   * @return the value of the property, not null
   */
  public LegalEntity getDecoratingIssuer() {
    return decoratingIssuer;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      DecoratedRatesProvider other = (DecoratedRatesProvider) obj;
      return JodaBeanUtils.equal(getUnderlyingRatesProvider(), other.getUnderlyingRatesProvider()) &&
          JodaBeanUtils.equal(getDecoratedCurrency(), other.getDecoratedCurrency()) &&
          JodaBeanUtils.equal(getDecoratingIssuer(), other.getDecoratingIssuer());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(getUnderlyingRatesProvider());
    hash = hash * 31 + JodaBeanUtils.hashCode(getDecoratedCurrency());
    hash = hash * 31 + JodaBeanUtils.hashCode(getDecoratingIssuer());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("DecoratedRatesProvider{");
    buf.append("underlyingRatesProvider").append('=').append(getUnderlyingRatesProvider()).append(',').append(' ');
    buf.append("decoratedCurrency").append('=').append(getDecoratedCurrency()).append(',').append(' ');
    buf.append("decoratingIssuer").append('=').append(JodaBeanUtils.toString(getDecoratingIssuer()));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code DecoratedRatesProvider}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code underlyingRatesProvider} property.
     */
    private final MetaProperty<RatesProvider> underlyingRatesProvider = DirectMetaProperty.ofImmutable(
        this, "underlyingRatesProvider", DecoratedRatesProvider.class, RatesProvider.class);
    /**
     * The meta-property for the {@code decoratedCurrency} property.
     */
    private final MetaProperty<Currency> decoratedCurrency = DirectMetaProperty.ofImmutable(
        this, "decoratedCurrency", DecoratedRatesProvider.class, Currency.class);
    /**
     * The meta-property for the {@code decoratingIssuer} property.
     */
    private final MetaProperty<LegalEntity> decoratingIssuer = DirectMetaProperty.ofImmutable(
        this, "decoratingIssuer", DecoratedRatesProvider.class, LegalEntity.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "underlyingRatesProvider",
        "decoratedCurrency",
        "decoratingIssuer");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 912653895:  // underlyingRatesProvider
          return underlyingRatesProvider;
        case 974169128:  // decoratedCurrency
          return decoratedCurrency;
        case 465307619:  // decoratingIssuer
          return decoratingIssuer;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends DecoratedRatesProvider> builder() {
      return new DecoratedRatesProvider.Builder();
    }

    @Override
    public Class<? extends DecoratedRatesProvider> beanType() {
      return DecoratedRatesProvider.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code underlyingRatesProvider} property.
     * @return the meta-property, not null
     */
    public MetaProperty<RatesProvider> underlyingRatesProvider() {
      return underlyingRatesProvider;
    }

    /**
     * The meta-property for the {@code decoratedCurrency} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Currency> decoratedCurrency() {
      return decoratedCurrency;
    }

    /**
     * The meta-property for the {@code decoratingIssuer} property.
     * @return the meta-property, not null
     */
    public MetaProperty<LegalEntity> decoratingIssuer() {
      return decoratingIssuer;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 912653895:  // underlyingRatesProvider
          return ((DecoratedRatesProvider) bean).getUnderlyingRatesProvider();
        case 974169128:  // decoratedCurrency
          return ((DecoratedRatesProvider) bean).getDecoratedCurrency();
        case 465307619:  // decoratingIssuer
          return ((DecoratedRatesProvider) bean).getDecoratingIssuer();
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
   * The bean-builder for {@code DecoratedRatesProvider}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<DecoratedRatesProvider> {

    private RatesProvider underlyingRatesProvider;
    private Currency decoratedCurrency;
    private LegalEntity decoratingIssuer;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 912653895:  // underlyingRatesProvider
          return underlyingRatesProvider;
        case 974169128:  // decoratedCurrency
          return decoratedCurrency;
        case 465307619:  // decoratingIssuer
          return decoratingIssuer;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 912653895:  // underlyingRatesProvider
          this.underlyingRatesProvider = (RatesProvider) newValue;
          break;
        case 974169128:  // decoratedCurrency
          this.decoratedCurrency = (Currency) newValue;
          break;
        case 465307619:  // decoratingIssuer
          this.decoratingIssuer = (LegalEntity) newValue;
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
    public DecoratedRatesProvider build() {
      return new DecoratedRatesProvider(
          underlyingRatesProvider,
          decoratedCurrency,
          decoratingIssuer);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("DecoratedRatesProvider.Builder{");
      buf.append("underlyingRatesProvider").append('=').append(JodaBeanUtils.toString(underlyingRatesProvider)).append(',').append(' ');
      buf.append("decoratedCurrency").append('=').append(JodaBeanUtils.toString(decoratedCurrency)).append(',').append(' ');
      buf.append("decoratingIssuer").append('=').append(JodaBeanUtils.toString(decoratingIssuer));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
