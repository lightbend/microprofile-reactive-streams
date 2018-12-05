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
final class SubStageInlet<T> extends SubStagePort implements StageInlet<T> {
  private final StageInlet<T> delegate;

  SubStageInlet(BuiltGraph builtGraph, StageInlet<T> delegate, List<GraphStage> subStages, List<Port> subStagePorts) {
    super(builtGraph, subStages, subStagePorts);
    this.delegate = delegate;
  }

  @Override
  public void pull() {
    delegate.pull();
  }

  @Override
  public boolean isPulled() {
    return delegate.isPulled();
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
  public void cancel() {
    delegate.cancel();
    shutdown();
  }

  @Override
  public T grab() {
    return delegate.grab();
  }

  @Override
  public void setListener(InletListener listener) {
    delegate.setListener(new InletListener() {
      @Override
      public void onPush() {
        listener.onPush();
      }

      @Override
      public void onUpstreamFinish() {
        listener.onUpstreamFinish();
        shutdown();
      }

      @Override
      public void onUpstreamFailure(Throwable error) {
        listener.onUpstreamFailure(error);
        shutdown();
      }
    });
  }
}
