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
package org.eclipse.microprofile.telemetry.tracing.tck.cdi;

import static org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset.SDK_DISABLED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.annotations.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;

public class SpanBeanTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {
        ConfigAsset config = new ConfigAsset().add(SDK_DISABLED, "false");

        return ShrinkWrap.create(WebArchive.class)
                .addAsResource(EmptyAsset.INSTANCE, "META-INF/beans.xml")
                .addAsResource(config, "META-INF/microprofile-config.properties");
    }

    @Inject
    private Span injectedSpan;

    @Inject
    private Tracer tracer;

    @Test
    public void spanBeanChange() {
        Span originalSpan = Span.current();
        // Check the injected span reflects the current span initially
        assertEquals(originalSpan.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());

        // Create a new span
        Span span1 = tracer.spanBuilder("span1").startSpan();
        // Check we have a real span with a different spanId
        assertNotEquals(originalSpan.getSpanContext().getSpanId(), span1.getSpanContext().getSpanId());

        // The original span should still be "current", so the injected span should still reflect it
        assertEquals(originalSpan.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());

        // Make span1 current
        try (Scope s = span1.makeCurrent()) {
            // Now the injected span should reflect span1
            assertEquals(span1.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());

            // Make a new span
            Span span2 = tracer.spanBuilder("span2").startSpan();
            // Injected span should still reflect span1
            assertEquals(span1.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());

            // Make span2 current
            try (Scope s2 = span2.makeCurrent()) {
                // Now the injected span should reflect span2
                assertEquals(span2.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());
            } finally {
                span2.end();
            }

            // After closing the scope, span1 is current again and the injected bean should reflect that
            assertEquals(span1.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());
        } finally {
            span1.end();
        }

        // After closing the scope, the original span is current again
        assertEquals(originalSpan.getSpanContext().getSpanId(), injectedSpan.getSpanContext().getSpanId());
    }

}
