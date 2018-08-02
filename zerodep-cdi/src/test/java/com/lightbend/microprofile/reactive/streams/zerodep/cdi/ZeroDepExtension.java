/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep.cdi;

import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.spi.LoadableExtension;

/**
 * Arquillian extension to register the archive appender.
 */
public class ZeroDepExtension implements LoadableExtension {
  @Override
  public void register(ExtensionBuilder extensionBuilder) {
    extensionBuilder.service(AuxiliaryArchiveAppender.class, ZeroDepArchiveAppender.class);
  }
}
