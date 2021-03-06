/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.calc.runner;

import java.io.Serializable;
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

import com.opengamma.strata.basics.CalculationTarget;
import com.opengamma.strata.collect.result.Result;

/**
 * The result of a single calculation performed by a {@link CalculationRunner}.
 */
@BeanDefinition
public final class CalculationResult implements ImmutableBean, Serializable {

  /** The target of the calculation, often a trade. */
  @PropertyDefinition(validate = "notNull")
  private final CalculationTarget target;

  /** The row index of the value in the results grid. */
  @PropertyDefinition
  private final int rowIndex;

  /** The column index of the value in the results grid. */
  @PropertyDefinition
  private final int columnIndex;

  /** The result of the calculation. */
  @PropertyDefinition(validate = "notNull")
  private final Result<?> result;

  /**
   * Returns a calculation result containing the target, the row and column in the results grid and the result
   * of a calculation.
   *
   * @param target  the target of the calculation, often a trade
   * @param rowIndex  the row index of the value in the results grid
   * @param columnIndex  the column index of the value in the results grid
   * @param result  the result of the calculation
   * @return a calculation result containing the target, the row and column in the results grid and the result
   *   of a calculation
   */
  public static CalculationResult of(
      CalculationTarget target,
      int rowIndex,
      int columnIndex,
      Result<?> result) {

    return new CalculationResult(target, rowIndex, columnIndex, result);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code CalculationResult}.
   * @return the meta-bean, not null
   */
  public static CalculationResult.Meta meta() {
    return CalculationResult.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(CalculationResult.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static CalculationResult.Builder builder() {
    return new CalculationResult.Builder();
  }

  private CalculationResult(
      CalculationTarget target,
      int rowIndex,
      int columnIndex,
      Result<?> result) {
    JodaBeanUtils.notNull(target, "target");
    JodaBeanUtils.notNull(result, "result");
    this.target = target;
    this.rowIndex = rowIndex;
    this.columnIndex = columnIndex;
    this.result = result;
  }

  @Override
  public CalculationResult.Meta metaBean() {
    return CalculationResult.Meta.INSTANCE;
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
   * Gets the target of the calculation, often a trade.
   * @return the value of the property, not null
   */
  public CalculationTarget getTarget() {
    return target;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the row index of the value in the results grid.
   * @return the value of the property
   */
  public int getRowIndex() {
    return rowIndex;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the column index of the value in the results grid.
   * @return the value of the property
   */
  public int getColumnIndex() {
    return columnIndex;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the result of the calculation.
   * @return the value of the property, not null
   */
  public Result<?> getResult() {
    return result;
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
      CalculationResult other = (CalculationResult) obj;
      return JodaBeanUtils.equal(target, other.target) &&
          (rowIndex == other.rowIndex) &&
          (columnIndex == other.columnIndex) &&
          JodaBeanUtils.equal(result, other.result);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(target);
    hash = hash * 31 + JodaBeanUtils.hashCode(rowIndex);
    hash = hash * 31 + JodaBeanUtils.hashCode(columnIndex);
    hash = hash * 31 + JodaBeanUtils.hashCode(result);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(160);
    buf.append("CalculationResult{");
    buf.append("target").append('=').append(target).append(',').append(' ');
    buf.append("rowIndex").append('=').append(rowIndex).append(',').append(' ');
    buf.append("columnIndex").append('=').append(columnIndex).append(',').append(' ');
    buf.append("result").append('=').append(JodaBeanUtils.toString(result));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code CalculationResult}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code target} property.
     */
    private final MetaProperty<CalculationTarget> target = DirectMetaProperty.ofImmutable(
        this, "target", CalculationResult.class, CalculationTarget.class);
    /**
     * The meta-property for the {@code rowIndex} property.
     */
    private final MetaProperty<Integer> rowIndex = DirectMetaProperty.ofImmutable(
        this, "rowIndex", CalculationResult.class, Integer.TYPE);
    /**
     * The meta-property for the {@code columnIndex} property.
     */
    private final MetaProperty<Integer> columnIndex = DirectMetaProperty.ofImmutable(
        this, "columnIndex", CalculationResult.class, Integer.TYPE);
    /**
     * The meta-property for the {@code result} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Result<?>> result = DirectMetaProperty.ofImmutable(
        this, "result", CalculationResult.class, (Class) Result.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "target",
        "rowIndex",
        "columnIndex",
        "result");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -880905839:  // target
          return target;
        case 23238424:  // rowIndex
          return rowIndex;
        case -855241956:  // columnIndex
          return columnIndex;
        case -934426595:  // result
          return result;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public CalculationResult.Builder builder() {
      return new CalculationResult.Builder();
    }

    @Override
    public Class<? extends CalculationResult> beanType() {
      return CalculationResult.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code target} property.
     * @return the meta-property, not null
     */
    public MetaProperty<CalculationTarget> target() {
      return target;
    }

    /**
     * The meta-property for the {@code rowIndex} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Integer> rowIndex() {
      return rowIndex;
    }

    /**
     * The meta-property for the {@code columnIndex} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Integer> columnIndex() {
      return columnIndex;
    }

    /**
     * The meta-property for the {@code result} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Result<?>> result() {
      return result;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -880905839:  // target
          return ((CalculationResult) bean).getTarget();
        case 23238424:  // rowIndex
          return ((CalculationResult) bean).getRowIndex();
        case -855241956:  // columnIndex
          return ((CalculationResult) bean).getColumnIndex();
        case -934426595:  // result
          return ((CalculationResult) bean).getResult();
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
   * The bean-builder for {@code CalculationResult}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<CalculationResult> {

    private CalculationTarget target;
    private int rowIndex;
    private int columnIndex;
    private Result<?> result;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(CalculationResult beanToCopy) {
      this.target = beanToCopy.getTarget();
      this.rowIndex = beanToCopy.getRowIndex();
      this.columnIndex = beanToCopy.getColumnIndex();
      this.result = beanToCopy.getResult();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -880905839:  // target
          return target;
        case 23238424:  // rowIndex
          return rowIndex;
        case -855241956:  // columnIndex
          return columnIndex;
        case -934426595:  // result
          return result;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -880905839:  // target
          this.target = (CalculationTarget) newValue;
          break;
        case 23238424:  // rowIndex
          this.rowIndex = (Integer) newValue;
          break;
        case -855241956:  // columnIndex
          this.columnIndex = (Integer) newValue;
          break;
        case -934426595:  // result
          this.result = (Result<?>) newValue;
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
    public CalculationResult build() {
      return new CalculationResult(
          target,
          rowIndex,
          columnIndex,
          result);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the target of the calculation, often a trade.
     * @param target  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder target(CalculationTarget target) {
      JodaBeanUtils.notNull(target, "target");
      this.target = target;
      return this;
    }

    /**
     * Sets the row index of the value in the results grid.
     * @param rowIndex  the new value
     * @return this, for chaining, not null
     */
    public Builder rowIndex(int rowIndex) {
      this.rowIndex = rowIndex;
      return this;
    }

    /**
     * Sets the column index of the value in the results grid.
     * @param columnIndex  the new value
     * @return this, for chaining, not null
     */
    public Builder columnIndex(int columnIndex) {
      this.columnIndex = columnIndex;
      return this;
    }

    /**
     * Sets the result of the calculation.
     * @param result  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder result(Result<?> result) {
      JodaBeanUtils.notNull(result, "result");
      this.result = result;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(160);
      buf.append("CalculationResult.Builder{");
      buf.append("target").append('=').append(JodaBeanUtils.toString(target)).append(',').append(' ');
      buf.append("rowIndex").append('=').append(JodaBeanUtils.toString(rowIndex)).append(',').append(' ');
      buf.append("columnIndex").append('=').append(JodaBeanUtils.toString(columnIndex)).append(',').append(' ');
      buf.append("result").append('=').append(JodaBeanUtils.toString(result));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
