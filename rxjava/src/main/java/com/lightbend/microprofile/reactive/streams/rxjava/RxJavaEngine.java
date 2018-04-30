/******************************************************************************
 * Licensed under Public Domain (CC0)                                         *
 *                                                                            *
 * To the extent possible under law, the person who associated CC0 with       *
 * this code has waived all copyright and related or neighboring              *
 * rights to this code.                                                       *
 *                                                                            *
 * You should have received a copy of the CC0 legalcode along with this       *
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.     *
 ******************************************************************************/

package com.lightbend.microprofile.reactive.streams.rxjava;

import hu.akarnokd.rxjava2.interop.FlowableInterop;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import io.reactivex.Flowable;
import org.eclipse.microprofile.reactive.streams.SubscriberWithResult;
import org.eclipse.microprofile.reactive.streams.spi.Graph;
import org.eclipse.microprofile.reactive.streams.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.spi.Stage;
import org.eclipse.microprofile.reactive.streams.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

public class RxJavaEngine implements ReactiveStreamsEngine {

  @Override
  public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    return this.buildFlowable(graph);
  }

  private <T> Flowable<T> buildFlowable(Graph graph) throws UnsupportedStageException {
    Flowable flowable = null;
    for (Stage stage : graph.getStages()) {
      if (flowable == null) {
        flowable = toFlowable(stage);
      } else {
        flowable = applyStage(flowable, stage);
      }
    }
    return flowable;
  }

  @Override
  public <T, R> SubscriberWithResult<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    Processor processor = new BridgedProcessor();

    Flowable flowable = Flowable.fromPublisher(processor);
    for (Stage stage : graph.getStages()) {
      if (stage.hasOutlet()) {
        flowable = applyStage(flowable, stage);
      } else {
        CompletionStage result = applySubscriber(flowable, stage);

        return new SubscriberWithResult(processor, result);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  @Override
  public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    Processor processor = new BridgedProcessor();

    Flowable flowable = Flowable.fromPublisher(processor);
    for (Stage stage : graph.getStages()) {
      flowable = applyStage(flowable, stage);
    }

    return new WrappedProcessor<>(processor, flowable);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    Flowable flowable = null;
    for (Stage stage : graph.getStages()) {
      if (flowable == null) {
        flowable = toFlowable(stage);
      } else if (stage.hasOutlet()) {
        flowable = applyStage(flowable, stage);
      } else {
        return applySubscriber(flowable, stage);
      }
    }

    throw new IllegalStateException("Graph did not have terminal stage");
  }

  private Flowable applyStage(Flowable flowable, Stage stage) {
    if (stage instanceof Stage.Map) {
      Function<Object, Object> mapper = (Function) ((Stage.Map) stage).getMapper();
      return flowable.map(mapper::apply);
    } else if (stage instanceof Stage.Filter) {
      Predicate<Object> predicate = (Predicate) (((Stage.Filter) stage).getPredicate()).get();
      return flowable.filter(predicate::test);
    } else if (stage instanceof Stage.TakeWhile) {
      Predicate<Object> predicate = (Predicate) (((Stage.TakeWhile) stage).getPredicate()).get();
      boolean inclusive = ((Stage.TakeWhile) stage).isInclusive();
      if (inclusive) {
        return flowable.takeUntil(element -> !predicate.test(element));
      } else {
        return flowable.takeWhile(predicate::test);
      }
    } else if (stage instanceof Stage.FlatMap) {
      Function<Object, Graph> mapper = (Function) ((Stage.FlatMap) stage).getMapper();
      return flowable.concatMap(e -> buildFlowable(mapper.apply(e)));
    } else if (stage instanceof Stage.FlatMapCompletionStage) {
      Function<Object, CompletionStage<Object>> mapper = (Function) ((Stage.FlatMapCompletionStage) stage).getMapper();
      return flowable.concatMap(e -> SingleInterop.fromFuture(mapper.apply(e)).toFlowable(), 1);
    } else if (stage instanceof Stage.FlatMapIterable) {
      Function<Object, Iterable<Object>> mapper = (Function) ((Stage.FlatMapIterable) stage).getMapper();
      return flowable.concatMapIterable(mapper::apply);
    } else if (stage instanceof Stage.ProcessorStage) {
      Processor<Object, Object> processor = (Processor) (((Stage.ProcessorStage) stage).getProcessor());
      flowable.subscribe(processor);
      return Flowable.fromPublisher(processor);
    } else if (stage.hasInlet() && stage.hasOutlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an inlet and an outlet.");
    }
  }

  private CompletionStage applySubscriber(Flowable flowable, Stage stage) {
    if (stage == Stage.FindFirst.INSTANCE) {
      try {
        return SingleInterop.get().apply(flowable.map(Optional::of).first(Optional.empty()));
      } catch (Exception e) {
        // Shouldn't happen
        throw new RuntimeException("Unexpected error", e);
      }
    } else if (stage instanceof Stage.Collect) {
      Collector collector = ((Stage.Collect) stage).getCollector();
      try {
        return FlowableInterop.first().apply(flowable.compose(FlowableInterop.collect(collector)));
      } catch (Exception e) {
        // Shouldn't happen
        throw new RuntimeException("Unexpected error", e);
      }
    } else if (stage == Stage.Cancel.INSTANCE) {
      flowable.subscribe().dispose();
      return CompletableFuture.completedFuture(null);
    } if (stage instanceof Stage.SubscriberStage) {
      Subscriber subscriber = ((Stage.SubscriberStage) stage).getSubscriber();
      TerminationWatchingSubscriber watchTermination = new TerminationWatchingSubscriber(subscriber);
      flowable.subscribe(watchTermination);
      return watchTermination.getTermination();
    } else if (stage.hasInlet() && !stage.hasOutlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an inlet and no outlet.");
    }
  }

  private Flowable toFlowable(Stage stage) {
    if (stage instanceof Stage.Of) {
      return Flowable.fromIterable(((Stage.Of) stage).getElements());
    } else if (stage instanceof Stage.PublisherStage) {
      return Flowable.fromPublisher(((Stage.PublisherStage) stage).getPublisher());
    } else if (stage instanceof Stage.Concat) {
      Graph first = ((Stage.Concat) stage).getFirst();
      Graph second = ((Stage.Concat) stage).getSecond();
      CancelInjectingPublisher secondPublisher = new CancelInjectingPublisher(buildFlowable(second));
      return Flowable.concat(buildFlowable(first), secondPublisher)
          .doOnTerminate(secondPublisher::cancelIfNotSubscribed)
          .doOnCancel(secondPublisher::cancelIfNotSubscribed);
    } else if (stage instanceof Stage.Failed) {
      return Flowable.error(((Stage.Failed) stage).getError());
    } else if (stage.hasOutlet() && !stage.hasInlet()) {
      throw new UnsupportedStageException(stage);
    } else {
      throw new IllegalStateException("Got " + stage + " but needed a stage with an outlet and no inlet.");
    }
  }

  private static final Object UNIT = new Object();
}
