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
package org.eclipse.microprofile.telemetry.tracing.tck.async;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.microprofile.telemetry.tracing.tck.porting.api.ConfigurationAccessor;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * This endpoint is used to test MP Telemetry integration with async JAX-RS resource methods.
 * <p>
 * It does the following:
 * <ul>
 * <li>Creates a span
 * <li>Reads the baggage entry with key {@link #BAGGAGE_KEY} and if present sets span attribute
 * {@link #BAGGAGE_VALUE_ATTR} to the entry value
 * <li>Submits a subtask to a managed executor with the context propagated and returns a CompletableFuture representing
 * the result of the subtask
 * <li>In the subtask it:
 * <ul>
 * <li>Creates a span
 * <li>Sleeps three seconds (to ensure that there is no chance of the subtask completing before the resource method
 * returns)
 * <li>Reads the baggage entry with key {@link #BAGGAGE_KEY} and if present sets span attribute
 * {@link #BAGGAGE_VALUE_ATTR} to the entry value
 * <li>Returns "OK"
 * </ul>
 * </ul>
 *
 */
@Path("JaxRsServerAsyncTestEndpoint")
public class JaxRsServerAsyncTestEndpoint {

    public static final String BAGGAGE_KEY = "test.baggage.key";
    public static final AttributeKey<String> BAGGAGE_VALUE_ATTR = AttributeKey.stringKey("test.baggage");

    private Executor executor = ConfigurationAccessor.get().getExecutor();

    @Inject
    private Tracer tracer;

    @GET
    @Path("completionstage")
    public CompletionStage<String> getCompletionStage(@QueryParam(value = "baggageValue") String queryValue) {
        Span span = Span.current();

        // Retrieve the test baggage value (if present) and store in the span
        String baggageValue = Baggage.current().getEntryValue(BAGGAGE_KEY);
        if (baggageValue != null) {
            span.setAttribute(BAGGAGE_VALUE_ATTR, baggageValue);
        }

        // Call a subtask, propagating the context
        Executor contextExecutor = Context.taskWrapping(executor);
        CompletableFuture<String> result = CompletableFuture.supplyAsync(this::subtask, contextExecutor);

        // Return the async result
        return result;
    }

    @GET
    @Path("completionstageerror")
    public CompletionStage<Response> getCompletionStageError(@QueryParam(value = "baggageValue") String queryValue) {
        Span span = Span.current();

        // Retrieve the test baggage value (if present) and store in the span
        String baggageValue = Baggage.current().getEntryValue(BAGGAGE_KEY);
        if (baggageValue != null) {
            span.setAttribute(BAGGAGE_VALUE_ATTR, baggageValue);
        }

        // Call a subtask, propagating the context
        Executor contextExecutor = Context.taskWrapping(executor);
        CompletableFuture<Response> result = CompletableFuture.supplyAsync(this::subtaskError, contextExecutor);
        // Return the async result
        return result;
    }

    @GET
    @Path("suspend")
    public void getSuspend(@Suspended AsyncResponse async, @QueryParam(value = "baggageValue") String queryValue) {
        Span span = Span.current();

        // Retrieve the test baggage value (if present) and store in the span
        String baggageValue = Baggage.current().getEntryValue(BAGGAGE_KEY);
        if (baggageValue != null) {
            span.setAttribute(BAGGAGE_VALUE_ATTR, baggageValue);
        }

        // Call a subtask, propagating the context
        Executor contextExecutor = Context.taskWrapping(executor);
        contextExecutor.execute(() -> {
            // Ensure we call resume, either with the result or a thrown exception
            try {
                async.resume(subtask());
            } catch (Throwable t) {
                async.resume(t);
            }
        });
    }

    @GET
    @Path("suspenderror")
    public void getSuspendError(@Suspended AsyncResponse async, @QueryParam(value = "baggageValue") String queryValue) {
        Span span = Span.current();

        // Retrieve the test baggage value (if present) and store in the span
        String baggageValue = Baggage.current().getEntryValue(BAGGAGE_KEY);
        if (baggageValue != null) {
            span.setAttribute(BAGGAGE_VALUE_ATTR, baggageValue);
        }

        // Call a subtask, propagating the context
        Executor contextExecutor = Context.taskWrapping(executor);
        contextExecutor.execute(() -> {
            // Ensure we call resume, either with the result or a thrown exception
            try {
                async.resume(subtaskError());
            } catch (Throwable t) {
                async.resume(t);
            }
        });
    }

    private String subtask() {
        Span span = tracer.spanBuilder("subtask").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Sleep a while to ensure that this is running after get() has returned
            Thread.sleep(3000);

            // Retrieve the test baggage value (if present) and store in the span
            String baggageValue = Baggage.current().getEntryValue(BAGGAGE_KEY);
            if (baggageValue != null) {
                span.setAttribute(BAGGAGE_VALUE_ATTR, baggageValue);
            }

            return "OK";

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    private Response subtaskError() {
        Span span = tracer.spanBuilder("subtask").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Sleep a while to ensure that this is running after get() has returned
            Thread.sleep(3000);

            // Retrieve the test baggage value (if present) and store in the span
            String baggageValue = Baggage.current().getEntryValue(BAGGAGE_KEY);
            if (baggageValue != null) {
                span.setAttribute(BAGGAGE_VALUE_ATTR, baggageValue);
            }

            // Return bad request error code so we can differentiate between an
            // unexpected exception which would cause an internal server error
            return Response.status(Status.BAD_REQUEST).build();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Collections.singleton(JaxRsServerAsyncTestEndpoint.class);
        }
    }
}
