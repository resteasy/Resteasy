package org.jboss.resteasy.test.core.servlet;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import javax.ws.rs.client.ClientBuilder;
import org.jboss.resteasy.test.core.servlet.resource.ServletConfigApplication;
import org.jboss.resteasy.test.core.servlet.resource.ServletConfigException;
import org.jboss.resteasy.test.core.servlet.resource.ServletConfigExceptionMapper;
import org.jboss.resteasy.test.core.servlet.resource.ServletConfigResource;
import org.jboss.resteasy.spi.HttpResponseCodes;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

/**
 * @tpSubChapter Configuration
 * @tpChapter Integration tests
 * @tpTestCaseDetails Regression test for RESTEASY-381, RESTEASY-518 and RESTEASY-582. Check ServletConfig instance.
 * @tpSince RESTEasy 3.0.16
 */
@RunWith(Arquillian.class)
@RunAsClient
public class ServletConfigTest {
   private static ResteasyClient client;

   @Deployment
   public static Archive<?> deploySimpleResource() {
      WebArchive war = ShrinkWrap.create(WebArchive.class, ServletConfigTest.class.getSimpleName() + ".war");
      war.addAsWebInfResource(ServletConfigTest.class.getPackage(), "ServletConfigWeb.xml", "web.xml");
      war.addClasses(ServletConfigException.class, ServletConfigExceptionMapper.class,
            ServletConfigApplication.class, ServletConfigResource.class);
      return war;
   }

   private String generateURL(String path) {
      return PortProviderUtil.generateURL(path, ServletConfigTest.class.getSimpleName());
   }

   @Before
   public void setup() {
      client = (ResteasyClient)ClientBuilder.newClient();
   }

   @After
   public void after() throws Exception {
      client.close();
   }

   /**
    * @tpTestDetails Regression test for RESTEASY-381
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testCount() throws Exception {
      String count = client.target(generateURL("/my/application/count")).request().get(String.class);
      Assert.assertEquals("Wrong count of RESTEasy application", "1", count);
   }

   /**
    * @tpTestDetails Regression test for RESTEASY-518
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testNullJaxb() throws Exception {
      Response response = client.target(generateURL("/my/null")).request().header("Content-Type", "application/xml").post(Entity.text(""));
      Assert.assertEquals(HttpResponseCodes.SC_BAD_REQUEST, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Regression test for RESTEASY-582
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testBadMediaTypeNoSubtype() throws Exception {
      Response response = client.target(generateURL("/my/application/count")).request().accept("text").get();
      Assert.assertEquals(HttpResponseCodes.SC_BAD_REQUEST, response.getStatus());
      response.close();

      response = client.target(generateURL("/my/application/count")).request().accept("text/plain; q=bad").get();
      Assert.assertEquals(HttpResponseCodes.SC_BAD_REQUEST, response.getStatus());
      response.close();
   }
}
