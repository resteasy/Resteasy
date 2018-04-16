package org.jboss.resteasy.test.providers.sse;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.test.providers.sse.resource.SseReconnectResource;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.SseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(Arquillian.class)
@RunAsClient
public class SseReconnectTest {

    @Deployment
    public static Archive<?> deploy() {
        WebArchive war = TestUtil.prepareArchive(SseReconnectTest.class.getSimpleName());
         return TestUtil.finishContainerPrepare(war, null, SseReconnectResource.class);
    }

    private String generateURL(String path)
    {
        return PortProviderUtil.generateURL(path, SseReconnectTest.class.getSimpleName());
    }

    /**
     * @tpTestDetails Check SseEvent.getReconnectDelay() returns SseEvent.RECONNECT_NOT_SET,
     * when OutboundSseEvent.reconnectDelay() is not set.
     * @tpInfo RESTEASY-1680
     * @tpSince RESTEasy 3.5.0
     */
    @Test
    public void testReconnectDelayIsNotSet() throws Exception {
        Client client = ClientBuilder.newClient();
        try {
            WebTarget baseTarget = client.target(generateURL("/reconnect/defaultReconnectDelay"));
            try (Response response = baseTarget.request().get()) {
                Assert.assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
                Assert.assertEquals(SseEvent.RECONNECT_NOT_SET, (long) response.readEntity(long.class));
            }
        } finally {
            client.close();
        }
    }

    /**
     * @tpTestDetails *** Check SseEvent.getReconnectDelay() returns correct delay,
     * when OutboundSseEvent.reconnectDelay() is set.
     * @tpInfo RESTEASY-1680
     * @tpSince RESTEasy 3.5.0
     */
    @Test
    public void testReconnectDelayIsSet() throws Exception {
        Client client = ClientBuilder.newClient();
        try {
            WebTarget baseTarget = client.target(generateURL("/reconnect/reconnectDelaySet"));
            try (Response response = baseTarget.request().get()) {
                Assert.assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
                Assert.assertEquals(1000L, (long) response.readEntity(long.class));
            }
        } finally {
            client.close();
        }
    }

    /**
     * @tpTestDetails SseEventSource receives HTTP 503 + "Retry-After" from the SSE endpoint. Check that SseEventSource
     * retries after the specified period
     * @tpInfo RESTEASY-1680
     * @tpSince RESTEasy 3.5.0
     */
    @Test
    public void testSseEndpointUnavailable() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger errors = new AtomicInteger(0);
        final List<String> results = new ArrayList<String>();
        Client client = ClientBuilder.newBuilder().build();
        try {
            WebTarget target = client.target(generateURL("/reconnect/unavailable"));
            SseEventSource msgEventSource = SseEventSource.target(target).build();
            try (SseEventSource eventSource = msgEventSource) {
                eventSource.register(event -> {
                    results.add(event.readData(String.class));
                    latch.countDown();
                }, ex -> {
                    errors.incrementAndGet();
                    Assert.assertTrue("ServiceUnavalile exception is expected", ex instanceof ServiceUnavailableException);
                });
                eventSource.open();

                boolean waitResult = latch.await(30, TimeUnit.SECONDS);
                Assert.assertEquals(0, errors.get());
                Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
            }
            Assert.assertTrue("ServiceAvailable message is expected", results.get(0).equals("ServiceAvailable"));
        } finally {
            client.close();
        }
    }

}
