/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.zerodep;

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;

import java.util.function.Function;

class FlatMapStage<T, R> extends GraphStage implements InletListener, OutletListener {
  private final StageInlet<T> inlet;
  private final StageOutlet<R> outlet;
  private final Function<T, Graph> mapper;

  private SubStageInlet<R> substream;

  FlatMapStage(BuiltGraph builtGraph, StageInlet<T> inlet, StageOutlet<R> outlet, Function<T, Graph> mapper) {
    super(builtGraph);
    this.inlet = inlet;
    this.outlet = outlet;
    this.mapper = mapper;

    inlet.setListener(this);
    outlet.setListener(this);
  }

  @Override
  public void onPush() {
    Graph graph = mapper.apply(inlet.grab());
    substream = createSubInlet(graph);
    substream.setListener(new InletListener() {
      @Override
      public void onPush() {
        outlet.push(substream.grab());
      }

      @Override
      public void onUpstreamFinish() {
        substream = null;
        if (inlet.isClosed()) {
          outlet.complete();
        } else if (outlet.isAvailable()) {
          inlet.pull();
        }
      }

      @Override
      public void onUpstreamFailure(Throwable error) {
        substream = null;
        outlet.fail(error);
        if (!inlet.isClosed()) {
          inlet.cancel();
        }
      }
    });
    substream.start();
    substream.pull();
  }

  @Override
  public void onUpstreamFinish() {
    if (substream == null) {
      outlet.complete();
    }
  }

  @Override
  public void onUpstreamFailure(Throwable error) {
    outlet.fail(error);
    if (substream != null) {
      substream.cancel();
    }
  }

  @Override
  public void onPull() {
    if (substream == null) {
      inlet.pull();
    } else {
      substream.pull();
    }
  }

  @Override
  public void onDownstreamFinish() {
    if (!inlet.isClosed()) {
      inlet.cancel();
    }
    if (substream != null) {
      substream.cancel();
    }
  }
}
