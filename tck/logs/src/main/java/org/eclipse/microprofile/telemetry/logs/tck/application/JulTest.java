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

package org.eclipse.microprofile.telemetry.logs.tck.application;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.Test;

import io.opentelemetry.api.OpenTelemetry;
import jakarta.inject.Inject;

public class JulTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addAsResource(new StringAsset(
                        "otel.sdk.disabled=false\notel.metrics.exporter=none\notel.traces.exporter=none\notel.logs.exporter=logging\notel.service.name=openliberty"),
                        "META-INF/microprofile-config.properties")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Inject
    private OpenTelemetry openTelemetry;

    private static final Logger julLogger = Logger.getLogger("jul-logger");

    private static final String logFilePath = System.getProperty("mptelemetry.tck.log.file.path");

    private static final String JUL_INFO_MESSAGE = "a very distinguishable info message";
    private static final String JUL_WARN_MESSAGE = "a very distinguishable warning message";

    @Test
    void julInfoTest() throws IOException {
        julLogger.log(Level.INFO, JUL_INFO_MESSAGE);
        try {
            Assert.assertTrue(checkMessage(".*INFO.*" + JUL_INFO_MESSAGE + ".*scopeInfo:.*"));
        } catch (IOException e) {
        }
    }

    @Test
    void julWarnTest() throws IOException {
        julLogger.log(Level.WARNING, JUL_WARN_MESSAGE);
        try {
            Assert.assertTrue(checkMessage(".*WARN.*" + JUL_WARN_MESSAGE + ".*scopeInfo:.*"));
        } catch (IOException e) {
        }
    }

    public boolean checkMessage(String logMessage) throws IOException {
        try {
            try {
                Thread.sleep(5000);
                BufferedReader reader = new BufferedReader(new FileReader(logFilePath));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.matches(logMessage)) {
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
