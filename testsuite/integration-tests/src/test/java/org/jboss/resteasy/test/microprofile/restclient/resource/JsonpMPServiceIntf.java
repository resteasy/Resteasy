package org.jboss.resteasy.test.microprofile.restclient.resource;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.inject.Singleton;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@RegisterRestClient(baseUri ="http://localhost:8080/jsonP_service")
@Path("/jsonpService")
@Singleton
public interface JsonpMPServiceIntf {
    @Path("array")
    @POST
    @Produces("application/json")
    @Consumes("application/json")
    JsonArray array(JsonArray array);

    @Path("object")
    @POST
    @Produces("application/json")
    @Consumes("application/json")
    JsonObject object(JsonObject obj);

    @Path("structure")
    @POST
    @Produces("application/json")
    @Consumes("application/json")
    JsonStructure object(JsonStructure struct);

    @Path("number")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    JsonNumber testNumber(JsonNumber number);

    @Path("string")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    JsonString testString(JsonString string);
}
