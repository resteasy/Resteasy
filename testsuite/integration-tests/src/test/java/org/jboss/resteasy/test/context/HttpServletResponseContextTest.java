package org.jboss.resteasy.test.context;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.test.context.resource.HttpServletResponseContextResource;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @tpSubChapter Jaxrs implementation
 * @tpChapter Integration tests
 * @tpTestCaseDetails RESTEASY-1531
 * @tpSince RESTEasy 3.1.0
 */
@RunWith(Arquillian.class)
@RunAsClient
public class HttpServletResponseContextTest
{
   @Deployment
   public static Archive<?> deploy()
   {
      WebArchive war = TestUtil.prepareArchive(HttpServletResponseContextTest.class.getSimpleName());
      return TestUtil.finishContainerPrepare(war, null, HttpServletResponseContextResource.class);
   }

   private String generateURL(String path) {
      return PortProviderUtil.generateURL(path, HttpServletResponseContextTest.class.getSimpleName());
   }

   /**
    * @tpTestDetails Tests that ResteasyClientBuilder implementation corresponds to JAXRS spec ClientBuilder. Tested client
    * is bundled in the server.
    * @tpSince RESTEasy 3.1.0
    */
   @Test
   public void test() {
      Client client = ClientBuilder.newClient();
      Builder request = client.target(generateURL("/test")).request();
      Response response = request.get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals("context", response.readEntity(String.class));
   }
}
