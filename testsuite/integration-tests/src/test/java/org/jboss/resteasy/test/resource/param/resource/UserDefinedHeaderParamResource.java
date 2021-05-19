package org.jboss.resteasy.test.resource.param.resource;

import org.jboss.resteasy.test.resource.param.UserDefinedHeaderParamTest;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

public class UserDefinedHeaderParamResource implements UserDefinedHeaderParamTest.UserHeaderParamInterface {

    @Context
    private HttpHeaders httpHeaders;

    @GET
    @Path("/header")
    public String sendHeaderFirst(@HeaderParam("Content-Type") String contentType, String text) {
        return httpHeaders.getHeaderString("Content-Type");
    }

    @GET
    @Path("/header")
    public String sendTextFirst(String text, @HeaderParam("Content-Type") String contentType) {
        return httpHeaders.getHeaderString("Content-Type");
    }

    @GET
    @Path("/header")
    public String sendDefaultType(String text) {
        return httpHeaders.getHeaderString("Content-Type");
    }

    @GET
    @Path("/header")
    public String sendMultipleTypes(String text, @HeaderParam("Content-Type") String contentType,
                                    @HeaderParam("Content-Type") String secondContentType,
                                    @HeaderParam("Content-Type") String thirdContentType) {
        return httpHeaders.getHeaderString("Content-Type");
    }
}
