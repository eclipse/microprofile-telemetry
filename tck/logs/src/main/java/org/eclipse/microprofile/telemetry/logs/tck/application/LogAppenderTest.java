/*
 * Copyright (c) 2016-2024 Contributors to the Eclipse Foundation
 *
 *  See the NOTICE file(s) distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.eclipse.microprofile.telemetry.logs.tck.application;

import java.util.logging.Logger;

import org.eclipse.microprofile.telemetry.logs.tck.TestLibraries;
import org.eclipse.microprofile.telemetry.logs.tck.exporter.InMemoryLogRecordExporter;
import org.eclipse.microprofile.telemetry.logs.tck.exporter.InMemoryLogRecordExporterProvider;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.testng.Assert;
import org.testng.annotations.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider;
import jakarta.inject.Inject;

public class LogAppenderTest extends Arquillian {
    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemoryLogRecordExporter.class, InMemoryLogRecordExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsServiceProvider(ConfigurableLogRecordExporterProvider.class,
                        InMemoryLogRecordExporterProvider.class)
                .addAsResource(new StringAsset(
                        "otel.sdk.disabled=false\notel.metrics.exporter=\notel.traces.exporter=none\notel.logs.exporter=in-memory"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private OpenTelemetry openTelemetry;

    @Inject
    private InMemoryLogRecordExporter memoryExporter;

    private static final java.util.logging.Logger julLogger = Logger.getLogger("jul-logger");

    @Test
    void julTest() throws InterruptedException {
        // Install OpenTelemetry in logback appender
        OpenTelemetryAppender.install(openTelemetry);

        // Route JUL logs to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        julLogger.info("A JUL log message");
        Assert.assertEquals(memoryExporter.getFinishedLogRecordItems(1).get(0).getBody(), "A JUL log message");
    }
}
