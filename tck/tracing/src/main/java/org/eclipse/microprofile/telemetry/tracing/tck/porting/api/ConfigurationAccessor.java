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
package org.eclipse.microprofile.telemetry.tracing.tck.porting.api;

import org.eclipse.microprofile.telemetry.tracing.tck.porting.PropertiesBasedConfigurationBuilder;

public class ConfigurationAccessor {
    private static Configuration current;

    private ConfigurationAccessor() {
    }

    /**
     * @param deploymentPhase
     *            Deployment phase (building test archive) initialization includes deployment specific properties
     * @return current JSR 365 TCK configuration
     */
    public static Configuration get(boolean deploymentPhase) {

        if (current == null) {
            try {
                current = new PropertiesBasedConfigurationBuilder().build(deploymentPhase);
            } catch (Exception e) {
                throw new IllegalStateException("Unable to get configuration", e);
            }
        }
        return current;
    }

    /**
     * @return current JSR 365 TCK configuration
     */
    public static Configuration get() {
        return get(false);
    }
}
