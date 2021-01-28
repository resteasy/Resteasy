package org.jboss.resteasy.microprofile.client.impl;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.ws.rs.core.UriBuilder;

import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;


public class MpClient extends ResteasyClient {

   private List<AsyncInvocationInterceptorFactory> asyncInterceptorFactories;

   public MpClient(final ClientHttpEngine engine, final ExecutorService executor, final boolean cleanupExecutor,
                    final ScheduledExecutorService scheduledExecutorService, final ClientConfiguration config,
                    final List<AsyncInvocationInterceptorFactory> asyncInterceptorFactories) {
        super(engine, executor, cleanupExecutor, scheduledExecutorService, config);
        this.asyncInterceptorFactories = asyncInterceptorFactories;
    }

    protected ResteasyWebTarget createClientWebTarget(ResteasyClient client, String uri, ClientConfiguration configuration) {
        return new MpClientWebTarget(client, uri, configuration, asyncInterceptorFactories);
    }

    protected ResteasyWebTarget createClientWebTarget(ResteasyClient client, URI uri, ClientConfiguration configuration) {
        return new MpClientWebTarget(client, uri, configuration, asyncInterceptorFactories);
    }

    protected ResteasyWebTarget createClientWebTarget(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration) {
        return new MpClientWebTarget(client, uriBuilder, configuration, asyncInterceptorFactories);
    }

}
