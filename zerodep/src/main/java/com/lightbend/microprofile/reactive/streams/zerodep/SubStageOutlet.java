/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

import java.util.List;

/**
 * An inlet for connecting a sub stage.
 * <p>
 * This stage captures close signals, and removes the stages and ports from the graph so as to avoid leaking memory.
 */
final class SubStageOutlet<T> extends SubStagePort implements StageOutlet<T> {
  private final StageOutlet<T> delegate;

  SubStageOutlet(BuiltGraph builtGraph, StageOutlet<T> delegate, List<GraphStage> subStages, List<Port> subStagePorts) {
    super(builtGraph, subStages, subStagePorts);
    this.delegate = delegate;
  }

  @Override
  public void push(T element) {
    delegate.push(element);
  }

  @Override
  public void complete() {
    delegate.complete();
    shutdown();
  }

  @Override
  public void fail(Throwable error) {
    delegate.fail(error);
    shutdown();
  }

  @Override
  public boolean isAvailable() {
    return delegate.isAvailable();
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public void setListener(OutletListener listener) {
    delegate.setListener(new OutletListener() {
      @Override
      public void onPull() {
        listener.onPull();
      }

      @Override
      public void onDownstreamFinish() {
        listener.onDownstreamFinish();
        shutdown();
      }
    });
  }
}
