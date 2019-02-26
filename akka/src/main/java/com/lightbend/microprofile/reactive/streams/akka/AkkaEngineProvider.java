/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.microprofile.reactive.streams.akka;

import akka.actor.ActorSystem;
import akka.actor.BootstrapSetup;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import com.google.common.base.FinalizablePhantomReference;
import com.google.common.base.FinalizableReferenceQueue;
import com.google.common.collect.Sets;
import com.typesafe.config.ConfigFactory;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import scala.compat.java8.FutureConverters;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;

/**
 * Provides the Akka Engine to the JDK9 modules system.
 * <p>
 * This will instantiate its own actor systems to run the streams. It uses weak references and a cleaner to ensure
 * that the actor system gets cleaned up.
 * <p>
 * While JDK9 modules provided using a module descriptor can provide static method, right now we're providing this
 * module as a classpath service, and that doesn't support static methods. So, when used as an engine, this class
 * itself is used as the engine, and delegates to the actual engine, which has a weak reference kept to it.
 */
public class AkkaEngineProvider implements ReactiveStreamsEngine {

  /**
   * This comes from Guava. If we move to JDK9 as a minimum required version, then this can be replaced with the JDK9
   * cleaner API.
   */
  private static final FinalizableReferenceQueue frq = new FinalizableReferenceQueue();

  /**
   * We need to hold references to any FinalizablePhantomReference objects that we create, to ensure that they
   * themselves don't get garbage collected. Ignore any IDE warnings that say that this set is updated but never
   * queried, the garbage collector queries it and that's all that matters.
   */
  private static final Set<Reference<?>> references = Sets.newConcurrentHashSet();

  private static volatile WeakReference<AkkaEngine> cachedEngine = null;

  /**
   * Used to ensure only one instance of the engine exists at a time.
   */
  private static final Object mutex = new Object();

  private static AkkaEngine createEngine() {
    ActorSystem system = ActorSystem.create("reactive-streams-engine",
        BootstrapSetup.create()
            // Use JDK common thread pool rather than instantiate our own.
            .withDefaultExecutionContext(FutureConverters.fromExecutorService(ForkJoinPool.commonPool()))
            // Be explicit about the classloader.
            .withClassloader(AkkaEngine.class.getClassLoader())
            // Use empty config to ensure any other actor systems using the root config don't conflict.
            // todo maybe we want to be able to configure it?
            .withConfig(ConfigFactory.empty())
    );
    Materializer materializer = ActorMaterializer.create(system);

    AkkaEngine engine = new AkkaEngine(materializer);
    references.add(new AkkaEngineFinalizablePhantomReference(engine, system));
    return engine;
  }

  /**
   * This method is used by the JDK9 modules service loader mechanism to load the engine.
   */
  public static AkkaEngine provider() {

    AkkaEngine engine = null;
    // Double checked locking to initialize the weak reference the first time this method
    // is invoked.
    if (cachedEngine == null) {
      synchronized (mutex) {
        if (cachedEngine == null) {
          // We could just use the weak reference to get the engine later, but technically,
          // it could be collected before we get there, so we strongly reference it here to
          // ensure that doesn't happen.
          engine = createEngine();
          cachedEngine = new WeakReference<>(engine);
        }
      }
    }
    if (engine == null) {
      // Double checked locking to ensure the weak reference is set.
      engine = cachedEngine.get();
      if (engine == null) {
        synchronized (mutex) {
          engine = cachedEngine.get();
          if (engine == null) {
            engine = createEngine();
            cachedEngine = new WeakReference<>(engine);
          }
        }
      }
    }

    return engine;
  }

  private final AkkaEngine delegate;

  public AkkaEngineProvider() {
    this.delegate = provider();
  }

  @Override
  public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
    return delegate.buildPublisher(graph);
  }

  @Override
  public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
    return delegate.buildSubscriber(graph);
  }

  @Override
  public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
    return delegate.buildProcessor(graph);
  }

  @Override
  public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
    return delegate.buildCompletion(graph);
  }

  /**
   * This is a static class to ensure we don't close over any state that might keep the engine alive.
   */
  private static class AkkaEngineFinalizablePhantomReference extends FinalizablePhantomReference<AkkaEngine> {
    private final ActorSystem system;

    private AkkaEngineFinalizablePhantomReference(AkkaEngine engine, ActorSystem system) {
      super(engine, AkkaEngineProvider.frq);
      this.system = system;
    }

    @Override
    public void finalizeReferent() {
      references.remove(this);
      system.terminate();
    }
  }
}
