/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.zerodep;


import org.eclipse.microprofile.reactive.streams.spi.Graph;
import org.eclipse.microprofile.reactive.streams.spi.SubscriberWithCompletionStage;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * A built graph.
 * <p>
 * This class is the main class responsible for building and running graphs.
 * <p>
 * Each stage in the graph is provided with {@link StageInlet}'s and {@link StageOutlet}'s to any inlets and outlets it
 * may have. Stages that feed into each other will be joined by a {@link StageOutletInlet}. On
 * {@link Publisher} and {@link Subscriber} ends of the graph, as
 * well as for publisher and subscriber stages, the ports are {@link SubscriberInlet} and {@link PublisherOutlet}.
 * <p>
 * So in general, a graph is a series of stages, each separated by {@link StageOutletInlet}, and started/ended by
 * {@link SubscriberInlet} and {@link PublisherOutlet} when the ends are open.
 * <p>
 * The graph itself is an executor. This executor guarantees that all operations submitted to it are run serially, on
 * a backed thread pool. All signals into the graph must be submitted to this executor. The executor also handles
 * exceptions, any exceptions caught by the executor will result in the entire graph shutting down.
 */
class BuiltGraph implements Executor {

  private final Executor mutex;
  private final Deque<Signal> signals = new ArrayDeque<>();
  private final Set<Port> ports = new LinkedHashSet<>();
  private final Set<GraphStage> stages = new LinkedHashSet<>();

  private BuiltGraph(Executor threadPool) {
    this.mutex = new MutexExecutor(threadPool);
  }

  private static GraphBuilder newBuilder(Executor mutex) {
    BuiltGraph logic = new BuiltGraph(mutex);
    return new GraphBuilder(logic);
  }

  /**
   * Build a pubisher graph.
   */
  static <T> Publisher<T> buildPublisher(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.PUBLISHER).publisher();
  }

  /**
   * Build a subscriber graph.
   */
  static <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.SUBSCRIBER).subscriber();
  }

  /**
   * Build a processor graph.
   */
  static <T, R> Processor<T, R> buildProcessor(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.PROCESSOR).processor();
  }

  /**
   * Build a closed graph.
   */
  static <T> CompletionStage<T> buildCompletion(Executor mutex, Graph graph) {
    return newBuilder(mutex).buildGraph(graph, Shape.CLOSED).completion();
  }

  /**
   * Build a sub stage inlet.
   */
  <T> SubStageInlet<T> buildSubInlet(Graph graph) {
    return new GraphBuilder(this).buildGraph(graph, Shape.INLET).inlet();
  }

  /**
   * Used to indicate the shape of the graph we're building.
   */
  enum Shape {
    PUBLISHER(false, true), SUBSCRIBER(true, false), PROCESSOR(true, true), CLOSED(false, false), INLET(false, true);

    private final boolean hasInlet;
    private final boolean hasOutlet;

    Shape(boolean hasInlet, boolean hasOutlet) {
      this.hasInlet = hasInlet;
      this.hasOutlet = hasOutlet;
    }

    boolean hasInlet() {
      return hasInlet;
    }

    boolean hasOutlet() {
      return hasOutlet;
    }
  }

  /**
   * Execute a signal on this graphs execution context.
   * <p>
   * This is the entry point for all external signals into the graph. The passed in command will be run with exclusion
   * from all other signals on this graph. Any exceptions thrown by the command will cause the graph to be terminated
   * with a failure.
   * <p>
   * Commands are also allowed to (synchronously) emit unrolled signals, by adding them to the signals queue.
   * Unrolled signals are used for breaking infinite recursion scenarios. This method will drain all unrolled signals
   * (including subsequent signals emitted by the unrolled signals themselves) after invocation of the command.
   *
   * @param command The command to execute in this graphs execution context.
   */
  @Override
  public void execute(Runnable command) {
    mutex.execute(() -> {
      try {
        // First execute the runnable
        command.run();

        // Now drain a maximum of 32 signals from the queue
        int signalsDrained = 0;
        while (!signals.isEmpty() && signalsDrained < 32) {
          signalsDrained++;
          signals.removeFirst().signal();
        }

        // If there were more than 32 unrolled signals, we resubmit
        // to the executor to allow us to receive external signals
        if (!signals.isEmpty()) {
          execute(() -> {
          });
        }

      } catch (Throwable t) {
        // shut down the stream
        streamFailure(t);
        // Clear remaining signals
        signals.clear();
      }
    });
  }

  private void streamFailure(Throwable error) {
    // todo handle better
    error.printStackTrace();
    for (Port port : ports) {
      try {
        port.onStreamFailure(error);
      } catch (Exception e) {
        // Ignore
      }
    }
    ports.clear();
  }

  /**
   * Enqueue a signal to be executed serially after the current signal processing finishes.
   */
  void enqueueSignal(Signal signal) {
    signals.add(signal);
  }

  void addPorts(Collection<Port> ports) {
    this.ports.addAll(ports);
  }

  void addStage(GraphStage stage) {
    this.stages.add(stage);
  }

  void removePorts(Collection<Port> ports) {
    this.ports.removeAll(ports);
  }

  void removeStages(Collection<GraphStage> stages) {
    this.stages.removeAll(stages);
  }

}

