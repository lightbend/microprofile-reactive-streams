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
    // Temporarily disable all IdentityProcessorVerifications until
    // https://github.com/akka/akka/pull/25311 is fixed.
    return !(test instanceof IdentityProcessorVerification);
  }
}
