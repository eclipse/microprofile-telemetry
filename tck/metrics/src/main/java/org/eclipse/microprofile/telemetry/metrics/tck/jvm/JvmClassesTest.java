/*
 **********************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICES file(s) distributed with this work for additional
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
 *
 * SPDX-License-Identifier: Apache-2.0
 **********************************************************************/
package org.eclipse.microprofile.telemetry.metrics.tck.jvm;

import java.io.IOException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import jakarta.inject.Inject;

public class JvmClassesTest extends Arquillian {

    @Inject
    OpenTelemetry openTelemetry;

    @Deployment
    public static WebArchive createTestArchive() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(MetricsReader.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    void testClassLoadedMetrics() throws IOException {
        Assert.assertTrue(
                MetricsReader.checkMessage("jvm.class.loaded", "Number of classes loaded since JVM start.", "{class}",
                        MetricDataType.LONG_SUM.toString()));
    }

    @Test
    void testClassUnloadedMetrics() throws IOException {
        Assert.assertTrue(
                MetricsReader.checkMessage("jvm.class.unloaded", "Number of classes unloaded since JVM start.",
                        "{class}", MetricDataType.LONG_SUM.toString()));
    }

    @Test
    void testClassCountMetrics() throws IOException {
        Assert.assertTrue(MetricsReader.checkMessage("jvm.class.count", "Number of classes currently loaded.",
                "{class}", MetricDataType.LONG_SUM.toString()));
    }

}
