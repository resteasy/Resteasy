/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.resteasy.plugins.providers.jsonb;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbException;

/**
 * A delegating {@link Jsonb} implementation where the lifecycle is managed elsewhere.
 * <p>
 * This delegates all methods with the exception of the {@link #close()} method which does nothing. It's the
 * responsibility of the provider to handle the lifecycle of the delegate.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ManagedJsonb implements Jsonb {
    private final Jsonb delegate;

    ManagedJsonb(final Jsonb delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T fromJson(final String str, final Class<T> type) throws JsonbException {
        return delegate.fromJson(str, type);
    }

    @Override
    public <T> T fromJson(final String str, final Type runtimeType) throws JsonbException {
        return delegate.fromJson(str, runtimeType);
    }

    @Override
    public <T> T fromJson(final Reader reader, final Class<T> type) throws JsonbException {
        return delegate.fromJson(reader, type);
    }

    @Override
    public <T> T fromJson(final Reader reader, final Type runtimeType) throws JsonbException {
        return delegate.fromJson(reader, runtimeType);
    }

    @Override
    public <T> T fromJson(final InputStream stream, final Class<T> type) throws JsonbException {
        return delegate.fromJson(stream, type);
    }

    @Override
    public <T> T fromJson(final InputStream stream, final Type runtimeType) throws JsonbException {
        return delegate.fromJson(stream, runtimeType);
    }

    @Override
    public String toJson(final Object object) throws JsonbException {
        return delegate.toJson(object);
    }

    @Override
    public String toJson(final Object object, final Type runtimeType) throws JsonbException {
        return delegate.toJson(object, runtimeType);
    }

    @Override
    public void toJson(final Object object, final Writer writer) throws JsonbException {
        delegate.toJson(object, writer);
    }

    @Override
    public void toJson(final Object object, final Type runtimeType, final Writer writer) throws JsonbException {
        delegate.toJson(object, runtimeType, writer);
    }

    @Override
    public void toJson(final Object object, final OutputStream stream) throws JsonbException {
        delegate.toJson(object, stream);
    }

    @Override
    public void toJson(final Object object, final Type runtimeType, final OutputStream stream) throws JsonbException {
        delegate.toJson(object, runtimeType, stream);
    }

    /**
     * <p>
     * <strong>NOTE:</strong> This does not close any resources in this context. The delegate provider is responsible
     * for the lifecycle.
     * </p>
     *
     * {@inheritDoc}
     */
    @Override
    public void close() throws Exception {
        // Do nothing on close as this is managed elsewhere
    }
}
