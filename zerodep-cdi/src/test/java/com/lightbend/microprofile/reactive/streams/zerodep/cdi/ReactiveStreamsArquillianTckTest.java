/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.microprofile.reactive.streams.zerodep.cdi;

import org.eclipse.microprofile.reactive.streams.tck.arquillian.ReactiveStreamsArquillianTck;

/**
 * This class exists purely to bring in the TCK so that it gets automatically picked up by surefire, and can be run by
 * IntelliJ.
 */
public class ReactiveStreamsArquillianTckTest extends ReactiveStreamsArquillianTck {
}
