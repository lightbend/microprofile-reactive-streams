/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.zerodep;

import org.eclipse.microprofile.reactive.streams.spi.Graph;

import java.util.concurrent.Executor;

/**
 * Superclass of all graph stages.
 */
abstract class GraphStage {

  private final BuiltGraph builtGraph;

  GraphStage(BuiltGraph builtGraph) {
    this.builtGraph = builtGraph;
  }

  /**
   * Create a sub inlet for the given graph.
   * <p>
   * After being created, the inlet should have an inlet listener attached to it, and then it should be started.
   *
   * @param graph The graph.
   * @return The inlet.
   */
  protected <T> BuiltGraph.SubStageInlet<T> createSubInlet(Graph graph) {
    return builtGraph.buildSubInlet(graph);
  }

  protected Executor executor() {
    return builtGraph;
  }

  /**
   * Run a callback after the graph has started.
   * <p>
   * When implementing this, it's important to remember that this is executed *after* the graph has started. It's
   * possible that the stage will receive other signals before this is executed, which may have been triggered from
   * the postStart methods on other stages. So this should not be used to do initialisation that should be done
   * before the stage is ready to receive signals, that initialisation should be done in the constructor, rather,
   * this can be used to initiate signals, but care needs to be taken, for example, a stage that just completes
   * immediately should check whether the outlet is completed first, since it may have been by a previous callback.
   */
  protected void postStart() {
    // Do nothing by default
  }

}
