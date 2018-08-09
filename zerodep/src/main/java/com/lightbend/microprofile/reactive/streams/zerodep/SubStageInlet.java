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
final class SubStageInlet<T> implements StageInlet<T> {
  private final BuiltGraph builtGraph;
  private final StageInlet<T> delegate;
  private final List<GraphStage> subStages;
  private final List<Port> subStagePorts;

  SubStageInlet(BuiltGraph builtGraph, StageInlet<T> delegate, List<GraphStage> subStages, List<Port> subStagePorts) {
    this.builtGraph = builtGraph;
    this.delegate = delegate;
    this.subStages = subStages;
    this.subStagePorts = subStagePorts;
  }

  void start() {
    subStagePorts.forEach(Port::verifyReady);
    builtGraph.addPorts(subStagePorts);
    for (GraphStage stage: subStages) {
      builtGraph.addStage(stage);
      stage.postStart();
    }
  }

  private void shutdown() {
    // Do it in a signal, this ensures that if shutdown happens while something is iterating through
    // the ports, we don't get a concurrent modification exception.
    builtGraph.enqueueSignal(() -> {
      builtGraph.removeStages(subStages);
      builtGraph.removePorts(subStagePorts);
    });
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
