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

import java.io.File;
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
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
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
        File[] slf4jFiles = Maven.resolver().resolve("org.slf4j:jul-to-slf4j:2.0.13").withTransitivity().asFile();
        File[] telemetryLogbackFiles = Maven.resolver()
                .resolve("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.2.0-alpha")
                .withTransitivity().asFile();
        File[] logbackFiles =
                Maven.resolver().resolve("ch.qos.logback:logback-classic:1.5.5").withTransitivity().asFile();

        return ShrinkWrap.create(WebArchive.class)
                .addClasses(InMemoryLogRecordExporter.class, InMemoryLogRecordExporterProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsLibraries(slf4jFiles)
                .addAsLibraries(telemetryLogbackFiles)
                .addAsLibraries(logbackFiles)
                .addAsServiceProvider(ConfigurableLogRecordExporterProvider.class,
                        InMemoryLogRecordExporterProvider.class)
                .addAsResource(new StringAsset(
                        "otel.sdk.disabled=false\notel.metrics.exporter=none\notel.traces.exporter=none\notel.logs.exporter=in-memory\notel.blrp.max.export.batch.size=1\notel.blrp.schedule.delay=30"),
                        "META-INF/microprofile-config.properties")
                .addAsResource("logback.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private OpenTelemetry openTelemetry;

    @Inject
    private InMemoryLogRecordExporter memoryExporter;

    private static final Logger julLogger = Logger.getLogger("jul-logger");

    @Test
    void julTest() throws InterruptedException {
        // Install OpenTelemetry in logback appender
        OpenTelemetryAppender.install(openTelemetry);

        // Route JUL logs to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        julLogger.info("A JUL log message");

        Assert.assertTrue(
                memoryExporter.getFinishedLogRecordItems(1).get(0).getBody().toString().contains("A JUL log message"));
    }
}
