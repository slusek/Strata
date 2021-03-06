/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calc.config.pricing;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.opengamma.strata.basics.CalculationTarget;
import com.opengamma.strata.calc.config.FunctionConfig;
import com.opengamma.strata.calc.config.Measure;

/**
 * The default implementation of {@link FunctionGroup}.
 * 
 * @param <T>  the type of the calculation target
 */
@BeanDefinition(builderScope = "private", constructorScope = "package")
public final class DefaultFunctionGroup<T extends CalculationTarget>
    implements ImmutableBean, FunctionGroup<T> {

  /** The name of this function group. */
  @PropertyDefinition(validate = "notNull")
  private final FunctionGroupName name;

  /** The type of the calculation target handled by the functions in the group. */
  @PropertyDefinition(validate = "notNull")
  private final Class<T> targetType;

  /** The functions in the group, keyed by the measure they calculate. */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<Measure, FunctionConfig<T>> functionConfig;

  /** The arguments used when creating functions. */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableMap<String, Object> functionArguments;

  /**
   * Returns a mutable builder for building a default function group.
   *
   * @param targetType  the type of calculation target handled by the function group
   * @param <T>  the type of calculation target handled by the function group
   * @return a mutable builder for building a default function group
   */
  public static <T extends CalculationTarget> DefaultFunctionGroupBuilder<T> builder(Class<T> targetType) {
    return new DefaultFunctionGroupBuilder<>(targetType);
  }

  /**
   * Returns a function group to calculate a value of the measure for the target if this rule applies to the target.
   *
   * @param target  a target
   * @param measure  a measure
   * @return a function group to calculate a value of the measure for the target if this rule applies to the target
   */
  @Override
  public Optional<FunctionConfig<T>> functionConfig(CalculationTarget target, Measure measure) {
    return targetType.isInstance(target) ?
        Optional.ofNullable(this.functionConfig.get(measure)) :
        Optional.empty();
  }

  @Override
  public ImmutableSet<Measure> configuredMeasures(CalculationTarget target) {
    return functionConfig.keySet();
  }

  // TODO Method to return parameter metadata for parameters that can be specified in config.
  //   The metadata should include an optional default value

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code DefaultFunctionGroup}.
   * @return the meta-bean, not null
   */
  @SuppressWarnings("rawtypes")
  public static DefaultFunctionGroup.Meta meta() {
    return DefaultFunctionGroup.Meta.INSTANCE;
  }

  /**
   * The meta-bean for {@code DefaultFunctionGroup}.
   * @param <R>  the bean's generic type
   * @param cls  the bean's generic type
   * @return the meta-bean, not null
   */
  @SuppressWarnings("unchecked")
  public static <R extends CalculationTarget> DefaultFunctionGroup.Meta<R> metaDefaultFunctionGroup(Class<R> cls) {
    return DefaultFunctionGroup.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(DefaultFunctionGroup.Meta.INSTANCE);
  }

  /**
   * Creates an instance.
   * @param name  the value of the property, not null
   * @param targetType  the value of the property, not null
   * @param functionConfig  the value of the property, not null
   * @param functionArguments  the value of the property, not null
   */
  DefaultFunctionGroup(
      FunctionGroupName name,
      Class<T> targetType,
      Map<Measure, FunctionConfig<T>> functionConfig,
      Map<String, Object> functionArguments) {
    JodaBeanUtils.notNull(name, "name");
    JodaBeanUtils.notNull(targetType, "targetType");
    JodaBeanUtils.notNull(functionConfig, "functionConfig");
    JodaBeanUtils.notNull(functionArguments, "functionArguments");
    this.name = name;
    this.targetType = targetType;
    this.functionConfig = ImmutableMap.copyOf(functionConfig);
    this.functionArguments = ImmutableMap.copyOf(functionArguments);
  }

  @SuppressWarnings("unchecked")
  @Override
  public DefaultFunctionGroup.Meta<T> metaBean() {
    return DefaultFunctionGroup.Meta.INSTANCE;
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
   * Gets the name of this function group.
   * @return the value of the property, not null
   */
  public FunctionGroupName getName() {
    return name;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the type of the calculation target handled by the functions in the group.
   * @return the value of the property, not null
   */
  public Class<T> getTargetType() {
    return targetType;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the functions in the group, keyed by the measure they calculate.
   * @return the value of the property, not null
   */
  public ImmutableMap<Measure, FunctionConfig<T>> getFunctionConfig() {
    return functionConfig;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the arguments used when creating functions.
   * @return the value of the property, not null
   */
  public ImmutableMap<String, Object> getFunctionArguments() {
    return functionArguments;
  }

  //-----------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      DefaultFunctionGroup<?> other = (DefaultFunctionGroup<?>) obj;
      return JodaBeanUtils.equal(name, other.name) &&
          JodaBeanUtils.equal(targetType, other.targetType) &&
          JodaBeanUtils.equal(functionConfig, other.functionConfig) &&
          JodaBeanUtils.equal(functionArguments, other.functionArguments);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(name);
    hash = hash * 31 + JodaBeanUtils.hashCode(targetType);
    hash = hash * 31 + JodaBeanUtils.hashCode(functionConfig);
    hash = hash * 31 + JodaBeanUtils.hashCode(functionArguments);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("DefaultFunctionGroup{");
    buf.append("name").append('=').append(name).append(',').append(' ');
    buf.append("targetType").append('=').append(targetType).append(',').append(' ');
    buf.append("functionConfig").append('=').append(functionConfig).append(',').append(' ');
    buf.append("functionArguments").append('=').append(JodaBeanUtils.toString(functionArguments));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code DefaultFunctionGroup}.
   * @param <T>  the type
   */
  public static final class Meta<T extends CalculationTarget> extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    @SuppressWarnings("rawtypes")
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code name} property.
     */
    private final MetaProperty<FunctionGroupName> name = DirectMetaProperty.ofImmutable(
        this, "name", DefaultFunctionGroup.class, FunctionGroupName.class);
    /**
     * The meta-property for the {@code targetType} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Class<T>> targetType = DirectMetaProperty.ofImmutable(
        this, "targetType", DefaultFunctionGroup.class, (Class) Class.class);
    /**
     * The meta-property for the {@code functionConfig} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<Measure, FunctionConfig<T>>> functionConfig = DirectMetaProperty.ofImmutable(
        this, "functionConfig", DefaultFunctionGroup.class, (Class) ImmutableMap.class);
    /**
     * The meta-property for the {@code functionArguments} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableMap<String, Object>> functionArguments = DirectMetaProperty.ofImmutable(
        this, "functionArguments", DefaultFunctionGroup.class, (Class) ImmutableMap.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "name",
        "targetType",
        "functionConfig",
        "functionArguments");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return name;
        case 486622315:  // targetType
          return targetType;
        case -1567383238:  // functionConfig
          return functionConfig;
        case -260573090:  // functionArguments
          return functionArguments;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends DefaultFunctionGroup<T>> builder() {
      return new DefaultFunctionGroup.Builder<T>();
    }

    @SuppressWarnings({"unchecked", "rawtypes" })
    @Override
    public Class<? extends DefaultFunctionGroup<T>> beanType() {
      return (Class) DefaultFunctionGroup.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code name} property.
     * @return the meta-property, not null
     */
    public MetaProperty<FunctionGroupName> name() {
      return name;
    }

    /**
     * The meta-property for the {@code targetType} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Class<T>> targetType() {
      return targetType;
    }

    /**
     * The meta-property for the {@code functionConfig} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<Measure, FunctionConfig<T>>> functionConfig() {
      return functionConfig;
    }

    /**
     * The meta-property for the {@code functionArguments} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableMap<String, Object>> functionArguments() {
      return functionArguments;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return ((DefaultFunctionGroup<?>) bean).getName();
        case 486622315:  // targetType
          return ((DefaultFunctionGroup<?>) bean).getTargetType();
        case -1567383238:  // functionConfig
          return ((DefaultFunctionGroup<?>) bean).getFunctionConfig();
        case -260573090:  // functionArguments
          return ((DefaultFunctionGroup<?>) bean).getFunctionArguments();
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
   * The bean-builder for {@code DefaultFunctionGroup}.
   * @param <T>  the type
   */
  private static final class Builder<T extends CalculationTarget> extends DirectFieldsBeanBuilder<DefaultFunctionGroup<T>> {

    private FunctionGroupName name;
    private Class<T> targetType;
    private Map<Measure, FunctionConfig<T>> functionConfig = ImmutableMap.of();
    private Map<String, Object> functionArguments = ImmutableMap.of();

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return name;
        case 486622315:  // targetType
          return targetType;
        case -1567383238:  // functionConfig
          return functionConfig;
        case -260573090:  // functionArguments
          return functionArguments;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder<T> set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          this.name = (FunctionGroupName) newValue;
          break;
        case 486622315:  // targetType
          this.targetType = (Class<T>) newValue;
          break;
        case -1567383238:  // functionConfig
          this.functionConfig = (Map<Measure, FunctionConfig<T>>) newValue;
          break;
        case -260573090:  // functionArguments
          this.functionArguments = (Map<String, Object>) newValue;
          break;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
      return this;
    }

    @Override
    public Builder<T> set(MetaProperty<?> property, Object value) {
      super.set(property, value);
      return this;
    }

    @Override
    public Builder<T> setString(String propertyName, String value) {
      setString(meta().metaProperty(propertyName), value);
      return this;
    }

    @Override
    public Builder<T> setString(MetaProperty<?> property, String value) {
      super.setString(property, value);
      return this;
    }

    @Override
    public Builder<T> setAll(Map<String, ? extends Object> propertyValueMap) {
      super.setAll(propertyValueMap);
      return this;
    }

    @Override
    public DefaultFunctionGroup<T> build() {
      return new DefaultFunctionGroup<T>(
          name,
          targetType,
          functionConfig,
          functionArguments);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("DefaultFunctionGroup.Builder{");
      buf.append("name").append('=').append(JodaBeanUtils.toString(name)).append(',').append(' ');
      buf.append("targetType").append('=').append(JodaBeanUtils.toString(targetType)).append(',').append(' ');
      buf.append("functionConfig").append('=').append(JodaBeanUtils.toString(functionConfig)).append(',').append(' ');
      buf.append("functionArguments").append('=').append(JodaBeanUtils.toString(functionArguments));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
