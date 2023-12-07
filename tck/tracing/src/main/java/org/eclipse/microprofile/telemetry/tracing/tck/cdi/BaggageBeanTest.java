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
package org.eclipse.microprofile.telemetry.tracing.tck.cdi;

import static org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset.SDK_DISABLED;

import org.eclipse.microprofile.telemetry.tracing.tck.ConfigAsset;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.testng.Assert;
import org.testng.annotations.Test;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import jakarta.inject.Inject;

public class BaggageBeanTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {
        ConfigAsset config = new ConfigAsset().add(SDK_DISABLED, "false");

        return ShrinkWrap.create(WebArchive.class)
                .addAsResource(EmptyAsset.INSTANCE, "META-INF/beans.xml")
                .addAsResource(config, "META-INF/microprofile-config.properties");
    }

    @Inject
    private Baggage injectedBaggage;

    /**
     * Test that the injected {@link Baggage} bean always reflects the current {@link Baggage} instance
     */
    @Test
    public void baggageBeanChange() {
        String key = "testKey";

        // Default Baggage should not have our key
        Assert.assertNull(injectedBaggage.getEntryValue(key));

        Baggage baggage1 = Baggage.builder().put(key, "value1").build();

        // After creating a new baggage, the current baggage still doesn't have our key
        Assert.assertNull(injectedBaggage.getEntryValue(key));

        // Make baggage1 the current baggage
        try (Scope s = baggage1.makeCurrent()) {
            // Baggage bean should now return our test key
            Assert.assertEquals(injectedBaggage.getEntryValue(key), "value1");

            // Creating another new baggage does not change the current baggage
            Baggage baggage2 = Baggage.builder().put(key, "value2").build();
            Assert.assertEquals(injectedBaggage.getEntryValue(key), "value1");

            try (Scope s2 = baggage2.makeCurrent()) {
                // but if we make baggage2 current, then the bean now has the value from baggage2
                Assert.assertEquals(injectedBaggage.getEntryValue(key), "value2");
            }

            // After reverting to baggage1 as the current baggage
            Assert.assertEquals(injectedBaggage.getEntryValue(key), "value1");
        }

        // After reverting to the original baggage
        Assert.assertNull(injectedBaggage.getEntryValue(key));
    }
}
