/*
 * Copyright (c) 2016-2022 Contributors to the Eclipse Foundation
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

package org.eclipse.microprofile.telemetry.tracing.tck;

import org.jboss.arquillian.container.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.spi.client.protocol.metadata.Servlet;
import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.test.spi.TestClass;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.restassured.RestAssured;

public class ArquillianLifecycle {
    public void beforeDeploy(@Observes BeforeDeploy event, TestClass testClass) {
        GlobalOpenTelemetry.resetForTest();
    }

    @Inject
    Instance<ProtocolMetaData> protocolMetadata;

    public void afterDeploy(@Observes AfterDeploy event, TestClass testClass) {
        HTTPContext httpContext = protocolMetadata.get().getContexts(HTTPContext.class).iterator().next();
        Servlet servlet = httpContext.getServlets().iterator().next();
        String baseUri = servlet.getBaseURI().toString();
        TestConfigSource.configuration.put("baseUri", baseUri);

        RestAssured.port = httpContext.getPort();
        RestAssured.basePath = servlet.getBaseURI().getPath();
    }
}
