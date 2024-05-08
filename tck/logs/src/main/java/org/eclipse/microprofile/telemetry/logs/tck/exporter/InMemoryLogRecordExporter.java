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

package org.eclipse.microprofile.telemetry.logs.tck.exporter;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.testng.Assert;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InMemoryLogRecordExporter implements LogRecordExporter {
    private boolean isStopped = false;
    private final Queue<LogRecordData> finishedLogItems = new ConcurrentLinkedQueue<>();

    public void reset() {
        finishedLogItems.clear();
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        if (isStopped) {
            return CompletableResultCode.ofFailure();
        }
        logs.stream()
                .forEach(finishedLogItems::add);
        return CompletableResultCode.ofSuccess();
    }

    public List<LogRecordData> getFinishedLogRecordItems(int itemCount) {
        assertItemCount(itemCount);
        return finishedLogItems.stream()
                .collect(Collectors.toList());
    }

    public void assertItemCount(int itemCount) {
        Awaitility.await().pollDelay(3, SECONDS).atMost(10, SECONDS)
                .untilAsserted(() -> Assert.assertEquals(finishedLogItems.size(), itemCount));
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        finishedLogItems.clear();
        isStopped = true;
        return CompletableResultCode.ofSuccess();
    }
}
