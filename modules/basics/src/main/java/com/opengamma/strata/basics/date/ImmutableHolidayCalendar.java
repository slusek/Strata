/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.strata.basics.date;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.ImmutableBean;
import org.joda.beans.ImmutableConstructor;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectFieldsBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.opengamma.strata.collect.ArgChecker;
import com.opengamma.strata.collect.range.LocalDateRange;

/**
 * A holiday calendar implementation based on an immutable set of holiday dates and weekends.
 * <p>
 * A standard immutable implementation of {@link HolidayCalendar} that stores all
 * dates that are holidays, plus a list of weekend days.
 * Internally, the class uses a range to determine the valid range of dates.
 * Only dates between the start of the earliest year and the end of the latest year can be queried.
 * <p>
 * In most cases, applications should refer to calendars by name, using {@link HolidayCalendar#of(String)}.
 * The named calendar will typically be resolved to an instance of this class.
 * As such, it is recommended to use the {@code HolidayCalendar} interface in application
 * code rather than directly referring to this class.
 */
@BeanDefinition(builderScope = "private")
public final class ImmutableHolidayCalendar
    implements HolidayCalendar, ImmutableBean, Serializable {
  // optimized implementation of HolidayCalendar
  // uses an int array where each int represents a month
  // each bit within the int represents a date, where 0 is a holiday and 1 is a business day
  // (most logic involves finding business days, finding 1 is easier than finding 0
  // when using Integer.numberOfTrailingZeros and Integer.numberOfLeadingZeros)
  // benchmarking showed nextOrSame() and previousOrSame() do not need to be overridden
  // out-of-range and weekend-only (used in testing) are handled using exceptions to fast-path the common case

  /**
   * The calendar name.
   */
  @PropertyDefinition(validate = "notNull", overrideGet = true)
  private final String name;
  /**
   * The set of holiday dates.
   * <p>
   * Each date in this set is not a business day.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableSortedSet<LocalDate> holidays;
  /**
   * The set of weekend days.
   * <p>
   * Each date that has a day-of-week matching one of these days is not a business day.
   */
  @PropertyDefinition(validate = "notNull")
  private final ImmutableSet<DayOfWeek> weekendDays;
  /**
   * The start year.
   * Used as the base year for the lookup table.
   */
  private final int startYear;
  /**
   * The lookup table, where each item represents a month from January of startYear onwards.
   * Bits 0 to 31 are used for each day-of-month, where 0 is a holiday and 1 is a business day.
   * Trailing bits are set to 0 so they act as holidays, avoiding month length logic.
   */
  private final int[] lookup;
  /**
   * The supported range of dates.
   */
  private final LocalDateRange range;

  //-------------------------------------------------------------------------
  /**
   * Obtains a {@code HolidayCalendar} from a set of holiday dates and weekend days.
   * <p>
   * The holiday dates will be extracted into a set with duplicates ignored.
   * The minimum supported date for query is the start of the year of the earliest holiday.
   * The maximum supported date for query is the end of the year of the latest holiday.
   * <p>
   * The weekend days may both be the same.
   * 
   * @param name  the calendar name
   * @param holidays  the set of holiday dates
   * @param firstWeekendDay  the first weekend day
   * @param secondWeekendDay  the second weekend day, may be same as first
   * @return the holiday calendar
   */
  public static ImmutableHolidayCalendar of(
      String name, Iterable<LocalDate> holidays, DayOfWeek firstWeekendDay, DayOfWeek secondWeekendDay) {
    ArgChecker.notNull(name, "name");
    ArgChecker.noNulls(holidays, "holidays");
    ArgChecker.notNull(firstWeekendDay, "firstWeekendDay");
    ArgChecker.notNull(secondWeekendDay, "secondWeekendDay");
    ImmutableSet<DayOfWeek> weekendDays = Sets.immutableEnumSet(firstWeekendDay, secondWeekendDay);
    return new ImmutableHolidayCalendar(name, ImmutableSortedSet.copyOf(holidays), weekendDays);
  }

  /**
   * Obtains a {@code HolidayCalendar} from a set of holiday dates and weekend days.
   * <p>
   * The holiday dates will be extracted into a set with duplicates ignored.
   * The minimum supported date for query is the start of the year of the earliest holiday.
   * The maximum supported date for query is the end of the year of the latest holiday.
   * <p>
   * The weekend days may be empty, in which case the holiday dates should contain any weekends.
   * 
   * @param name  the calendar name
   * @param holidays  the set of holiday dates
   * @param weekendDays  the days that define the weekend, if empty then weekends are treated as business days
   * @return the holiday calendar
   */
  public static ImmutableHolidayCalendar of(
      String name, Iterable<LocalDate> holidays, Iterable<DayOfWeek> weekendDays) {
    ArgChecker.notNull(name, "name");
    ArgChecker.noNulls(holidays, "holidays");
    ArgChecker.noNulls(weekendDays, "weekendDays");
    return new ImmutableHolidayCalendar(name, ImmutableSortedSet.copyOf(holidays), Sets.immutableEnumSet(weekendDays));
  }

  //-------------------------------------------------------------------------
  /**
   * Creates an instance calculating the supported range.
   * 
   * @param name  the calendar name
   * @param holidays  the set of holidays, validated non-null
   * @param weekendDays  the set of weekend days, validated non-null
   */
  @ImmutableConstructor
  private ImmutableHolidayCalendar(String name, SortedSet<LocalDate> holidays, Set<DayOfWeek> weekendDays) {
    ArgChecker.notNull(name, "name");
    ArgChecker.notNull(holidays, "holidays");
    ArgChecker.notNull(weekendDays, "weekendDays");
    this.name = name;
    this.holidays = ImmutableSortedSet.copyOfSorted(holidays);
    this.weekendDays = Sets.immutableEnumSet(weekendDays);
    if (holidays.isEmpty()) {
      // special case where no holiday dates are specified
      this.range = LocalDateRange.ALL;
      this.startYear = 0;
      this.lookup = new int[0];
    } else {
      // normal case where holidays are specified
      this.range = LocalDateRange.ofClosed(
          holidays.first().with(TemporalAdjusters.firstDayOfYear()),
          holidays.last().with(TemporalAdjusters.lastDayOfYear()));
      this.startYear = range.getStart().getYear();
      int endYearExclusive = range.getEndExclusive().getYear();
      this.lookup = buildLookupArray(holidays, weekendDays, startYear, endYearExclusive);
    }
  }

  // create and populate the int[] lookup
  // use 1 for business days and 0 for holidays
  private static int[] buildLookupArray(
      SortedSet<LocalDate> holidays,
      Set<DayOfWeek> weekendDays,
      int startYear,
      int endYearExclusive) {
    // array that has one entry for each month
    int[] array = new int[(endYearExclusive - startYear) * 12];
    // loop through all months to handle end-of-month and weekends
    LocalDate firstOfMonth = LocalDate.of(startYear, 1, 1);
    for (int i = 0; i < array.length; i++) {
      int monthLen = firstOfMonth.lengthOfMonth();
      // set each valid day-of-month to be a business day
      // the bits for days beyond the end-of-month will be unset and thus treated as non-business days
      // the minus one part converts a single set bit into each lower bit being set
      array[i] = (1 << monthLen) - 1;
      // unset the bits associated with a weekend
      // can unset across whole month using repeating pattern of 7 bits
      // just need to find the offset between the weekend and the day-of-week of the 1st of the month
      for (DayOfWeek weekendDow : weekendDays) {
        int daysDiff = weekendDow.getValue() - firstOfMonth.getDayOfWeek().getValue();
        int offset = (daysDiff < 0 ? daysDiff + 7 : daysDiff);
        array[i] &= ~(0b10000001000000100000010000001 << offset);
      }
      firstOfMonth = firstOfMonth.plusMonths(1);
    }
    // unset the bit associated with each holiday date
    for (LocalDate date : holidays) {
      int index = (date.getYear() - startYear) * 12 + date.getMonthValue() - 1;
      array[index] &= ~(1 << (date.getDayOfMonth() - 1));
    }
    return array;
  }

  //-------------------------------------------------------------------------
  /**
   * Gets the range of dates that may be queried.
   * <p>
   * If a holiday query is made for a date outside this range, an exception is thrown.
   * 
   * @return the range of dates that may be queried
   */
  public LocalDateRange getRange() {
    return range;
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean isHoliday(LocalDate date) {
    ArgChecker.notNull(date, "date");
    try {
      // find data for month
      int index = (date.getYear() - startYear) * 12 + date.getMonthValue() - 1;
      // check if bit is 1 at zero-based day-of-month
      return (lookup[index] & (1 << (date.getDayOfMonth() - 1))) == 0;

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return weekendDays.contains(date.getDayOfWeek());
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  // pulled out to aid hotspot inlining
  private String rangeError(LocalDate date) {
    return "Date is not within the range of known holidays: " + date + ", " + range;
  }

  //-------------------------------------------------------------------------
  @Override
  public LocalDate shift(LocalDate date, int amount) {
    ArgChecker.notNull(date, "date");
    try {
      if (amount > 0) {
        // day-of-month: minus one for zero-based day-of-month, plus one to start from next day
        return shiftNext(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), amount);
      } else if (amount < 0) {
        // day-of-month: minus one to start from previous day
        return shiftPrev(date.getYear(), date.getMonthValue(), date.getDayOfMonth() - 1, amount);
      }
      return date;

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return HolidayCalendar.super.shift(date, amount);
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public LocalDate next(LocalDate date) {
    ArgChecker.notNull(date, "date");
    try {
      // day-of-month: minus one for zero-based day-of-month, plus one to start from next day
      return shiftNext(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), 1);

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return HolidayCalendar.super.next(date);
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  // shift to a later working day, following nextOrSame semantics
  // input day-of-month is zero-based
  private LocalDate shiftNext(int baseYear, int baseMonth, int baseDom0, int amount) {
    // find data for month
    int index = (baseYear - startYear) * 12 + baseMonth - 1;
    int monthData = lookup[index];
    // loop around amount, the number of days to shift by
    // use domOffset to keep track of day-of-month
    int domOffset = baseDom0;
    for (int amt = amount; amt > 0; amt--) {
      // shift to move the target day-of-month into bit-0, removing earlier days
      int shifted = monthData >> domOffset;
      // recurse to next month if no more business days in the month
      if (shifted == 0) {
        return baseMonth == 12 ? shiftNext(baseYear + 1, 1, 0, amt) : shiftNext(baseYear, baseMonth + 1, 0, amt);
      }
      // find least significant bit, which is next business day
      // use JDK numberOfTrailingZeros() method which is mapped to a fast intrinsic
      domOffset += (Integer.numberOfTrailingZeros(shifted) + 1);
    }
    return LocalDate.of(baseYear, baseMonth, domOffset);
  }

  //-------------------------------------------------------------------------
  @Override
  public LocalDate previous(LocalDate date) {
    ArgChecker.notNull(date, "date");
    try {
      // day-of-month: minus one to start from previous day
      return shiftPrev(date.getYear(), date.getMonthValue(), date.getDayOfMonth() - 1, -1);

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return HolidayCalendar.super.previous(date);
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  // shift to an earlier working day, following previousOrSame semantics
  // input day-of-month is one-based and may be zero or negative
  private LocalDate shiftPrev(int baseYear, int baseMonth, int baseDom, int amount) {
    // find data for month
    int index = (baseYear - startYear) * 12 + baseMonth - 1;
    int monthData = lookup[index];
    // loop around amount, the number of days to shift by
    // use domOffset to keep track of day-of-month
    int domOffset = baseDom;
    for (int amt = amount; amt < 0; amt++) {
      // shift to move the target day-of-month into bit-31, removing later days
      int shifted = (monthData << (32 - domOffset));
      // recurse to previous month if no more business days in the month
      if (shifted == 0 || domOffset <= 0) {
        return baseMonth == 1 ? shiftPrev(baseYear - 1, 12, 31, amt) : shiftPrev(baseYear, baseMonth - 1, 31, amt);
      }
      // find most significant bit, which is previous business day
      // use JDK numberOfLeadingZeros() method which is mapped to a fast intrinsic
      domOffset -= (Integer.numberOfLeadingZeros(shifted) + 1);
    }
    return LocalDate.of(baseYear, baseMonth, domOffset + 1);
  }

  //-------------------------------------------------------------------------
  @Override
  public LocalDate nextSameOrLastInMonth(LocalDate date) {
    ArgChecker.notNull(date, "date");
    try {
      // day-of-month: no alteration as method is one-based and same is valid
      return shiftNextSameLast(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return HolidayCalendar.super.nextSameOrLastInMonth(date);
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  // shift to a later working day, following nextOrSame semantics
  // falling back to the last business day-of-month to avoid crossing a month boundary
  // input day-of-month is one-based
  private LocalDate shiftNextSameLast(int baseYear, int baseMonth, int baseDom) {
    // find data for month
    int index = (baseYear - startYear) * 12 + baseMonth - 1;
    int monthData = lookup[index];
    // shift to move the target day-of-month into bit-0, removing earlier days
    int shifted = monthData >> (baseDom - 1);
    // return last business day-of-month if no more business days in the month
    if (shifted == 0) {
      // need to find the most significant bit, which is the last business day
      // use JDK numberOfLeadingZeros() method which is mapped to a fast intrinsic
      int leading = Integer.numberOfLeadingZeros(monthData);
      return LocalDate.of(baseYear, baseMonth, 32 - leading);
    }
    // find least significant bit, which is the next/same business day
    // use JDK numberOfTrailingZeros() method which is mapped to a fast intrinsic
    return LocalDate.of(baseYear, baseMonth, baseDom + Integer.numberOfTrailingZeros(shifted));
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean isLastBusinessDayOfMonth(LocalDate date) {
    ArgChecker.notNull(date, "date");
    try {
      // find data for month
      int index = (date.getYear() - startYear) * 12 + date.getMonthValue() - 1;
      // shift right, leaving the input date as bit-0 and filling with 0 on the left
      // if the result is 1, which is all zeroes and a final 1 (...0001) then it is last business day of month
      return (lookup[index] >>> (date.getDayOfMonth() - 1)) == 1;

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return HolidayCalendar.super.isLastBusinessDayOfMonth(date);
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public LocalDate lastBusinessDayOfMonth(LocalDate date) {
    ArgChecker.notNull(date, "date");
    try {
      // find data for month
      int index = (date.getYear() - startYear) * 12 + date.getMonthValue() - 1;
      // need to find the most significant bit, which is the last business day
      // use JDK numberOfLeadingZeros() method which is mapped to a fast intrinsic
      int leading = Integer.numberOfLeadingZeros(lookup[index]);
      return date.withDayOfMonth(32 - leading);

    } catch (ArrayIndexOutOfBoundsException ex) {
      if (startYear == 0) {
        return HolidayCalendar.super.lastBusinessDayOfMonth(date);
      }
      throw new IllegalArgumentException(rangeError(date));
    }
  }

  //-------------------------------------------------------------------------
  @Override
  public HolidayCalendar combineWith(HolidayCalendar other) {
    ArgChecker.notNull(other, "other");
    if (this.equals(other)) {
      return this;
    }
    if (other == HolidayCalendars.NO_HOLIDAYS) {
      return this;
    }
    if (other instanceof ImmutableHolidayCalendar) {
      ImmutableHolidayCalendar otherCal = (ImmutableHolidayCalendar) other;
      LocalDateRange newRange = range.union(otherCal.range);  // exception if no overlap
      ImmutableSortedSet<LocalDate> newHolidays =
          ImmutableSortedSet.copyOf(Iterables.concat(holidays, otherCal.holidays))
              .subSet(newRange.getStart(), newRange.getEndExclusive());
      ImmutableSet<DayOfWeek> newWeekends = ImmutableSet.copyOf(Iterables.concat(weekendDays, otherCal.weekendDays));
      String combinedName = name + "+" + otherCal.name;
      return new ImmutableHolidayCalendar(combinedName, newHolidays, newWeekends);
    }
    return HolidayCalendar.super.combineWith(other);
  }

  //-------------------------------------------------------------------------
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof ImmutableHolidayCalendar) {
      return name.equals(((ImmutableHolidayCalendar) obj).name);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  //-------------------------------------------------------------------------
  /**
   * Returns the name of the calendar.
   * 
   * @return the descriptive string
   */
  @Override
  public String toString() {
    return getName();
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code ImmutableHolidayCalendar}.
   * @return the meta-bean, not null
   */
  public static ImmutableHolidayCalendar.Meta meta() {
    return ImmutableHolidayCalendar.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(ImmutableHolidayCalendar.Meta.INSTANCE);
  }

  /**
   * The serialization version id.
   */
  private static final long serialVersionUID = 1L;

  @Override
  public ImmutableHolidayCalendar.Meta metaBean() {
    return ImmutableHolidayCalendar.Meta.INSTANCE;
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
   * Gets the calendar name.
   * @return the value of the property, not null
   */
  @Override
  public String getName() {
    return name;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the set of holiday dates.
   * <p>
   * Each date in this set is not a business day.
   * @return the value of the property, not null
   */
  public ImmutableSortedSet<LocalDate> getHolidays() {
    return holidays;
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the set of weekend days.
   * <p>
   * Each date that has a day-of-week matching one of these days is not a business day.
   * @return the value of the property, not null
   */
  public ImmutableSet<DayOfWeek> getWeekendDays() {
    return weekendDays;
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code ImmutableHolidayCalendar}.
   */
  public static final class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code name} property.
     */
    private final MetaProperty<String> name = DirectMetaProperty.ofImmutable(
        this, "name", ImmutableHolidayCalendar.class, String.class);
    /**
     * The meta-property for the {@code holidays} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableSortedSet<LocalDate>> holidays = DirectMetaProperty.ofImmutable(
        this, "holidays", ImmutableHolidayCalendar.class, (Class) ImmutableSortedSet.class);
    /**
     * The meta-property for the {@code weekendDays} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<ImmutableSet<DayOfWeek>> weekendDays = DirectMetaProperty.ofImmutable(
        this, "weekendDays", ImmutableHolidayCalendar.class, (Class) ImmutableSet.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "name",
        "holidays",
        "weekendDays");

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
        case -510663909:  // holidays
          return holidays;
        case 563236190:  // weekendDays
          return weekendDays;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends ImmutableHolidayCalendar> builder() {
      return new ImmutableHolidayCalendar.Builder();
    }

    @Override
    public Class<? extends ImmutableHolidayCalendar> beanType() {
      return ImmutableHolidayCalendar.class;
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
    public MetaProperty<String> name() {
      return name;
    }

    /**
     * The meta-property for the {@code holidays} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableSortedSet<LocalDate>> holidays() {
      return holidays;
    }

    /**
     * The meta-property for the {@code weekendDays} property.
     * @return the meta-property, not null
     */
    public MetaProperty<ImmutableSet<DayOfWeek>> weekendDays() {
      return weekendDays;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          return ((ImmutableHolidayCalendar) bean).getName();
        case -510663909:  // holidays
          return ((ImmutableHolidayCalendar) bean).getHolidays();
        case 563236190:  // weekendDays
          return ((ImmutableHolidayCalendar) bean).getWeekendDays();
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
   * The bean-builder for {@code ImmutableHolidayCalendar}.
   */
  private static final class Builder extends DirectFieldsBeanBuilder<ImmutableHolidayCalendar> {

    private String name;
    private SortedSet<LocalDate> holidays = ImmutableSortedSet.of();
    private Set<DayOfWeek> weekendDays = ImmutableSet.of();

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
        case -510663909:  // holidays
          return holidays;
        case 563236190:  // weekendDays
          return weekendDays;
        default:
          throw new NoSuchElementException("Unknown property: " + propertyName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Builder set(String propertyName, Object newValue) {
      switch (propertyName.hashCode()) {
        case 3373707:  // name
          this.name = (String) newValue;
          break;
        case -510663909:  // holidays
          this.holidays = (SortedSet<LocalDate>) newValue;
          break;
        case 563236190:  // weekendDays
          this.weekendDays = (Set<DayOfWeek>) newValue;
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
    public ImmutableHolidayCalendar build() {
      return new ImmutableHolidayCalendar(
          name,
          holidays,
          weekendDays);
    }

    //-----------------------------------------------------------------------
    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(128);
      buf.append("ImmutableHolidayCalendar.Builder{");
      buf.append("name").append('=').append(JodaBeanUtils.toString(name)).append(',').append(' ');
      buf.append("holidays").append('=').append(JodaBeanUtils.toString(holidays)).append(',').append(' ');
      buf.append("weekendDays").append('=').append(JodaBeanUtils.toString(weekendDays));
      buf.append('}');
      return buf.toString();
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}
