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

public class JvmMemoryTest extends Arquillian {

    @Inject
    OpenTelemetry openTelemetry;

    @Deployment
    public static WebArchive createTestArchive() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(MetricsReader.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    void testJvmMemoryUsedMetric() throws IOException {
        Assert.assertTrue(
                MetricsReader.checkMessage("jvm.memory.used", "Measure of memory used.", "By",
                        MetricDataType.LONG_SUM.toString()));
    }

    @Test
    void testJvmMemoryCommittedMetric() throws IOException {
        Assert.assertTrue(
                MetricsReader.checkMessage("jvm.memory.committed", "Measure of memory committed.", "By",
                        MetricDataType.LONG_SUM.toString()));
    }

    @Test
    void testMemoryLimitMetric() throws IOException {
        Assert.assertTrue(
                MetricsReader.checkMessage("jvm.memory.limit", "Measure of max obtainable memory.", "{class}",
                        MetricDataType.LONG_SUM.toString()));
    }

    @Test
    void testMemoryUsedAfterLastGcMetric() throws IOException {
        Assert.assertTrue(
                MetricsReader.checkMessage("jvm.memory.used_after_last_gc",
                        "Measure of memory used, as measured after the most recent garbage collection event on this pool.",
                        "By",
                        MetricDataType.LONG_SUM.toString()));
    }

}
