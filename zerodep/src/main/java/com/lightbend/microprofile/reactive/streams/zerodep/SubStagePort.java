/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

import java.util.List;

/**
 * A port that connects to a sub graph.
 * <p>
 * This port captures close signals, and removes the stages and ports from the graph so as to avoid leaking memory.
 */
abstract class SubStagePort {
  private final BuiltGraph builtGraph;
  private final List<GraphStage> subStages;
  private final List<Port> subStagePorts;

  SubStagePort(BuiltGraph builtGraph, List<GraphStage> subStages, List<Port> subStagePorts) {
    this.builtGraph = builtGraph;
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

  protected void shutdown() {
    // Do it in a signal, this ensures that if shutdown happens while something is iterating through
    // the ports, we don't get a concurrent modification exception.
    builtGraph.enqueueSignal(() -> {
      builtGraph.removeStages(subStages);
      builtGraph.removePorts(subStagePorts);
    });
  }

}
