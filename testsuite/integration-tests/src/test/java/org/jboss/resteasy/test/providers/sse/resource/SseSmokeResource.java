package org.jboss.resteasy.test.providers.sse.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

@Path("/sse")
public class SseSmokeResource {
   
   public static final String RESPONSE_STATUS = "responseStatus";

   public static final String RESPONSE_CONTENT_TYPE = "responseContentType";

   public static final String RESPONSE_CONTENT = "responseContent";
   
    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void sentEvents(@Context SseEventSink sseEventSink, @Context Sse sse) {

        try (SseEventSink sink = sseEventSink) {
         sseEventSink.send(sse.newEventBuilder()
                 .name("customObj")
                 .data(new SseSmokeUser("Zeytin", "zeytin@resteasy.org")).build());
        }
    }

    @GET
    @Path("/eventssimple")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void sentSimpleEvents(@Context SseEventSink sseEventSink, @Context Sse sse) {

        try (SseEventSink sink = sseEventSink) {
            sseEventSink.send(sse.newEvent("data"));
        }
    }
    
   @Path("genericResponse")
   @Produces(MediaType.WILDCARD)
   @GET
   public Response generateResponse(@QueryParam(RESPONSE_STATUS) Status responseStatus,
         @QueryParam(RESPONSE_CONTENT_TYPE) MediaType responseContentType,
         @QueryParam(RESPONSE_CONTENT) String responseContent)
   {
      ResponseBuilder responseBuilder = Response.status(responseStatus).type(responseContentType);
      responseBuilder.type(responseContentType);
      if (responseContent != null)
      {
         responseBuilder.entity(responseContent);
      }
      return responseBuilder.build();
   }
   
}
