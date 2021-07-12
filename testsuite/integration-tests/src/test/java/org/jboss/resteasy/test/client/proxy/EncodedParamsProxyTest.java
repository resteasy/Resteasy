package org.jboss.resteasy.test.client.proxy;

import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import javax.ws.rs.client.ClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.test.client.proxy.resource.EncodedParamsProxyInterface;
import org.jboss.resteasy.test.client.proxy.resource.EncodedParamsProxyResource;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @tpSubChapter Resteasy-client
 * @tpChapter Integration tests
 * @tpTestCaseDetails Regression test for RESTEASY-1475 and RESTEASY-2068.
 * @tpSince RESTEasy 3.1.4
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EncodedParamsProxyTest {
   private static ResteasyClient client;

   @BeforeClass
   public static void before() throws Exception {
      client = (ResteasyClient)ClientBuilder.newClient();
   }

   @AfterClass
   public static void after() throws Exception {
      client.close();
   }

   @Deployment
   public static Archive<?> deployUriInfoSimpleResource() {
      WebArchive war = TestUtil.prepareArchive(EncodedParamsProxyTest.class.getSimpleName());
      war.addClasses(EncodedParamsProxyInterface.class);
      return TestUtil.finishContainerPrepare(war, null, EncodedParamsProxyResource.class);
   }

   private static String generateBaseUrl() {
      return PortProviderUtil.generateBaseUrl(EncodedParamsProxyTest.class.getSimpleName());
   }

   /**
    * @tpTestDetails Verify "/" in "t;hawkular/f;jk-feed?query=100%-%25ig" is sent encoded
    * @tpSince RESTEasy 3.1.4
    */
   @Test
   public void testEncodeProxy() throws Exception
   {
      ResteasyWebTarget target = client.target(generateBaseUrl());
      EncodedParamsProxyInterface proxy = target.proxy(EncodedParamsProxyInterface.class);
      Response response = proxy.encode("t;hawkular/f%3Bjk-feed", "100%-%25ig", null);
      Assert.assertEquals(200, response.getStatus());
      String uri = response.readEntity(String.class);
      Assert.assertEquals(generateBaseUrl() + "/test/encode/t;hawkular%2Ff%3Bjk-feed?query=100%25-%25ig", uri);
   }

   /**
    * @tpTestDetails Verify "/" in "t;hawkular/f;jk-feed?100-%ig ?" is sent unencoded
    * @tpSince RESTEasy 3.1.4
    */
   @Test
   public void testNoencodeProxy() throws Exception
   {
      ResteasyWebTarget target = client.target(generateBaseUrl());
      EncodedParamsProxyInterface proxy = target.proxy(EncodedParamsProxyInterface.class);
      Response response = proxy.noencode("t;hawkular/f%3Bjk-feed", "100%-%25ig", null);
      Assert.assertEquals(200, response.getStatus());
      String uri = response.readEntity(String.class);
      Assert.assertEquals(generateBaseUrl() + "/test/noencode/t;hawkular/f%253Bjk-feed?query=100%25-%2525ig", uri);
   }

   /**
    * @tpTestDetails Verify matrix parameter values are sent encoded
    * @tpSince RESTEasy 4.0.0
    */
   @Test
   public void testMatrixEncodeProxy() throws Exception
   {
      ResteasyWebTarget target = client.target(generateBaseUrl());
      EncodedParamsProxyInterface proxy = target.proxy(EncodedParamsProxyInterface.class);
      Response response = proxy.encodeMatrix("t;hawkular/f%3Bjk-feed", "100%-%25ig", null);
      Assert.assertEquals(200, response.getStatus());
      String uri = response.readEntity(String.class);
      Assert.assertEquals(generateBaseUrl() + "/test/encode-matrix;matrix1=t%3Bhawkular%2Ff%3Bjk-feed;matrix2=100%25-%25ig", uri);
   }

   /**
    * @tpTestDetails Verify matrix parameter values are sent unencoded
    * @tpSince RESTEasy 4.0.0
    */
   @Test
   public void testMatrixNoencodeProxy() throws Exception
   {
      ResteasyWebTarget target = client.target(generateBaseUrl());
      EncodedParamsProxyInterface proxy = target.proxy(EncodedParamsProxyInterface.class);
      Response response = proxy.noencodeMatrix("t;hawkular/f%3Bjk-feed", "100%-%25ig", null);
      Assert.assertEquals(200, response.getStatus());
      String uri = response.readEntity(String.class);
      Assert.assertEquals(generateBaseUrl() + "/test/noencode-matrix;matrix1=t%3Bhawkular%2Ff%3Bjk-feed;matrix2=100%25-%25ig", uri);
   }
}
