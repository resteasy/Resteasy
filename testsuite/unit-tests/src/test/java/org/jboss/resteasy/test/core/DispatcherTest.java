package org.jboss.resteasy.test.core;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

import org.jboss.resteasy.core.Dispatcher;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DispatcherTest {

    private Dispatcher dispatcher;


    @Before
    public void before() {
        dispatcher = MockDispatcherFactory.createDispatcher();
    }

    // @see https://issues.jboss.org/browse/RESTEASY-1865
    @Test
    public void testSingletonResource() throws URISyntaxException, UnsupportedEncodingException {
        dispatcher.getRegistry().addSingletonResource(new ParentResource());
        dispatcher.getRegistry().addSingletonResource(new ChildResource("I'm singleton child"));

        MockHttpRequest request = MockHttpRequest.get("/parent");
        MockHttpResponse response = new MockHttpResponse();

        dispatcher.invoke(request, response);

        assertEquals(200, response.getStatus());
        assertEquals("I'm singleton child", response.getContentAsString());
    }




    @Path("/parent")
    public static class ParentResource {

        @Context
        public ResourceContext resourceCtx;


        @GET
        public String get() {
            ChildResource child = resourceCtx.getResource(ChildResource.class);
            return child.get();
        }

    }

    @Path("child")
    public static class ChildResource {

        private final String name;


        @SuppressWarnings("unused")     // Not used if RESTEASY-1865 is fixed
        public ChildResource() {
            this.name = "I'm new child created on " + new Date();
        }

        public ChildResource(String name) {
            this.name = name;
        }


        @GET
        public String get() {
            return name;
        }

    }

}
