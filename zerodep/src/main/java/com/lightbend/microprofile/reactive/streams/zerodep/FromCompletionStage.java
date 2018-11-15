/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

import java.util.concurrent.CompletionStage;

public class FromCompletionStage<T> extends GraphStage implements OutletListener {
  private final StageOutlet<T> outlet;
  private final CompletionStage<? extends T> completionStage;
  private T element;

  public FromCompletionStage(BuiltGraph builtGraph, StageOutlet<T> outlet, CompletionStage<? extends T> completionStage) {
    super(builtGraph);
    this.outlet = outlet;
    this.completionStage = completionStage;
    outlet.setListener(this);
  }

  @Override
  protected void postStart() {
    completionStage.whenCompleteAsync((t, error) -> {
      if (!outlet.isClosed()) {
        if (error != null) {
          outlet.fail(error);
        } else if (t == null) {
          outlet.fail(new NullPointerException("Element redeemed by CompletionStage passed to fromCompletionStage must not be null"));
        } else {
          if (outlet.isAvailable()) {
            outlet.push(t);
            outlet.complete();
          } else {
            element = t;
          }
        }
      }
    }, executor());
  }

  @Override
  public void onPull() {
    if (element != null) {
      outlet.push(element);
      element = null;
      outlet.complete();
    }
  }

  @Override
  public void onDownstreamFinish() {
    element = null;
  }
}
