/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;

import java.util.function.Function;

/**
 * Stage used in place of an empty builder.
 */
class IdentityMap implements Stage.Map {
  private IdentityMap() {
  }

  static final IdentityMap INSTANCE = new IdentityMap();

  @Override
  public Function<?, ?> getMapper() {
    return Function.identity();
  }
}
