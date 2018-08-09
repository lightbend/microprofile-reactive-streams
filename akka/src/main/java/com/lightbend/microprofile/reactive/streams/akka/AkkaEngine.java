/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.akka;

import akka.NotUsed;
import akka.japi.JavaPartialFunction;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.SourceShape;
import akka.stream.javadsl.*;
import org.eclipse.microprofile.reactive.streams.CompletionRunner;
import org.eclipse.microprofile.reactive.streams.CompletionSubscriber;
import org.eclipse.microprofile.reactive.streams.GraphAccessor;
import org.eclipse.microprofile.reactive.streams.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.spi.Graph;
import org.eclipse.microprofile.reactive.streams.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.spi.Stage;
import org.eclipse.microprofile.reactive.streams.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

/**
 * Akka implementation of the {@link ReactiveStreamsEngine}.
 */
public class AkkaEngine implements ReactiveStreamsEngine {

  final Materializer materializer;

  public AkkaEngine(Materializer materializer) {
    this.materializer = materializer;
  }

  @Override
  public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    // Optimization - if it's just a pub
    // lisher, return it directly
    Stage firstStage = graph.getStages().iterator().next();
    if (graph.getStages().size() == 1 && firstStage instanceof Stage.PublisherStage) {
      return (Publisher) ((Stage.PublisherStage) firstStage).getRsPublisher();
    }

    return materialize(this.<T>buildSource(graph)
        .toMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), Keep.right()));
  }

  /**
   * Convert a publisher builder to a source.
   */
  public <T> Source<T, NotUsed> buildSource(PublisherBuilder<T> publisher) throws UnsupportedStageException {
    return buildSource(GraphAccessor.buildGraphFor(publisher));
  }

  private <T> Source<T, NotUsed> buildSource(Graph graph) throws UnsupportedStageException {
    Source source = null;
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      if (source == null) {
        source = toSource(stage);
      }
      else {
        flow = applyStage(flow, stage);
      }
    }
    return (Source) source
        .via(flow);
  }

  @Override
  public <T, R> CompletionSubscriber<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    return (CompletionSubscriber<T, R>) materialize(Source.asSubscriber()
        .toMat(buildSink(graph), (subscriber, result) ->
            CompletionSubscriber.of(subscriber, result)));
  }

  /**
   * Convert a subscriber builder to a sink.
   */
  public <T, R> Sink<T, CompletionStage<R>> buildSink(SubscriberBuilder<T, R> subscriber) throws UnsupportedStageException {
    return buildSink(GraphAccessor.buildGraphFor(subscriber));
  }

  private <T, R> Sink<T, CompletionStage<R>> buildSink(Graph graph) throws UnsupportedStageException {
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      if (stage.hasOutlet()) {
        flow = applyStage(flow, stage);
      }
      else {
        return flow.toMat(toSink(stage), Keep.right());
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  @Override
  public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    if (!graph.getStages().isEmpty()) {
      // Optimization - if it's just a processor, return it directly
      Stage firstStage = graph.getStages().iterator().next();
      if (graph.getStages().size() == 1 && firstStage instanceof Stage.ProcessorStage) {
        return (Processor) ((Stage.ProcessorStage) firstStage).getRsProcessor();
      }
    }

    return (Processor) materialize(buildFlow(graph).toProcessor());
  }

  /**
   * Convert a processor builder to a flow.
   */
  public <T, R> Flow<T, R, NotUsed> buildFlow(ProcessorBuilder<T, R> processor) throws UnsupportedStageException {
    return buildFlow(GraphAccessor.buildGraphFor(processor));
  }

  private <T, R> Flow<T, R, NotUsed> buildFlow(Graph graph) throws UnsupportedStageException {
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      flow = applyStage(flow, stage);
    }
    return flow;
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    return materialize(buildRunnableGraph(graph));
  }

  /**
   * Convert a completion builder to a runnable graph.
   */
  public <T> RunnableGraph<CompletionStage<T>> buildCompletion(CompletionRunner<T> completion) throws UnsupportedStageException {
    return buildRunnableGraph(GraphAccessor.buildGraphFor(completion));
  }

  private <T> RunnableGraph<CompletionStage<T>> buildRunnableGraph(Graph graph) throws UnsupportedStageException {
    Source source = null;
    Flow flow = Flow.create();
    for (Stage stage : graph.getStages()) {
      if (source == null) {
        source = toSource(stage);
      }
      else if (stage.hasOutlet()) {
        flow = applyStage(flow, stage);
      }
      else {
        return source.via(flow).toMat(toSink(stage), Keep.right());
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  // For efficient mapping of stages to translators to Akka streams, we use these maps.
  private final Map<Class<? extends Stage>, Function<Stage, Source>> sourceStages = new HashMap<>();
  private final Map<Class<? extends Stage>, BiFunction<Flow, Stage, Flow>> flowStages = new HashMap<>();
  private final Map<Class<? extends Stage>, Function<Stage, Sink>> sinkStages = new HashMap<>();

  // These helper functions primarily exist to avoid casting, since the type of S is inferred by the
  // compiler using the class passed as the first parameter, the lambda passed as the second parameter
  // allows the stage to be handled as that class.
  private <S extends Stage> void addSourceStage(Class<S> stageClass, Function<S, Source> factory) {
    sourceStages.put(stageClass, (Function) factory);
  }

  private <S extends Stage> void addFlowStage(Class<S> stageClass, BiFunction<Flow, S, Flow> factory) {
    flowStages.put(stageClass, (BiFunction) factory);
  }

  private <S extends Stage> void addSinkStage(Class<S> stageClass, Function<S, Sink> factory) {
    sinkStages.put(stageClass, (Function) factory);
  }

  // Initializer
  {
    // Sources
    addSourceStage(Stage.Of.class, stage -> {
      // perhaps a premature optimization?
      if (stage.getElements() instanceof Collection) {
        int size = ((Collection) stage.getElements()).size();
        if (size == 0) {
          return Source.empty();
        }
        else if (size == 1) {
          return Source.single(stage.getElements().iterator().next());
        }
      }
      return Source.from(stage.getElements());
    });
    addSourceStage(Stage.PublisherStage.class, stage -> Source.fromPublisher(stage.getRsPublisher()));
    addSourceStage(Stage.Concat.class, stage -> buildSource(stage.getFirst())
        .concat(buildSource(stage.getSecond())));
    addSourceStage(Stage.Failed.class, stage -> Source.failed(stage.getError()));

    // Flows
    addFlowStage(Stage.Map.class, (flow, stage) -> {
      Function<Object, Object> mapper = (Function) stage.getMapper();
      return flow.map(mapper::apply);
    });
    addFlowStage(Stage.Filter.class, (flow, stage) -> {
      Predicate<Object> predicate = (Predicate) stage.getPredicate();
      return flow.filter(predicate::test);
    });
    addFlowStage(Stage.FlatMap.class, (flow, stage) -> {
      Function<Object, Graph> mapper = (Function) stage.getMapper();
      return flow.flatMapConcat(e -> buildSource(mapper.apply(e)));
    });
    addFlowStage(Stage.TakeWhile.class, (flow, stage) -> {
      Predicate<Object> predicate = (Predicate) stage.getPredicate();
      return flow.takeWhile(predicate::test);
    });
    addFlowStage(Stage.FlatMapCompletionStage.class, (flow, stage) -> {
      Function<Object, CompletionStage<Object>> mapper = (Function) stage.getMapper();
      return flow.mapAsync(1, mapper::apply);
    });
    addFlowStage(Stage.FlatMapIterable.class, (flow, stage) -> {
      Function<Object, Iterable<Object>> mapper = (Function) stage.getMapper();
      return flow.mapConcat(mapper::apply);
    });
    addFlowStage(Stage.ProcessorStage.class, (flow, stage) -> {
      Processor<Object, Object> processor = (Processor) stage.getRsProcessor();
      Flow processorFlow;
      try {
        processorFlow = Flow.fromProcessor(() -> processor);
      }
      catch (Exception e) {
        // Technically can't happen, since the lambda we passed doesn't throw anything.
        throw new RuntimeException("Unexpected exception thrown", e);
      }
      return flow.via(processorFlow);
    });
    addFlowStage(Stage.Limit.class, (flow, stage) -> flow.take(stage.getLimit()));
    addFlowStage(Stage.Distinct.class, (flow, stage) -> {
      Set<Object> seen = new HashSet<>();
      return flow.filter(t -> seen.add(t));
    });
    addFlowStage(Stage.Peek.class, (flow, stage) -> {
      Consumer<Object> consumer = (Consumer) stage.getConsumer();
      return flow.map(t -> {
        consumer.accept(t);
        return t;
      });
    });
    addFlowStage(Stage.Skip.class, (flow, stage) -> flow.drop(stage.getSkip()));
    addFlowStage(Stage.DropWhile.class, (flow, stage) -> {
      Predicate<Object> predicate = (Predicate) stage.getPredicate();
      return flow.dropWhile(predicate::test);
    });
    addFlowStage(Stage.OnComplete.class, (flow, stage) -> flow.via(TerminationPeeker.onComplete(stage.getAction())));
    addFlowStage(Stage.OnError.class, (flow, stage) -> flow.via(TerminationPeeker.onError(stage.getConsumer())));
    addFlowStage(Stage.OnTerminate.class, (flow, stage) -> flow.via(TerminationPeeker.onTerminate(stage.getAction())));
    addFlowStage(Stage.OnErrorResume.class, (flow, stage) -> {
      Function<Throwable, Object> function = (Function) stage.getFunction();
      return flow.recover(new JavaPartialFunction<Throwable, Object>() {
        @Override
        public Object apply(Throwable x, boolean isCheck) throws Exception {
          if (isCheck) return null;
          else return function.apply(x);
        }
      });
    });
    addFlowStage(Stage.OnErrorResumeWith.class, (flow, stage) -> {
      Function<Throwable, Graph> function = (Function) stage.getFunction();
      return flow.recoverWithRetries(1, new JavaPartialFunction<Throwable, akka.stream.Graph<SourceShape<Object>, NotUsed>>() {
        @Override
        public akka.stream.Graph<SourceShape<Object>, NotUsed> apply(Throwable x, boolean isCheck) throws Exception {
          if (isCheck) return null;
          else return buildSource(function.apply(x));
        }
      });
    });

    // Sinks
    addSinkStage(Stage.FindFirst.class, stage -> Sink.headOption());
    addSinkStage(Stage.Collect.class, stage -> {
          Collector collector = stage.getCollector();
          // Lazy inited sink so that exceptions thrown by supplier get propagated through completion stage, not directly.
          return Sink.lazyInitAsync(() -> {
            BiConsumer accumulator = collector.accumulator();
            Object firstContainer = collector.supplier().get();
            if (firstContainer == null) {
              firstContainer = NULL;
            }
            return CompletableFuture.completedFuture(Sink.fold(firstContainer, (resultContainer, in) -> {
              if (resultContainer == NULL) {
                accumulator.accept(null, in);
              } else {
                accumulator.accept(resultContainer, in);
              }
              return resultContainer;
            }));
          }).mapMaterializedValue(asyncMaybeResult -> asyncMaybeResult.thenCompose(maybeResult -> {
            CompletionStage<Object> resultContainer;
            if (maybeResult.isPresent()) {
              resultContainer = maybeResult.get();
            } else {
              resultContainer = CompletableFuture.completedFuture(collector.supplier().get());
            }
            return resultContainer.thenApply(container -> {
              if (collector.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH) && resultContainer != NULL) {
                return container;
              } else {
                return collector.finisher().apply(container);
              }
            });
          }));
        });
    addSinkStage(Stage.SubscriberStage.class, stage ->
        Flow.create()
            .viaMat(new TerminationWatcher(), Keep.right())
            .to((Sink) Sink.fromSubscriber(stage.getRsSubscriber())));
    addSinkStage(Stage.Cancel.class, stage -> Sink.cancelled().mapMaterializedValue(n -> CompletableFuture.completedFuture(null)));
  }

  private Source toSource(Stage stage) {
    if (stage.hasInlet() || !stage.hasOutlet()) {
      throw new IllegalArgumentException("Cannot create source stage for stage of this shape: " + stage);
    }

    Function<Stage, Source> factory = sourceStages.get(stage.getClass());

    if (factory == null) {
      throw new UnsupportedStageException(stage);
    }
    else {
      return factory.apply(stage);
    }
  }

  private Flow applyStage(Flow flow, Stage stage) {
    if (!stage.hasInlet() || !stage.hasOutlet()) {
      throw new IllegalArgumentException("Cannot create flow stage for stage of this shape: " + stage);
    }

    BiFunction<Flow, Stage, Flow> flowStage = flowStages.get(stage.getClass());

    if (flowStage == null) {
      throw new UnsupportedStageException(stage);
    }
    else {
      return flowStage.apply(flow, stage);
    }
  }

  private Sink toSink(Stage stage) {
    if (!stage.hasInlet() || stage.hasOutlet()) {
      throw new IllegalArgumentException("Cannot create sink stage for stage of this shape: " + stage);
    }

    Function<Stage, Sink> sinkStage = sinkStages.get(stage.getClass());

    if (sinkStage == null) {
      throw new UnsupportedStageException(stage);
    }
    else {
      return sinkStage.apply(stage);
    }
  }


  private <T> T materialize(RunnableGraph<T> graph) {
    return graph.addAttributes(akkaEngineAttributes).run(materializer);
  }

  /**
   * This attribute does nothing except ensures a reference to this AkkaEngine is kept by the running stream.
   * <p>
   * This is to prevent the cleaner used in the AkkaEngineProvider from finding that the AkkaEngine is unreachable
   * while a stream is still running, and shut the engine down. Once all streams stop running, the stream actor will
   * be disposed and the engine will become unreachable (as long as no user code references it), then it can be shut
   * down.
   */
  private class AkkaEngineAttribute implements Attributes.Attribute {
    /**
     * Technically not needed since this is a non static inner class and so holds this reference anyway, but this
     * makes it explicit, ensuring someone doesn't unhelpfully make this class static in future.
     */
    private final AkkaEngine akkaEngine = AkkaEngine.this;
  }

  private final Attributes akkaEngineAttributes = Attributes.apply(new AkkaEngineAttribute());

  /**
   * Place holder for null.
   */
  private static final Object NULL = new Object();
}
