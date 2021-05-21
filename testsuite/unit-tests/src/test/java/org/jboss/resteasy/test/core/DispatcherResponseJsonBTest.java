package org.jboss.resteasy.test.core;

import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import javax.ws.rs.ProcessingException;

import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.test.common.DispatcherResponseUtils;
import org.junit.Before;
import org.junit.Test;

public class DispatcherResponseJsonBTest {
    
    private Dispatcher dispatcher;
    
    @Before
    public void before() {
        ResteasyProviderFactory.getInstance()
                .getExceptionMappers()
                .put(ProcessingException.class, new DispatcherResponseUtils.JsonSerializationExceptionMapper());
        
        dispatcher = new SynchronousDispatcher(ResteasyProviderFactory.getInstance());
    }
    
    @Test
    public void testResponseAfterSerializationFailureDoesNotContainsGarbage()
            throws UnsupportedEncodingException, URISyntaxException {
        dispatcher.getRegistry().addSingletonResource(new DispatcherResponseUtils.MockUnsafeResource());
        
        MockHttpRequest request = MockHttpRequest.get("/unsafe");
        MockHttpResponse response = new MockHttpResponse();
        
        dispatcher.invoke(request, response);
        
        assertEquals(500, response.getStatus());
        // Below already doesn't fail even before RESTEASY-2901 is fixed
        assertEquals("{\"ARQ-502\":\"JSON Serialization Error\"}", response.getContentAsString());
    }
    
}
