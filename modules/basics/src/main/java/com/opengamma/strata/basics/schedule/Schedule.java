/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.schedule;

import static com.opengamma.strata.collect.Guavate.toImmutableList;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
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

import com.google.common.collect.ImmutableList;
import com.opengamma.strata.basics.date.DayCount.ScheduleInfo;
import com.opengamma.strata.collect.ArgChecker;

/**
 * A complete schedule of periods (date ranges), with both unadjusted and adjusted dates.
 * <p>
 * The schedule consists of one or more adjacent periods (date ranges).
 * This is typically used as the basis for financial calculations, such as accrual of interest.
 * <p>
 * It is recommended to create a {@link Schedule} using a {@link PeriodicSchedule}.
 */
@BeanDefinition
public final class Schedule
    implements ScheduleInfo, ImmutableBean, Serializable {

  /**
   * The schedule periods.
   * <p>
   * There will be at least one period.
   * The periods are ordered from earliest to latest.
   * It is intended that each period is adjacent to the next one, however each
   * period is independent and non-adjacent periods are allowed.
   */
  @PropertyDefinition(validate = "notEmpty")
  private final ImmutableList<SchedulePeriod> periods;
  /**
   * The periodic frequency used when building the schedule.
   * <p>
   * If the schedule was not built from a regular periodic frequency,
   * then the frequency should be a suitable estimate.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final Frequency frequency;
  /**
   * The roll convention used when building the schedule.
   * <p>
   * If the schedule was not built from a regular periodic frequency, then the convention should be 'None'.
   */
  @PropertyDefinition(validate = "notNull")
  private final RollConvention rollConvention;

  //-------------------------------------------------------------------------
  /**
   * Create a 'Term' schedule from a single period.
   * <p>
   * A 'Term' schedule has one period with a frequency of 'Term'.
   * 
   * @param period  the single period
   * @return the merged 'Term' schedule
   */
  public static Schedule ofTerm(SchedulePeriod period) {
    ArgChecker.notNull(period, "period");
    return Schedule.builder()
        .periods(ImmutableList.of(period))
        .frequency(Frequency.TERM)
        .rollConvention(RollConventions.NONE)
        .build();
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the number of periods in the schedule.
   * <p>
   * This returns the number of periods, which will be at least one.
   * 
   * @return the number of periods
   */
  public int size() {
    return periods.size();
  }

  /**
   * Checks if this schedule represents a single 'Term' period.
   * <p>
   * A 'Term' schedule has one period.
   * 
   * @return true if this is a 'Term' schedule
   */
  public boolean isTerm() {
    return size() == 1;
  }

  //-------------------------------------------------------------------------
  /**
   * Gets a schedule period by index.
   * <p>
   * This returns a period using a zero-based index.
   * 
   * @param index  the zero-based period index
   * @return the schedule period
   * @throws IndexOutOfBoundsException if the index is invalid
   */
  public SchedulePeriod getPeriod(int index) {
    return periods.get(index);
  }

  /**
   * Gets the first schedule period.
   * 
   * @return the first schedule period
   */
  public SchedulePeriod getFirstPeriod() {
    return periods.get(0);
  }

  /**
   * Gets the last schedule period.
   * 
   * @return the last schedule period
   */
  public SchedulePeriod getLastPeriod() {
    return periods.get(periods.size() - 1);
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the start date of the schedule.
   * <p>
   * The first date in the schedule, typically treated as inclusive.
   * If the schedule adjusts for business days, then this is the adjusted date.
   * 
   * @return the schedule start date
   */
  @Override
  public LocalDate getStartDate() {
    return getFirstPeriod().getStartDate();
  }

  /**
   * Gets the end date of the schedule.
   * <p>
   * The last date in the schedule, typically treated as exclusive.
   * If the schedule adjusts for business days, then this is the adjusted date.
   * 
   * @return the schedule end date
   */
  @Override
  public LocalDate getEndDate() {
    return getLastPeriod().getEndDate();
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the initial stub if it exists.
   * <p>
   * There is an initial stub if there is more than one period and the first
   * period is a stub.
   * 
   * @return the initial stub, empty if no initial stub
   */
  public Optional<SchedulePeriod> getInitialStub() {
    return (isInitialStub() ? Optional.of(getFirstPeriod()) : Optional.empty());
  }

  // checks if there is an initial stub
  private boolean isInitialStub() {
    return !isTerm() && !getFirstPeriod().isRegular(frequency, rollConvention);
  }

  /**
   * Gets the final stub if it exists.
   * <p>
   * There is a final stub if there is more than one period and the last
   * period is a stub.
   * 
   * @return the final stub, empty if no final stub
   */
  public Optional<SchedulePeriod> getFinalStub() {
    return (isFinalStub() ? Optional.of(getLastPeriod()) : Optional.empty());
  }

  // checks if there is a final stub
  private boolean isFinalStub() {
    return !isTerm() && !getLastPeriod().isRegular(frequency, rollConvention);
  }

  /**
   * Gets the regular schedule periods.
   * <p>
   * The regular periods exclude any initial or final stub.
   * In most cases, the periods returned will be regular, corresponding to the periodic
   * frequency and roll convention, however there are cases when this is not true.
   * See {@link SchedulePeriod#isRegular(Frequency, RollConvention)}.
   * 
   * @return the non-stub schedule periods
   */
  public ImmutableList<SchedulePeriod> getRegularPeriods() {
    if (isTerm()) {
      return periods;
    }
    int startStub = isInitialStub() ? 1 : 0;
    int endStub = isFinalStub() ? 1 : 0;
    return (startStub == 0 && endStub == 0 ? periods : periods.subList(startStub, periods.size() - endStub));
  }

  //-------------------------------------------------------------------------
  /**
   * Checks if the end of month convention is in use.
   * <p>
   * If true then when building a schedule, dates will be at the end-of-month if the
   * first date in the series is at the end-of-month.
   * 
   * @return true if the end of month convention is in use
   */
  @Override
  public boolean isEndOfMonthConvention() {
    return rollConvention == RollConventions.EOM;
  }

  /**
   * Finds the period end date given a date in the period.
   * <p>
   * The first matching period is returned.
   * The adjusted start and end dates of each period are used in the comparison.
   * The start date is included, the end date is excluded.
   * 
   * @param date  the date to find
   * @return the end date of the period that includes the specified date
   */
  @Override
  public LocalDate getPeriodEndDate(LocalDate date) {
    return periods.stream()
        .filter(p -> p.contains(date))
        .map(p -> p.getEndDate())
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Date is not contained in any period"));
  }

  //-------------------------------------------------------------------------
  /**
   * Merges this schedule to form a new schedule with a single 'Term' period.
   * <p>
   * The result will have one period of type 'Term', with dates matching this schedule.
   * 
   * @return the merged 'Term' schedule
   */
  public Schedule mergeToTerm() {
    if (isTerm()) {
      return this;
    }
    SchedulePeriod first = getFirstPeriod();
    SchedulePeriod last = getLastPeriod();
    return Schedule.ofTerm(SchedulePeriod.of(
        first.getStartDate(),
        last.getEndDate(),
        first.getUnadjustedStartDate(),
        last.getUnadjustedEndDate()));
  }

  /**
   * Merges this schedule to form a new schedule by combining the regular schedule periods.
   * <p>
   * This produces a schedule where some periods are merged together.
   * For example, this could be used to convert a 3 monthly schedule into a 6 monthly schedule.
   * <p>
   * The merging is controlled by the group size, which defines the number of periods
   * to merge together in the result. For example, to convert a 3 monthly schedule into
   * a 6 monthly schedule the group size would be 2 (6 divided by 3).
   * <p>
   * A group size of zero or less will throw an exception.
   * A group size of 1 will return this schedule.
   * A larger group size will return a schedule where each group of regular periods are merged.
   * The roll flag is used to determine the direction in which grouping occurs.
   * <p>
   * Any existing stub periods are considered to be special, and are not merged.
   * Even if the grouping results in an excess period, such as 10 periods with a group size
   * of 3, the excess period will not be merged with a stub.
   * <p>
   * If this period is a 'Term' period, this schedule is returned.
   * 
   * @param groupSize  the group size
   * @param rollForwards  whether to roll forwards (true) or backwards (false)
   * @return the merged schedule
   * @throws IllegalArgumentException if the group size is zero or less
   */
  public Schedule mergeRegular(int groupSize, boolean rollForwards) {
    ArgChecker.notNegativeOrZero(groupSize, "groupSize");
    if (isTerm() || groupSize == 1) {
      return this;
    }
    List<SchedulePeriod> newSchedule = new ArrayList<>();
    // retain initial stub
    Optional<SchedulePeriod> initialStub = getInitialStub();
    if (initialStub.isPresent()) {
      newSchedule.add(initialStub.get());
    }
    // merge regular, handling stubs via min/max
    ImmutableList<SchedulePeriod> regularPeriods = getRegularPeriods();
    int regularSize = regularPeriods.size();
    int remainder = regularSize % groupSize;
    int startIndex = (rollForwards || remainder == 0 ? 0 : -(groupSize - remainder));
    for (int i = startIndex; i < regularSize; i += groupSize) {
      int from = Math.max(i, 0);
      int to = Math.min(i + groupSize, regularSize);
      newSchedule.add(createSchedulePeriod(regularPeriods.subList(from, to)));
    }
    // retain final stub
    Optional<SchedulePeriod> finalStub = getFinalStub();
    if (finalStub.isPresent()) {
      newSchedule.add(finalStub.get());
    }
    // build schedule
    return Schedule.builder()
        .periods(newSchedule)
        .frequency(Frequency.of(frequency.getPeriod().multipliedBy(groupSize)))
        .rollConvention(rollConvention)
        .build();
  }

  // creates a schedule period
  private SchedulePeriod createSchedulePeriod(List<SchedulePeriod> accruals) {
    SchedulePeriod first = accruals.get(0);
    if (accruals.size() == 1) {
      return first;
    }
    SchedulePeriod last = accruals.get(accruals.size() - 1);
    return SchedulePeriod.of(
        first.getStartDate(),
        last.getEndDate(),
        first.getUnadjustedStartDate(),
        last.getUnadjustedEndDate());
  }

  //-------------------------------------------------------------------------
  /**
   * Converts this schedule to a schedule where every adjusted date is reset
   * to the unadjusted equivalent.
   * <p>
   * The result will have the same number of periods, but each start date and
   * end date is replaced by the matching unadjusted start or end date.
   * 
   * @return the equivalent unadjusted schedule
   */
  public Schedule toUnadjusted() {
    return toBuilder()
        .periods(periods.stream()
            .map(p -> SchedulePeriod.of(p.getUnadjustedStartDate(), p.getUnadjustedEndDate()))
            .collect(toImmutableList()))
        .build();
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code Schedule}.
   * @return the meta-bean, not null
   */
  public static Schedule.Meta meta() {
    return Schedule.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(Schedule.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  /**
   * Returns a builder used to create an instance of the bean.
   * @return the builder, not null
   */
  public static Schedule.Builder builder() {
    return new Schedule.Builder();
  }

  private Schedule(
      List<SchedulePeriod> periods,
      Frequency frequency,
      RollConvention rollConvention) {
    JodaBeanUtils.notEmpty(periods, "periods");
    JodaBeanUtils.notNull(frequency, "frequency");
    JodaBeanUtils.notNull(rollConvention, "rollConvention");
    this.periods = ImmutableList.copyOf(periods);
    this.frequency = frequency;
    this.rollConvention = rollConvention;
  }

  @Override
  public Schedule.Meta metaBean() {
    return Schedule.Meta.INSTANCE;
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
   * Gets the schedule periods.
   * <p>
   * There will be at least one period.
   * The periods are ordered from earliest to latest.
   * It is intended that each period is adjacent to the next one, however each
   * period is independent and non-adjacent periods are allowed.
   * @return the value of the property, not empty
   */
  public ImmutableList<SchedulePeriod> getPeriods() {
    return periods;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the periodic frequency used when building the schedule.
   * <p>
   * If the schedule was not built from a regular periodic frequency,
   * then the frequency should be a suitable estimate.
   * @return the value of the property, not null
   */
  @Override
  public Frequency getFrequency() {
    return frequency;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the roll convention used when building the schedule.
   * <p>
   * If the schedule was not built from a regular periodic frequency, then the convention should be 'None'.
   * @return the value of the property, not null
   */
  public RollConvention getRollConvention() {
    return rollConvention;
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
      Schedule other = (Schedule) obj;
      return JodaBeanUtils.equal(periods, other.periods) &&
          JodaBeanUtils.equal(frequency, other.frequency) &&
          JodaBeanUtils.equal(rollConvention, other.rollConvention);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash = hash * 31 + JodaBeanUtils.hashCode(periods);
    hash = hash * 31 + JodaBeanUtils.hashCode(frequency);
    hash = hash * 31 + JodaBeanUtils.hashCode(rollConvention);
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(128);
    buf.append("Schedule{");
    buf.append("periods").append('=').append(periods).append(',').append(' ');
    buf.append("frequency").append('=').append(frequency).append(',').append(' ');
    buf.append("rollConvention").append('=').append(JodaBeanUtils.toString(rollConvention));
    buf.append('}');
    return buf.toString();
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code Schedule}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code periods} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableList<SchedulePeriod>> periods = DirectMetaProperty.ofImmutable(
        this, "periods", Schedule.class, (Class) ImmutableList.class);
    /**
     * The meta-property for the {@code frequency} property.
     */
    private final MetaProperty<Frequency> frequency = DirectMetaProperty.ofImmutable(
        this, "frequency", Schedule.class, Frequency.class);
    /**
     * The meta-property for the {@code rollConvention} property.
     */
    private final MetaProperty<RollConvention> rollConvention = DirectMetaProperty.ofImmutable(
        this, "rollConvention", Schedule.class, RollConvention.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "periods",
        "frequency",
        "rollConvention");

    /**
     * Restricted constructor.
     */
    private Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -678739246:  // periods
          return periods;
        case -70023844:  // frequency
          return frequency;
        case -10223666:  // rollConvention
          return rollConvention;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public Schedule.Builder builder() {
      return new Schedule.Builder();
    }

    @Override
    public Class<? extends Schedule> beanType() {
      return Schedule.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code periods} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableList<SchedulePeriod>> periods() {
      return periods;
    }

    /**
     * The meta-property for the {@code frequency} property.
     * @return the meta-property, not null
     */
    public MetaProperty<Frequency> frequency() {
      return frequency;
    }

    /**
     * The meta-property for the {@code rollConvention} property.
     * @return the meta-property, not null
     */
    public MetaProperty<RollConvention> rollConvention() {
      return rollConvention;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -678739246:  // periods
          return ((Schedule) bean).getPeriods();
        case -70023844:  // frequency
          return ((Schedule) bean).getFrequency();
        case -10223666:  // rollConvention
          return ((Schedule) bean).getRollConvention();
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
   * The bean-builder for {@code Schedule}.
   */
  public static final class Builder extends DirectFieldsBeanBuilder<Schedule> {

    private List<SchedulePeriod> periods = ImmutableList.of();
    private Frequency frequency;
    private RollConvention rollConvention;

    /**
     * Restricted constructor.
     */
    private Builder() {
    }

    /**
     * Restricted copy constructor.
     * @param beanToCopy  the bean to copy from, not null
     */
    private Builder(Schedule beanToCopy) {
      this.periods = beanToCopy.getPeriods();
      this.frequency = beanToCopy.getFrequency();
      this.rollConvention = beanToCopy.getRollConvention();
    }

    //-----------------------------------------------------------------------
    @Override
    public Object get(String propertyName) {
      switch (propertyName.hashCode()) {
        case -678739246:  // periods
          return periods;
        case -70023844:  // frequency
          return frequency;
        case -10223666:  // rollConvention
          return rollConvention;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case -678739246:  // periods
          this.periods = (List<SchedulePeriod>) newValue;
          break;
        case -70023844:  // frequency
          this.frequency = (Frequency) newValue;
          break;
        case -10223666:  // rollConvention
          this.rollConvention = (RollConvention) newValue;
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
    public Schedule build() {
      return new Schedule(
          periods,
          frequency,
          rollConvention);
    }

    //-----------------------------------------------------------------------
    /**
     * Sets the schedule periods.
     * <p>
     * There will be at least one period.
     * The periods are ordered from earliest to latest.
     * It is intended that each period is adjacent to the next one, however each
     * period is independent and non-adjacent periods are allowed.
     * @param periods  the new value, not empty
     * @return this, for chaining, not null
     */
    public Builder periods(List<SchedulePeriod> periods) {
      JodaBeanUtils.notEmpty(periods, "periods");
      this.periods = periods;
      return this;
    }

    /**
     * Sets the {@code periods} property in the builder
     * from an array of objects.
     * @param periods  the new value, not empty
     * @return this, for chaining, not null
     */
    public Builder periods(SchedulePeriod... periods) {
      return periods(ImmutableList.copyOf(periods));
    }

    /**
     * Sets the periodic frequency used when building the schedule.
     * <p>
     * If the schedule was not built from a regular periodic frequency,
     * then the frequency should be a suitable estimate.
     * @param frequency  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder frequency(Frequency frequency) {
      JodaBeanUtils.notNull(frequency, "frequency");
      this.frequency = frequency;
      return this;
    }

    /**
     * Sets the roll convention used when building the schedule.
     * <p>
     * If the schedule was not built from a regular periodic frequency, then the convention should be 'None'.
     * @param rollConvention  the new value, not null
     * @return this, for chaining, not null
     */
    public Builder rollConvention(RollConvention rollConvention) {
      JodaBeanUtils.notNull(rollConvention, "rollConvention");
      this.rollConvention = rollConvention;
      return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("Schedule.Builder{");
      buf.append("periods").append('=').append(JodaBeanUtils.toString(periods)).append(',').append(' ');
      buf.append("frequency").append('=').append(JodaBeanUtils.toString(frequency)).append(',').append(' ');
      buf.append("rollConvention").append('=').append(JodaBeanUtils.toString(rollConvention));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
