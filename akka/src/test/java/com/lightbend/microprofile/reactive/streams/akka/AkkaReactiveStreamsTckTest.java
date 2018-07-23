/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.akka;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import org.eclipse.microprofile.reactive.streams.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.tck.CancelStageVerification;
import org.eclipse.microprofile.reactive.streams.tck.FlatMapStageVerification;
import org.eclipse.microprofile.reactive.streams.tck.ReactiveStreamsTck;
import org.reactivestreams.tck.IdentityProcessorVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterSuite;

/**
 * TCK verification for the {@link AkkaEngine} implementation of the {@link ReactiveStreamsEngine}.
 */
public class AkkaReactiveStreamsTckTest extends ReactiveStreamsTck<AkkaEngine> {

  public AkkaReactiveStreamsTckTest() {
    super(new TestEnvironment());
  }

  private ActorSystem system;
  private Materializer materializer;

  @AfterSuite
  public void shutdownActorSystem() {
    if (system != null) {
      system.terminate();
    }
  }

  @Override
  protected AkkaEngine createEngine() {
    system = ActorSystem.create();
    materializer = ActorMaterializer.create(system);
    return new AkkaEngine(materializer);
  }

  @Override
  protected boolean isEnabled(Object test) {
    return true;
  }
}
