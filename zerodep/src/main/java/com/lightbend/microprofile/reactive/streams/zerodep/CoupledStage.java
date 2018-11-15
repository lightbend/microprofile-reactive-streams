/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

class CoupledStage<T, R> extends GraphStage {

  private final SubStageOutlet<T> innerOutlet;
  private final SubStageInlet<R> innerInlet;

  CoupledStage(BuiltGraph builtGraph, StageInlet<T> inlet, SubStageOutlet<T> innerOutlet, SubStageInlet<R> innerInlet, StageOutlet<R> outlet) {
    super(builtGraph);
    this.innerOutlet = innerOutlet;
    this.innerInlet = innerInlet;

    inlet.setListener(new InletListener() {
      @Override
      public void onPush() {
        innerOutlet.push(inlet.grab());
      }

      @Override
      public void onUpstreamFinish() {
        innerOutlet.complete();
        outlet.complete();
        innerInlet.cancel();
      }

      @Override
      public void onUpstreamFailure(Throwable error) {
        innerOutlet.fail(error);
        outlet.fail(error);
        innerInlet.cancel();
      }
    });

    innerOutlet.setListener(new OutletListener() {
      @Override
      public void onPull() {
        inlet.pull();
      }

      @Override
      public void onDownstreamFinish() {
        inlet.cancel();
        innerInlet.cancel();
        outlet.complete();
      }
    });

    innerInlet.setListener(new InletListener() {
      @Override
      public void onPush() {
        outlet.push(innerInlet.grab());
      }

      @Override
      public void onUpstreamFinish() {
        outlet.complete();
        innerOutlet.complete();
        inlet.cancel();
      }

      @Override
      public void onUpstreamFailure(Throwable error) {
        outlet.fail(error);
        innerOutlet.fail(error);
        inlet.cancel();
      }
    });

    outlet.setListener(new OutletListener() {
      @Override
      public void onPull() {
        innerInlet.pull();
      }

      @Override
      public void onDownstreamFinish() {
        innerInlet.cancel();
        inlet.cancel();
        innerOutlet.complete();
      }
    });
  }

  @Override
  protected void postStart() {
    innerOutlet.start();
    innerInlet.start();
  }
}
