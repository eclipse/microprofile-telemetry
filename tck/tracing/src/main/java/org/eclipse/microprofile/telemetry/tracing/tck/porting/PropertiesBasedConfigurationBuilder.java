/*
 * Copyright 2010, Red Hat, Inc., and individual contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//This code comes from
//https://github.com/jakartaee/cdi-tck/blob/master/impl/src/main/java/org/jboss/cdi/tck/impl/PropertiesBasedConfigurationBuilder.java
//with minor changes. Full credit to the original authors.
package org.eclipse.microprofile.telemetry.tracing.tck.porting;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;

import org.eclipse.microprofile.telemetry.tracing.tck.porting.api.Configuration;

public class PropertiesBasedConfigurationBuilder {

    public static final String RESOURCE_BUNDLE = "META-INF/microprofile-telemetry-tck.properties";

    @SuppressWarnings("unchecked")
    public Configuration build(boolean deploymentPhase) {

        Configuration configuration = new Configuration();

        configuration.setExecutor(
                (Executor) getInstanceValue(Configuration.EXECUTOR_PROPERTY_NAME, Executor.class, !deploymentPhase));

        return configuration;
    }
    /**
     *
     * @param <T>
     * @param propertyName
     * @param expectedType
     * @param required
     * @return
     */
    @SuppressWarnings("unchecked")
    protected <T> Class<T> getClassValue(String propertyName, Class<T> expectedType, boolean required) {

        Set<Class<T>> classes = new HashSet<Class<T>>();

        for (String className : getPropertyValues(propertyName)) {
            ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                if (currentThreadClassLoader != null) {
                    classes.add((Class<T>) currentThreadClassLoader.loadClass(className));
                } else {
                    classes.add((Class<T>) Class.forName(className));
                }

            } catch (ClassNotFoundException | LinkageError e) {
                throw new IllegalArgumentException("Implementation class with name " + className
                        + " not found using classloader "
                        + (currentThreadClassLoader != null
                                ? currentThreadClassLoader
                                : this.getClass().getClassLoader()),
                        e);
            }
        }

        if (classes.size() == 0) {
            if (required) {
                throw new IllegalArgumentException(
                        "Cannot find any implementations of " + expectedType.getSimpleName() + ", check that "
                                + propertyName
                                + " is specified");
            } else {
                return null;
            }
        } else if (classes.size() > 1) {
            throw new IllegalArgumentException(
                    "More than one implementation of " + expectedType.getSimpleName() + " specified by " + propertyName
                            + ", not sure which one to use!");
        } else {
            return classes.iterator().next();
        }
    }

    /**
     *
     * @param <T>
     * @param propertyName
     * @param expectedType
     * @param required
     * @return
     */
    protected <T> T getInstanceValue(String propertyName, Class<T> expectedType, boolean required) {

        T instance = null;

        Class<T> clazz = getClassValue(propertyName, expectedType, required);
        if (clazz != null) {
            try {
                instance = clazz.newInstance();
            } catch (InstantiationException e) {
                throw new IllegalStateException("Error instantiating " + clazz + " specified by " + propertyName, e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Error instantiating " + clazz + " specified by " + propertyName, e);
            }
        }
        return instance;
    }
    /**
     * Get a list of possible values for a given key.
     *
     * First, System properties are tried, followed by the specified resource bundle (first in classpath only).
     *
     * @param key
     *            The key to search for
     * @return A list of possible values. An empty list is returned if there are no matches.
     */
    public Set<String> getPropertyValues(String key) {
        Set<String> values = new HashSet<String>();
        addPropertiesFromSystem(key, values);
        addPropertiesFromResourceBundle(key, values);
        return values;
    }

    /**
     * Adds matches from system properties
     *
     * @param key
     *            The key to match
     * @param values
     *            The currently found values
     */
    private void addPropertiesFromSystem(String key, Set<String> values) {
        addProperty(key, System.getProperty(key), values);
    }

    /**
     * Adds matches from detected resource bundles.
     *
     * @param key
     *            The key to match
     * @param values
     *            The currently found values
     */
    private void addPropertiesFromResourceBundle(String key, Set<String> values) {
        addPropertiesFromResourceBundle(key, values, new StringBuilder());
    }

    /**
     * Adds matches from detected resource bundles
     *
     * @param key
     *            The key to match
     * @param values
     *            The currently found values
     * @param info
     *            a StringBuilder to append information about the found property, useful for debugging duplicates
     */
    private void addPropertiesFromResourceBundle(String key, Set<String> values, StringBuilder info) {
        try {
            int count = 0;
            for (Enumeration<URL> e = getResources(RESOURCE_BUNDLE); e.hasMoreElements();) {

                URL url = e.nextElement();
                Properties properties = new Properties();
                InputStream propertyStream = url.openStream();

                try {
                    properties.load(propertyStream);
                    String value = properties.getProperty(key);
                    if (value != null) {
                        values.add(value);
                        info.append(String.format("\t%d: %s=%s\n", count++, url.toExternalForm(), value));
                    }
                } finally {
                    if (propertyStream != null) {
                        propertyStream.close();
                    }
                }
            }

        } catch (IOException e) {
            // No-op, file is optional
        }
    }

    /**
     * Add the property to the set of properties only if it hasn't already been added
     *
     * @param key
     *            The key searched for
     * @param value
     *            The value of the property
     * @param values
     *            The currently found values
     */
    private void addProperty(String key, String value, Set<String> values) {
        if (value != null) {
            values.add(value);
        }
    }

    /**
     *
     * @param name
     * @return
     * @throws IOException
     */
    public Enumeration<URL> getResources(String name) throws IOException {

        if (Thread.currentThread().getContextClassLoader() != null) {
            return Thread.currentThread().getContextClassLoader().getResources(name);
        } else {
            return getClass().getClassLoader().getResources(name);
        }
    }
}
