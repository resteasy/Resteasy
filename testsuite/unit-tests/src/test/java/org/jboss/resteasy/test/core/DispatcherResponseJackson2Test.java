package org.jboss.resteasy.test.core;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.test.common.DispatcherResponseUtils;
import org.junit.Before;
import org.junit.Test;

public class DispatcherResponseJackson2Test {
    
    private Dispatcher dispatcher;
    
    @Before
    public void before() {
        System.setProperty(ResteasyContextParameters.RESTEASY_PREFER_JACKSON_OVER_JSONB, "true");
        
        ResteasyProviderFactory.getInstance()
                .registerProviderInstance(new DispatcherResponseUtils.JsonSerializationExceptionMapper());
        
        dispatcher = new SynchronousDispatcher(ResteasyProviderFactory.getInstance());
    }
    
    @Test
    public void testResponseAfterSerializationFailureDoesNotContainsGarbage()
            throws UnsupportedEncodingException, URISyntaxException {
        dispatcher.getRegistry().addSingletonResource(new DispatcherResponseUtils.MockUnsafeResource());
        
        MockHttpRequest request = MockHttpRequest.get("/unsafe");
        MockUncommitedResponse response = new MockUncommitedResponse();
        
        dispatcher.invoke(request, response);
        
        assertEquals(500, response.getStatus());
        // Below does not fail if RESTEASY-2901 is fixed
        assertEquals("{\"ARQ-502\":\"JSON Serialization Error\"}", response.getContentAsString());
    }
    
    public static class MockUncommitedResponse extends MockHttpResponse {
        /**
         * In the scenario seen when reporting RESTEASY-2901 the response was not commited when checked by
         * {@link SynchronousDispatcher#writeException(org.jboss.resteasy.spi.HttpRequest, org.jboss.resteasy.spi.HttpResponse, Throwable, java.util.function.Consumer)}
         * 
         * TODO: Maybe this check returning false in such a scenario is the root cause of the issue? JSONB doesn't need
         * this, so maybe it just doesn't mess with resteasy's OutputStream as jackson does.
         */
        @Override
        public boolean isCommitted() {
            return false;
        }
    }
    
}
