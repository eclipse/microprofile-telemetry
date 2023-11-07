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
package org.eclipse.microprofile.telemetry.tracing.tck.rest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

public class PropagationHelper {

    public static final String BAGGAGE_VALUE = "test.baggage.value";
    public static final String BAGGAGE_METADATA = "test.baggage.metadata";
    public static final AttributeKey<List<String>> PROPAGATION_HEADERS_ATTR =
            AttributeKey.stringArrayKey("test.propagation.headers");
    public static final String BAGGAGE_KEY = "test.baggage.key";
    public static final AttributeKey<String> BAGGAGE_VALUE_ATTR = AttributeKey.stringKey("test.baggage");
    public static final AttributeKey<String> BAGGAGE_METADATA_ATTR = AttributeKey.stringKey("test.baggage.metadata");
    public static final List<String> PROPAGATION_HEADER_NAMES = Arrays.asList("baggage", "traceparent",
            "b3",
            "X-B3-TraceId", "X-B3-SpanId", "X-B3-ParentSpanId", "X-B3-Sampled",
            "uber-trace-id");

    // No instances
    private PropagationHelper() {
    };

    @Path("/")
    public static class SpanResource {
        @GET
        @Path("/span")
        public String get(@Context HttpHeaders headers) {
            Span span = Span.current();

            // Extract the propagation headers and store in the span
            List<String> propagationHeaders = headers.getRequestHeaders().keySet().stream()
                    .filter(h -> PROPAGATION_HEADER_NAMES.contains(h) || h.startsWith("uberctx-"))
                    .collect(Collectors.toList());
            span.setAttribute(AttributeKey.stringArrayKey("test.propagation.headers"), propagationHeaders);

            // Extract the test baggage value (if present) and store in the span
            BaggageEntry baggageEntry = Baggage.current().asMap().get(BAGGAGE_KEY);
            if (baggageEntry != null) {
                span.setAttribute(BAGGAGE_VALUE_ATTR, baggageEntry.getValue());
                span.setAttribute(BAGGAGE_METADATA_ATTR, baggageEntry.getMetadata().getValue());
            }

            return "OK";
        }
    }

    @RegisterRestClient(configKey = "client")
    @Path("/")
    public interface SpanResourceClient {
        @GET
        @Path("/span")
        Response span();
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

    }

    public static boolean isPropagationHeader(String header) {
        return PROPAGATION_HEADER_NAMES.contains(header)
                || header.startsWith("uberctx-"); // Jaeger baggage headers use keys as part of the header but have a
                                                  // common prefix
    }
}
