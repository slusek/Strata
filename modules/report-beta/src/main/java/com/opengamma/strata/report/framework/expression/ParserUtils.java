/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 * <p>
 * Please see distribution for license.
 */
package com.opengamma.strata.report.framework.expression;

import java.util.List;

/**
 *
 */
class ParserUtils {

  private ParserUtils() {
  }

  static List<String> tail(List<String> list) {
    return drop(list, 1);
  }

  static List<String> drop(List<String> list, int nItems) {
    return list.subList(nItems, list.size());
  }
}
