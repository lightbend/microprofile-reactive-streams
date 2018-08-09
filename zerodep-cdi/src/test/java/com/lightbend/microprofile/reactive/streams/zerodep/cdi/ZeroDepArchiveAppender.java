/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep.cdi;

import com.beust.jcommander.JCommander;
import com.lightbend.microprofile.reactive.streams.zerodep.ReactiveStreamsEngineImpl;
import org.eclipse.microprofile.reactive.streams.ReactiveStreams;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.reactivestreams.Publisher;

/**
 * Adds the Reactive Streams operators API as well as the zerodep implementation to Thorntail.
 */
public class ZeroDepArchiveAppender implements AuxiliaryArchiveAppender {
  @Override
  public Archive<?> createAuxiliaryArchive() {
    return ShrinkWrap.create(JavaArchive.class)
        .addPackages(true, ReactiveStreams.class.getPackage())
        .addPackages(true, Publisher.class.getPackage())
        // For some reason, Thorntail automatically creates a bean for this, and I don't know why.
        // When I tried to add another application scoped bean that @Produces this, it failed with
        // an ambiguous dependency because both that, and the one that Thorntail automatically
        // created, existed. I have no idea how that's happening.
        .addPackages(true, ReactiveStreamsEngineImpl.class.getPackage())
        // Because TestNG depends on it but this old verison of Arquillian doesn't bring it in.
        .addPackages(true, JCommander.class.getPackage());
  }
}
