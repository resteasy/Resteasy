package org.jboss.resteasy.test.client.proxy.resource;

import org.jboss.resteasy.test.client.proxy.ProxyPathParamRegexTest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Encoded;
import javax.ws.rs.core.MediaType;

@Path("")
public class ProxyPathParamRegexResource implements ProxyPathParamRegexTest.RegexInterface {
    @GET
    @Path("/{path}/{string:[a-z]?}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getPath(@PathParam("path") String path, @PathParam("string") @Encoded String string) {
        String responseString = path + string;
        return responseString;
    }
}