/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

class OnCompleteStage<T> extends GraphStage implements InletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<T> outlet;
  private final Runnable action;

  OnCompleteStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<T> outlet, Runnable action) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.action = action;

    inlet.setListener(this);
    outlet.forwardTo(inlet);
  }

  @Override
  public void onPush() {
    outlet.push(inlet.grab());
  }

  @Override
  public void onUpstreamFinish() {
    try {
      action.run();
      outlet.complete();
    } catch (RuntimeException e) {
      outlet.fail(e);
    }
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
  }
}
