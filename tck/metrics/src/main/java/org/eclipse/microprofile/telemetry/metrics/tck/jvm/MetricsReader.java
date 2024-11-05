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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.awaitility.Awaitility;
import org.testng.Assert;

public class MetricsReader {

    private static final String logFilePath = System.getProperty("mptelemetry.tck.log.file.path");

    /**
     * This method asserts that a log line matching the following format
     *
     * "name=<metricName>, description=<metricDescription>, unit=<metricUnit>, type=<metricType>"
     *
     * Can be found in the log file pointed to by the system property mptelemetry.tck.log.file.path. It will wait for up
     * to fifteen seconds for the log to appear.
     *
     * @param metricName
     *            The name of the metric we expect to find in the logs
     * @param metricDescription
     *            The description of the metric we expect to find in the logs
     * @param metricUnit
     *            The unit of the metric we expect to find in the logs
     * @param metricType
     *            The type of the metric we expect to find in the logs
     */
    public static void assertLogMessage(String metricName, String metricDescription, String metricUnit,
            String metricType) {

        String searchString = "name=" + metricName + ", description=" + metricDescription + ", unit=" + metricUnit
                + ", type=" + metricType;

        ExecutorService es = Executors.newFixedThreadPool(1);

        LogFileTailerAdaptor logFileTailerAdaptor =
                new LogFileTailerAdaptor(searchString);

        Tailer tailer = Tailer.builder()
                .setStartThread(true)
                .setPath(logFilePath)
                .setExecutorService(es)
                .setReOpen(false)
                .setTailerListener(logFileTailerAdaptor)
                .setTailFromEnd(false)
                .get();

        try (tailer) {
            Awaitility.await().atMost(15, SECONDS)
                    .untilAsserted(() -> Assert.assertTrue(logFileTailerAdaptor.foundMetric(),
                            "Did not find " + searchString + " in logfile: " + logFilePath));
        }
    }

    /**
     * This method asserts that a log line matching the following format
     *
     * "searchPattern=<line pattern that must be matched>"
     *
     * Can be found in the log file pointed to by the system property mptelemetry.tck.log.file.path. It will wait for up
     * to fifteen seconds for the log to appear.
     *
     * @param searchPattern
     *            The pattern to search for in the log file
     */
    public static void assertLogMessagePattern(String searchPattern) {

        ExecutorService es = Executors.newFixedThreadPool(1);

        LogFileTailerMatcherAdaptor logFileTailerAdaptor =
                new LogFileTailerMatcherAdaptor(searchPattern);

        Tailer tailer = Tailer.builder()
                .setStartThread(true)
                .setPath(logFilePath)
                .setExecutorService(es)
                .setReOpen(false)
                .setTailerListener(logFileTailerAdaptor)
                .setTailFromEnd(false)
                .get();

        try (tailer) {
            Awaitility.await().atMost(15, SECONDS)
                    .untilAsserted(() -> Assert.assertTrue(logFileTailerAdaptor.foundMetric(),
                            "Did not find " + searchPattern + " in logfile: " + logFilePath));
        }
    }

    private static class LogFileTailerAdaptor extends TailerListenerAdapter {

        private final String searchString;
        private boolean foundMetric = false;
        private Tailer tailer = null;

        public LogFileTailerAdaptor(String searchString) {
            this.searchString = searchString;
        }

        public void init(Tailer tailer) {
            this.tailer = tailer;
        }

        public void handle(String line) {
            if (line.contains(searchString)) {
                foundMetric = true;
                tailer.close();
            }
        }

        public boolean foundMetric() {
            return foundMetric;
        }
    }

    private static class LogFileTailerMatcherAdaptor extends TailerListenerAdapter {

        private final Pattern pattern;
        private boolean foundMetric = false;
        private Tailer tailer = null;

        public LogFileTailerMatcherAdaptor(String searchString) {
            this.pattern = Pattern.compile(searchString);
        }

        public void init(Tailer tailer) {
            this.tailer = tailer;
        }

        public void handle(String line) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                foundMetric = true;
                tailer.close();
            }
        }

        public boolean foundMetric() {
            return foundMetric;
        }
    }

}
