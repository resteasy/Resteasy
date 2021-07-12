package org.jboss.resteasy.test.client.proxy.resource;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("test")
public class EncodedParamsProxyResource implements EncodedParamsProxyInterface {

   // Note. @Encoded isn't placed on path parameter: it is used on the client side
   //       by the proxy mechanism.
   @Override
   public Response encode(String path, String query, UriInfo uri) {
      return Response.ok(uri.getRequestUri().toString()).build();
   }

   @Override
   public Response noencode(String path, String query, UriInfo uri) {
      return Response.ok(uri.getRequestUri().toString()).build();
   }

   @Override
   public Response encodeMatrix(String matrix1, String matrix2, UriInfo uri) {
      return Response.ok(uri.getRequestUri().toString()).build();
   }

   @Override
   public Response noencodeMatrix(String matrix1, String matrix2, UriInfo uri) {
      return Response.ok(uri.getRequestUri().toString()).build();
   }
}
