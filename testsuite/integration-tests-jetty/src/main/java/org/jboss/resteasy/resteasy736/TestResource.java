package org.jboss.resteasy.resteasy736;

import org.jboss.logging.Logger;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 *
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version $Revision: 1.1 $
 *
 * Copyright Aug 15, 2012
 */
@Path("/")
@Produces("text/plain")
public class TestResource
{

   private static final Logger LOG = Logger.getLogger(TestResource.class);

   @GET
   @Path("test")
   public void test(final @Suspended AsyncResponse response)
   {
      response.setTimeout(5000, TimeUnit.MILLISECONDS);
      Thread t = new Thread()
      {
         @Override
         public void run()
         {
            try
            {
               LOG.info("TestResource: async thread started");
               Thread.sleep(10000);
               Response jaxrs = Response.ok("test").type(MediaType.TEXT_PLAIN).build();
               response.resume(jaxrs);
               LOG.info("TestResource: async thread finished");
            }
            catch (Exception e)
            {
               LOG.error(e.getMessage(), e);
            }
         }
      };
      t.start();
   }

   @GET
   @Path("default")
   public void defaultTest(final @Suspended AsyncResponse response)
   {
      Thread t = new Thread()
      {
         @Override
         public void run()
         {
            try
            {
               LOG.info("TestResource: async thread started");
               Thread.sleep(35000); // Jetty async timeout defaults to 30000.
               Response jaxrs = Response.ok("test").type(MediaType.TEXT_PLAIN).build();
               response.resume(jaxrs);
               LOG.info("TestResource: async thread finished");
            }
            catch (Exception e)
            {
               LOG.error(e.getMessage(), e);
            }
         }
      };
      t.start();
   }
}
