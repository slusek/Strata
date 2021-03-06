/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.strata.product;

import com.opengamma.strata.basics.Trade;

/**
 * A financial product that can be traded.
 * <p>
 * A product is a high level abstraction applicable to many different types.
 * For example, an Interest Rate Swap is a product, as is a Forward Rate Agreement (FRA).
 * <p>
 * A product exists independently from a {@link Trade}. It represents the economics of the
 * financial instrument regardless of the trade date or counterparties.
 * <p>
 * Implementations must be immutable and thread-safe beans.
 */
public interface Product {

}
