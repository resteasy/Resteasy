package org.jboss.resteasy.test.client.proxy.resource;

import javax.ws.rs.Encoded;
import javax.ws.rs.GET;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("test")
public interface EncodedParamsProxyInterface
{
   @GET
   @Path("/encode/{path:.+}")
   Response encode(@Encoded @PathParam("path") String path, @Encoded @QueryParam("query") String query, @Context UriInfo info);

   @GET
   @Path("/noencode/{path:.+}")
   Response noencode(@PathParam("path") String path, @QueryParam("query") String query, @Context UriInfo info);

   @GET
   @Path("/encode-matrix")
   Response encodeMatrix(@Encoded @MatrixParam("matrix1") String matrix1, @Encoded @MatrixParam("matrix2") String matrix2, @Context UriInfo info);

   @GET
   @Path("/noencode-matrix")
   Response noencodeMatrix(@MatrixParam("matrix1") String matrix1, @MatrixParam("matrix2") String matrix2, @Context UriInfo info);
}
