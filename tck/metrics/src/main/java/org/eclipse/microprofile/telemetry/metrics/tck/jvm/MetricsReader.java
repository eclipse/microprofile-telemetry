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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class MetricsReader {

    private static final String logFilePath = System.getProperty("log.file.path");

    public static boolean checkMessage(String metricName, String metricDescription, String metricUnit,
            String metricType)
            throws IOException {
        try {
            Thread.sleep(5000);
            try {
                BufferedReader reader = new BufferedReader(new FileReader(logFilePath));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(
                            "name=" + metricName + ", description=" + metricDescription + ", unit=" + metricUnit
                                    + ", type=" + metricType)) {

                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
    }

}
