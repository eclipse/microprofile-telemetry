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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

public class TestResourceProvider implements ResourceProvider {

    public static final AttributeKey<String> TEST_KEY1 = AttributeKey.stringKey("otel.test.key1");
    public static final AttributeKey<String> TEST_KEY2 = AttributeKey.stringKey("otel.test.key2");

    /** {@inheritDoc} */
    @Override
    public Resource createResource(ConfigProperties config) {
        // Read two test values from config and add them
        return Resource.builder()
                .put(TEST_KEY1, config.getString(TEST_KEY1.getKey()))
                .put(TEST_KEY2, config.getString(TEST_KEY2.getKey()))
                .build();
    }

}
