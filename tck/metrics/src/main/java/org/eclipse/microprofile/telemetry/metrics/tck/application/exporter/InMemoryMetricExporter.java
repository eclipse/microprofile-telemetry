/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

package org.eclipse.microprofile.telemetry.metrics.tck.application.exporter;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.testng.Assert;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InMemoryMetricExporter implements MetricExporter {

    private final Queue<MetricData> finishedMetricItems = new ConcurrentLinkedQueue<>();
    private final AggregationTemporality aggregationTemporality;
    private boolean isStopped = false;

    public InMemoryMetricExporter() {
        aggregationTemporality = AggregationTemporality.DELTA;
    }

    /**
     * Returns a {@code List} of the finished {@code Metric}s, represented by {@code MetricData}.
     *
     * @return a {@code List} of the finished {@code Metric}s.
     */
    public List<MetricData> getFinishedMetricItems() {
        return finishedMetricItems.stream()
                .collect(Collectors.toList());
    }

    public List<MetricData> getMetricData(String metricName) {
        assertMetricNameFound(metricName);
        return getFinishedMetricItems().stream().filter(metric -> metric.getName() == metricName)
                .collect(Collectors.toList());
        // .orElseThrow(() -> new IllegalStateException("No metric found with type " + dataType));
    }
    public void assertMetricNameFound(String metricName) {
        Awaitility.await().pollDelay(5, SECONDS).atMost(10, SECONDS)
                .untilAsserted(() -> Assert.assertTrue(
                        getFinishedMetricItems().stream().filter(metric -> metric.getName() == metricName)
                                .collect(Collectors.toList()).size() > 0));
    }

    /**
     * Clears the internal {@code List} of finished {@code Metric}s.
     *
     * <p>
     * Does not reset the state of this exporter if already shutdown.
     */
    public void reset() {
        finishedMetricItems.clear();
    }

    @Override
    public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return aggregationTemporality;
    }

    /**
     * Exports the collection of {@code Metric}s into the inmemory queue.
     *
     * <p>
     * If this is called after {@code shutdown}, this will return {@code ResultCode.FAILURE}.
     */
    @Override
    public CompletableResultCode export(Collection<MetricData> metrics) {
        if (isStopped) {
            return CompletableResultCode.ofFailure();
        }
        finishedMetricItems.addAll(metrics);
        return CompletableResultCode.ofSuccess();
    }

    /**
     * The InMemory exporter does not batch metrics, so this method will immediately return with success.
     *
     * @return always Success
     */
    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }
    /**
     * Clears the internal {@code List} of finished {@code Metric}s.
     *
     * <p>
     * Any subsequent call to export() function on this MetricExporter, will return {@code
     * CompletableResultCode.ofFailure()}
     */
    @Override
    public CompletableResultCode shutdown() {
        isStopped = true;
        finishedMetricItems.clear();
        return CompletableResultCode.ofSuccess();
    }
}
