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

import java.util.List;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

/**
 * A test sampler which looks for the "test.sample.me" attribute to decide whether a span should be sampled
 */
public class TestSampler implements Sampler {

    public static final AttributeKey<Boolean> SAMPLE_ME = AttributeKey.booleanKey("test.sample.me");

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Test sampler, samples if test.sample.me is true";
    }

    /** {@inheritDoc} */
    @Override
    public SamplingResult shouldSample(Context context, String traceId, String name, SpanKind spanKind,
            Attributes attributes, List<LinkData> parentLinks) {

        if (attributes.get(SAMPLE_ME) == Boolean.TRUE) {
            return SamplingResult.recordAndSample();
        } else {
            return SamplingResult.drop();
        }
    }

}
