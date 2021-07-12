package org.jboss.resteasy.test.common;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class DispatcherResponseUtils {
    
    @Path("/unsafe")
    public static class MockUnsafeResource {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public Response get() {
            return Response.ok().entity(new UnsafeDto()).build();
        }
    }
    
    public static class UnsafeDto {
        private String typeCode;
        
        public String getTypeDescription() {
            // Causes NPE during jackson's serialization
            if (this.typeCode.equals("not empty")) {
                return "NOT EMPTY";
            } else {
                return this.typeCode;
            }
        }
    }
    
    public static class JsonSerializationExceptionMapper implements ExceptionMapper<Exception> {
        @Override
        public Response toResponse(Exception e) {
            Map<String, String> errorDetails = new HashMap<>();
            errorDetails.put("ARQ-502", "JSON Serialization Error");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorDetails)
                    .type(MediaType.APPLICATION_JSON).build();
        }
    }
    
}
