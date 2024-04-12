/*
 * Copyright (c) 2022-2023 Contributors to the Eclipse Foundation
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
package org.eclipse.microprofile.telemetry.metrics.tck.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.jboss.shrinkwrap.api.asset.Asset;

public class ConfigAsset implements Asset {

    public static final String SDK_DISABLED = "otel.sdk.disabled";

    private Properties properties = new Properties();

    @Override
    public InputStream openStream() {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            properties.store(os, null);
            return new ByteArrayInputStream(os.toByteArray());
        } catch (IOException e) {
            // Shouldn't happen since we're only using in memory streams
            throw new RuntimeException("Unexpected error saving properties", e);
        }
    }

    public ConfigAsset add(String key, String value) {
        properties.put(key, value);
        return this;
    }

}
