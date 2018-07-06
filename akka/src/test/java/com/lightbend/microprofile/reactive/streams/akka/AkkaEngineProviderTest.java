/*******************************************************************************
 * Copyright (c) 2018 Lightbend Inc.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.lightbend.microprofile.reactive.streams.akka;

import akka.Done;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.AsPublisher;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import org.eclipse.microprofile.reactive.streams.ReactiveStreams;
import org.testng.annotations.Test;
import org.reactivestreams.Publisher;
import scala.compat.java8.FutureConverters;
import scala.concurrent.duration.Duration;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class AkkaEngineProviderTest {

  @Test
  public void akkaEngineProviderIsProvided() throws Exception {
    assertEquals(
        ReactiveStreams.of(1).toList().run()
            .toCompletableFuture().get(1, TimeUnit.SECONDS),
        Collections.singletonList(1));
  }

  @Test
  public void actorSystemIsCleanedUpWhenThereAreNoMoreReferences() throws Exception {
    // First get a reference
    AkkaEngine engine = AkkaEngineProvider.provider();
    // And get the actor system from it
    ActorSystem system = ((ActorMaterializer) engine.materializer).system();
    // Clear reference
    engine = null;
    // Wait a while in case there are streams running from other tests
    Thread.sleep(300);
    // And gc
    System.gc();
    // Now wait for the system to shutdown
    FutureConverters.toJava(system.whenTerminated()).toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

  @Test
  public void aRunningStreamShouldPreventActorSystemFromShuttingDown() throws Exception {
    AkkaEngine engine = AkkaEngineProvider.provider();
    ActorSystem system = ((ActorMaterializer) engine.materializer).system();

    Publisher<Done> publisher =
        Source.tick(
            Duration.create(100, TimeUnit.MILLISECONDS),
            Duration.create(100, TimeUnit.MILLISECONDS),
            Done.getInstance()
        )
            .runWith(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), engine.materializer);

    AtomicReference<RuntimeException> error = new AtomicReference<>();
    ReactiveStreams.fromPublisher(publisher).forEach(d -> {
      if (error.get() != null) {
        throw error.get();
      }
    }).run(engine);
    publisher = null;

    engine = null;
    Thread.sleep(300);

    // And gc
    System.gc();
    // Wait for it to possibly complete
    Thread.sleep(1000);
    // Now ensure it doesn't complete
    assertFalse(system.whenTerminated().isCompleted());

    // Stop the stream
    error.set(new RuntimeException());
    // Wait for the stream to shutdown
    Thread.sleep(1000);
    // gc again
    System.gc();
    // Now ensure it does complete
    FutureConverters.toJava(system.whenTerminated()).toCompletableFuture().get(10, TimeUnit.SECONDS);
  }

}
