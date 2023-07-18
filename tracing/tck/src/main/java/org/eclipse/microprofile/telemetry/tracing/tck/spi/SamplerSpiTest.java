/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.microprofile.telemetry.tracing.tck.spi;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.eclipse.microprofile.telemetry.tracing.tck.TestLibraries;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.annotations.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider;
import jakarta.inject.Inject;

public class SamplerSpiTest extends Arquillian {

    @Inject
    private Tracer tracer;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(TestSampler.class, TestSamplerProvider.class)
                .addAsServiceProvider(ConfigurableSamplerProvider.class, TestSamplerProvider.class)
                .addAsLibrary(TestLibraries.AWAITILITY_LIB)
                .addAsResource(
                        new StringAsset("otel.sdk.disabled=false\notel.traces.sampler=" + TestSamplerProvider.NAME),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void testSampler() {
        // Span 1 does not set SAMPLE_ME, so it should not be sampled
        Span span1 = tracer.spanBuilder("span1").startSpan();
        try {
            assertFalse(span1.isRecording());
            assertFalse(span1.getSpanContext().isSampled());
        } finally {
            span1.end();
        }

        Span span2 = tracer.spanBuilder("span2").setAttribute(TestSampler.SAMPLE_ME, true).startSpan();
        try {
            // assertTrue(span2.isRecording());
            assertTrue(span2.getSpanContext().isSampled());
        } finally {
            span2.end();
        }

    }
}
