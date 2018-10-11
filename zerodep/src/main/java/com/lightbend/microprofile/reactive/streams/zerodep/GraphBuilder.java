/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep;

import org.eclipse.microprofile.reactive.streams.spi.Graph;
import org.eclipse.microprofile.reactive.streams.spi.Stage;
import org.eclipse.microprofile.reactive.streams.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A builder.
 * <p>
 * Builders are used both to build new graphs, as well as to add sub streams to an existing graph.
 */
class GraphBuilder {

  private static final int DEFAULT_BUFFER_HIGH_WATERMARK = 8;
  private static final int DEFAULT_BUFFER_LOW_WATERMARK = 4;

  private final BuiltGraph builtGraph;
  /**
   * The first subscriber of this graph. If this graph has a processor or subscriber shape, then by the time the
   * graph is ready to be built, this will be non null.
   */
  private Subscriber firstSubscriber;
  /**
   * Last publisher for this graph. If this graph has a processor or publisher shape, then by the time the graph is
   * ready to be built, this will be non null.
   */
  private Publisher lastPublisher;
  /**
   * The last inlet for the graph. Is this graph has an inlet shape, then by the time the graph is ready to be built,
   * this will be non null.
   */
  private StageInlet lastInlet;
  /**
   * The result for the graph. If this graph has a subscriber or closed shape, then by the time the graph is ready to
   * be built, this will be non null.
   */
  private CompletableFuture result;
  /**
   * The stages that have been added to the graph by this builder.
   */
  private List<GraphStage> builderStages = new ArrayList<>();
  /**
   * The ports that have been added to the graph by this builder.
   */
  private List<Port> builderPorts = new ArrayList<>();

  GraphBuilder(BuiltGraph builtGraph) {
    this.builtGraph = builtGraph;
  }

  /**
   * Build the graph.
   */
  GraphBuilder buildGraph(Graph graph, BuiltGraph.Shape shape) {

    // If we're building a subscriber or closed graph, instantiate the result.
    if (shape == BuiltGraph.Shape.SUBSCRIBER || shape == BuiltGraph.Shape.CLOSED) {
      result = new CompletableFuture();
    }

    Collection<Stage> graphStages = graph.getStages();
    // Special case - an empty graph. This should result in an identity processor.
    // To build this, we use a single map stage with the identity function.
    if (graphStages.isEmpty()) {
      graphStages = Collections.singleton(IdentityMap.INSTANCE);
    }

    // In the loop below, we need to compare each pair of consecutive stages, to work out what sort of inlet/outlet
    // needs to be between them. Publisher, Subscriber and Processor stages get treated specially, since they need
    // to feed in to/out of not an inlet, but a subscriber/publisher. So, we're looking for the following patterns:
    // * A publisher or processor stage to a subscriber or processor stage - no inlet/outlet is needed, these can
    //   feed directly to each other, and we connect them using a connector stage.
    // * A publisher or processor stage to an inlet stage, these get connected using a SubscriberInlet
    // * An outlet stage to a subscriber or processor stage, these get connected using a PublisherOutlet
    // * An outlet stage to an inlet stage, these get connected using a StageOutletInlet
    // Finally we need to consider the ends of the graph - if the first stage has no inlet, then no port is needed
    // there. Otherwise, we need a SubscriberInlet. And if the last stage has no outlet, then no port is needed there,
    // otherwise, we need a PublisherOutlet.
    //
    // As we iterate through the graph, we need to know what the previous stage is to be able to work out which port
    // to instantiate, and we need to keep a reference to either the previous inlet or publisher, so that we can
    // pass it to the next stage that we construct.
    Stage previousStage = null;
    StageInlet previousInlet = null;
    Publisher previousPublisher = null;

    for (Stage stage : graphStages) {

      StageOutlet currentOutlet = null;
      StageInlet currentInlet = null;
      Publisher currentPublisher = null;
      Subscriber currentSubscriber = null;

      // If this is the first stage in the graph
      if (previousStage == null) {
        if (isSubscriber(stage)) {
          // It's a subscriber, we don't create an inlet, instead we use it directly as the first subscriber
          // of this graph.
          if (stage instanceof Stage.SubscriberStage) {
            firstSubscriber = ((Stage.SubscriberStage) stage).getRsSubscriber();
          } else if (stage instanceof Stage.ProcessorStage) {
            firstSubscriber = ((Stage.ProcessorStage) stage).getRsProcessor();
          }
        } else if (shape.hasInlet()) {
          // Otherwise if it has an inlet, we need to create a subscriber inlet as the first subscriber.
          SubscriberInlet inlet = addPort(createSubscriberInlet());
          currentInlet = inlet;
          firstSubscriber = inlet;
        }
      } else {
        if (isPublisher(previousStage)) {
          if (isSubscriber(stage)) {
            // We're connecting a publisher to a subscriber, don't create any port, just record what the current
            // publisher is.
            if (stage instanceof Stage.SubscriberStage) {
              currentSubscriber = ((Stage.SubscriberStage) stage).getRsSubscriber();
            } else {
              currentSubscriber = ((Stage.ProcessorStage) stage).getRsProcessor();
            }
          } else {
            // We're connecting a publisher to an inlet, create a subscriber inlet for that.
            SubscriberInlet inlet = addPort(createSubscriberInlet());
            currentInlet = inlet;
            currentSubscriber = inlet;
          }
        } else {
          if (isSubscriber(stage)) {
            // We're connecting an outlet to a subscriber, create a publisher outlet for that.
            PublisherOutlet outlet = addPort(new PublisherOutlet(builtGraph));
            currentOutlet = outlet;
            currentPublisher = outlet;
          } else {
            // We're connecting an outlet to an inlet
            StageOutletInlet outletInlet = addPort(new StageOutletInlet(builtGraph));
            currentOutlet = outletInlet.new Outlet();
            currentInlet = outletInlet.new Inlet();
          }
        }

        // Now that we know the inlet/outlet/subscriber/publisher for the previous stage, we can instantiate it
        addStage(previousStage, previousInlet, previousPublisher, currentOutlet, currentSubscriber);
      }

      previousStage = stage;
      previousInlet = currentInlet;
      previousPublisher = currentPublisher;
    }

    // Now we need to handle the last stage
    if (previousStage != null) {
      if (isPublisher(previousStage)) {
        if (shape == BuiltGraph.Shape.INLET) {
          // Last stage is a publisher, and we need to produce a sub stream inlet
          SubscriberInlet subscriberInlet = addPort(createSubscriberInlet());
          lastInlet = subscriberInlet;
          addStage(previousStage, null, null, null, subscriberInlet);
        } else {
          // Last stage is a publisher, and we need a publisher, no need to handle it, we just set it to be
          // the last publisher.
          if (previousStage instanceof Stage.PublisherStage) {
            lastPublisher = ((Stage.PublisherStage) previousStage).getRsPublisher();
          } else {
            lastPublisher = ((Stage.ProcessorStage) previousStage).getRsProcessor();
          }
        }
      } else if (shape.hasOutlet()) {
        StageOutlet outlet;
        if (shape == BuiltGraph.Shape.INLET) {
          // We need to produce an inlet, and the last stage has an outlet, so create an outlet inlet for that
          StageOutletInlet outletInlet = addPort(new StageOutletInlet(builtGraph));
          lastInlet = outletInlet.new Inlet();
          outlet = outletInlet.new Outlet();
        } else {
          // Otherwise we must be producing a publisher, to create a publisher outlet for that.
          PublisherOutlet publisherOutlet = addPort(new PublisherOutlet(builtGraph));
          outlet = publisherOutlet;
          lastPublisher = publisherOutlet;
        }
        // And add the stage
        addStage(previousStage, previousInlet, previousPublisher, outlet, null);
      } else {
        // There's no outlet or publisher, just wire it to the previous stage
        addStage(previousStage, previousInlet, previousPublisher, null, null);
      }
    }

    return this;
  }

  /**
   * Verify that the ports in this builder are ready to start receiving signals - that is, that they all have their
   * listeners set.
   */
  private void verifyReady() {
    // Verify that the ports have listeners etc
    for (Port port : builderPorts) {
      port.verifyReady();
    }
    builtGraph.addPorts(builderPorts);
  }

  /**
   * Start the stages on this listener
   */
  private void startGraph() {
    builtGraph.execute(() -> {
      for (GraphStage stage : builderStages) {
        builtGraph.addStage(stage);
        stage.postStart();
      }
    });
  }


  <T> SubStageInlet<T> inlet() {
    Objects.requireNonNull(lastInlet, "Not an inlet graph");
    assert result == null;
    assert firstSubscriber == null;
    assert lastPublisher == null;

    return new SubStageInlet(builtGraph, lastInlet, builderStages, builderPorts);
  }

  Publisher publisher() {
    Objects.requireNonNull(lastPublisher, "Not a publisher graph");
    assert result == null;
    assert firstSubscriber == null;
    assert lastInlet == null;

    verifyReady();
    startGraph();

    return lastPublisher;
  }

  SubscriberWithCompletionStage subscriber() {
    Objects.requireNonNull(firstSubscriber, "Not a subscriber graph");
    Objects.requireNonNull(result, "Not a subscriber graph");
    assert lastPublisher == null;
    assert lastInlet == null;

    verifyReady();
    startGraph();

    return new SubscriberWithCompletionStageImpl(firstSubscriber, result);
  }

  CompletionStage completion() {
    Objects.requireNonNull(result, "Not a completion graph");
    assert lastPublisher == null;
    assert firstSubscriber == null;
    assert lastInlet == null;

    verifyReady();
    startGraph();

    return result;
  }

  Processor processor() {
    Objects.requireNonNull(lastPublisher, "Not a processor graph");
    Objects.requireNonNull(firstSubscriber, "Not a processor graph");
    assert result == null;

    verifyReady();
    startGraph();

    return new WrappedProcessor(firstSubscriber, lastPublisher);
  }

  /**
   * Add a stage.
   * <p>
   * The stage will be inspected to see what type of stage it is, and it will be created using the passed in inlet,
   * publisher, outlet or subscriber, according to what it needs.
   * <p>
   * It is up to the caller of this method to ensure that the right combination of inlet/publisher/outlet/subscriber
   * are not null for the stage it's creating.
   */
  private void addStage(Stage stage, StageInlet inlet, Publisher publisher, StageOutlet outlet,
      Subscriber subscriber) {

    StageApplier stageApplier =lookupStage(stage.getClass());
    if (stageApplier == null) {
      throw new UnsupportedStageException(stage);
    } else {
      stageApplier.apply(this, stage, inlet, publisher, outlet, subscriber);
    }
  }

  private static StageApplier lookupStage(Class<?> clazz) {
    // Breadth first search on implemented interfaces, for the core implementation, in all cases, the first interface
    // encountered will be the one we're looking for, and so this should return without having to recurse or do any
    // more than one lookup on the map, only if a different implementation of the API is used will this ever be different.
    for (Class<?> inter : clazz.getInterfaces()) {
      StageApplier applier = STAGE_APPLIERS.get(inter);
      if (applier != null) {
        return applier;
      }
    }

    if (clazz.getSuperclass() != null) {
      StageApplier applier = lookupStage(clazz.getSuperclass());
      if (applier != null) {
        return applier;
      }
    }

    for (Class<?> inter : clazz.getInterfaces()) {
      StageApplier applier = lookupStage(inter);
      if (applier != null) {
        return applier;
      }
    }

    return null;

  }

  private SubscriberInlet createSubscriberInlet() {
    return new SubscriberInlet(builtGraph, DEFAULT_BUFFER_HIGH_WATERMARK, DEFAULT_BUFFER_LOW_WATERMARK);
  }

  private <T extends Port> T addPort(T port) {
    builderPorts.add(port);
    return port;
  }

  private void addStage(GraphStage stage) {
    builderStages.add(stage);
  }

  private boolean isSubscriber(Stage stage) {
    return stage instanceof Stage.SubscriberStage || stage instanceof Stage.ProcessorStage;
  }

  private boolean isPublisher(Stage stage) {
    return stage instanceof Stage.PublisherStage || stage instanceof Stage.ProcessorStage;
  }

  @FunctionalInterface
  private interface StageApplier<S extends Stage> {
    void apply(GraphBuilder builder, S stage, StageInlet inlet, Publisher publisher, StageOutlet outlet, Subscriber subscriber);
  }

  private static final Map<Class<? extends Stage>, StageApplier<?>> STAGE_APPLIERS;

  /**
   * Convenience class for building stages.
   */
  private static class StageApplierBuilder {
    private final Map<Class<? extends Stage>, StageApplier<?>> stages = new HashMap<>();

    private <S extends Stage> void add(Class<S> stageClass, StageApplier<S> stageApplier) {
      stages.put(stageClass, stageApplier);
    }
  }

  // Build the map of stage appliers
  static {

    StageApplierBuilder stages = new StageApplierBuilder();

    // publishers
    stages.add(Stage.Of.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new OfStage(builder.builtGraph, outlet, stage.getElements())));
    stages.add(Stage.Concat.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new ConcatStage(builder.builtGraph, builder.builtGraph.buildSubInlet(stage.getFirst()),
            builder.builtGraph.buildSubInlet(stage.getSecond()), outlet)));
    stages.add(Stage.PublisherStage.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new ConnectorStage(builder.builtGraph, stage.getRsPublisher(), subscriber)));
    stages.add(Stage.Failed.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FailedStage(builder.builtGraph, outlet, stage.getError())));
    stages.add(Stage.FromCompletionStage.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FromCompletionStage(builder.builtGraph, outlet, stage.getCompletionStage())));
    stages.add(Stage.FromCompletionStageNullable.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FromCompletionStageNullableStage(builder.builtGraph, outlet, stage.getCompletionStage())));

    // processors
    stages.add(Stage.Map.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new MapStage(builder.builtGraph, inlet, outlet, stage.getMapper())));
    stages.add(Stage.Filter.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FilterStage(builder.builtGraph, inlet, outlet, stage.getPredicate())));
    stages.add(Stage.TakeWhile.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new TakeWhileStage(builder.builtGraph, inlet, outlet, stage.getPredicate())));
    stages.add(Stage.FlatMap.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FlatMapStage(builder.builtGraph, inlet, outlet, stage.getMapper())));
    stages.add(Stage.FlatMapCompletionStage.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FlatMapCompletionStage(builder.builtGraph, inlet, outlet, stage.getMapper())));
    stages.add(Stage.FlatMapIterable.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FlatMapIterableStage(builder.builtGraph, inlet, outlet, stage.getMapper())));
    stages.add(Stage.ProcessorStage.class, (builder, stage, inlet, publisher, outlet, subscriber) -> {
      Processor processor = ((Stage.ProcessorStage) stage).getRsProcessor();
      builder.addStage(new ConnectorStage(builder.builtGraph, publisher, processor));
      builder.addStage(new ConnectorStage(builder.builtGraph, processor, subscriber));
    });
    stages.add(Stage.Distinct.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new DistinctStage(builder.builtGraph, inlet, outlet)));
    stages.add(Stage.Limit.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new LimitStage(builder.builtGraph, inlet, outlet, stage.getLimit())));
    stages.add(Stage.Skip.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new SkipStage(builder.builtGraph, inlet, outlet, stage.getSkip())));
    stages.add(Stage.DropWhile.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new DropWhileStage(builder.builtGraph, inlet, outlet, stage.getPredicate())));
    stages.add(Stage.Peek.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new PeekStage(builder.builtGraph, inlet, outlet, stage.getConsumer())));
    stages.add(Stage.OnComplete.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new OnCompleteStage(builder.builtGraph, inlet, outlet, stage.getAction())));
    stages.add(Stage.OnError.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new OnErrorStage(builder.builtGraph, inlet, outlet, stage.getConsumer())));
    stages.add(Stage.OnTerminate.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new OnTerminateStage(builder.builtGraph, inlet, outlet, stage.getAction())));
    stages.add(Stage.OnErrorResume.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new OnErrorResumeStage(builder.builtGraph, inlet, outlet, stage.getFunction())));
    stages.add(Stage.OnErrorResumeWith.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new OnErrorResumeWithStage(builder.builtGraph, inlet, outlet, stage.getFunction())));

    // subscribers
    stages.add(Stage.Collect.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new CollectStage(builder.builtGraph, inlet, builder.result, stage.getCollector())));
    stages.add(Stage.FindFirst.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new FindFirstStage(builder.builtGraph, inlet, builder.result)));
    stages.add(Stage.Cancel.class, (builder, stage, inlet, publisher, outlet, subscriber) ->
        builder.addStage(new CancelStage(builder.builtGraph, inlet, builder.result)));
    stages.add(Stage.SubscriberStage.class, (builder, stage, inlet, publisher, outlet, subscriber) -> {
      // We need to capture termination, to do that we insert a CaptureTerminationStage between this and the
      // previous stage.
      if (inlet == null) {
        SubscriberInlet subscriberInlet = builder.addPort(builder.createSubscriberInlet());
        if (publisher != null) {
          builder.addStage(new ConnectorStage(builder.builtGraph, publisher, subscriberInlet));
        }
        inlet = subscriberInlet;
      }
      PublisherOutlet publisherOutlet = builder.addPort(new PublisherOutlet(builder.builtGraph));
      builder.addStage(new CaptureTerminationStage(builder.builtGraph, inlet, publisherOutlet, builder.result));
      builder.addStage(new ConnectorStage(builder.builtGraph, publisherOutlet, ((Stage.SubscriberStage) stage).getRsSubscriber()));
    });

    STAGE_APPLIERS = Collections.unmodifiableMap(stages.stages);
  }

}
