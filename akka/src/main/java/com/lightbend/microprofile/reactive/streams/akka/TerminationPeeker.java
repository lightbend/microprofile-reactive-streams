/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.akka;

import akka.stream.Attributes;
import akka.stream.FlowShape;
import akka.stream.Inlet;
import akka.stream.Outlet;
import akka.stream.stage.AbstractInHandler;
import akka.stream.stage.AbstractOutHandler;
import akka.stream.stage.GraphStage;
import akka.stream.stage.GraphStageLogic;

import java.util.function.Consumer;

/**
 * Differs from Flow.watchTermination in that termination callbacks are run in the stream, so if the callback experiences an error,
 * the stream fails.
 */
class TerminationPeeker<T> extends GraphStage<FlowShape<T, T>> {
  private final Inlet<T> in = Inlet.create("TerminationPeeker.in");
  private final Outlet<T> out = Outlet.create("TerminationPeeker.out");

  private final FlowShape<T, T> shape = FlowShape.of(in, out);

  private final Runnable onCompleteCallback;
  private final Consumer<Throwable> onErrorCallback;

  private TerminationPeeker(Runnable onCompleteCallback, Consumer<Throwable> onErrorCallback) {
    this.onCompleteCallback = onCompleteCallback;
    this.onErrorCallback = onErrorCallback;
  }

  static <T> GraphStage<FlowShape<T, T>> onComplete(Runnable action) {
    return new TerminationPeeker<>(action, t -> {});
  }

  static <T> GraphStage<FlowShape<T, T>> onTerminate(Runnable action) {
    return new TerminationPeeker<>(action, t -> action.run());
  }

  static <T> GraphStage<FlowShape<T, T>> onError(Consumer<Throwable> consumer) {
    return new TerminationPeeker<>(() -> {}, consumer);
  }

  @Override
  public FlowShape<T, T> shape() {
    return shape;
  }

  @Override
  public GraphStageLogic createLogic(Attributes inheritedAttributes) {
    return new GraphStageLogic(shape()) {
      {
        setHandler(in, new AbstractInHandler() {
          @Override
          public void onPush() throws Exception {
            push(out, grab(in));
          }

          @Override
          public void onUpstreamFinish() throws Exception {
            onCompleteCallback.run();
            complete(out);
          }

          @Override
          public void onUpstreamFailure(Throwable ex) throws Exception {
            onErrorCallback.accept(ex);
            fail(out, ex);
          }
        });
        setHandler(out, new AbstractOutHandler() {
          @Override
          public void onPull() throws Exception {
            pull(in);
          }

          @Override
          public void onDownstreamFinish() throws Exception {
            onCompleteCallback.run();
            cancel(in);
          }
        });
      }

    };
  }
}