package org.jboss.resteasy.test.response;

import java.net.InetAddress;
import java.util.concurrent.Future;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import javax.ws.rs.client.ClientBuilder;
import org.jboss.resteasy.test.response.resource.AsyncResponseCallback;
import org.jboss.resteasy.test.response.resource.CompletionStageProxy;
import org.jboss.resteasy.test.response.resource.CompletionStageResponseMessageBodyWriter;
import org.jboss.resteasy.test.response.resource.CompletionStageResponseResource;
import org.jboss.resteasy.test.response.resource.CompletionStageResponseTestClass;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @tpSubChapter CompletionStage response type
 * @tpChapter Integration tests
 * @tpSince RESTEasy 3.5
 */
@RunWith(Arquillian.class)
@RunAsClient
public class CompletionStageResponseTest {

   static boolean serverIsLocal;
   static ResteasyClient client;

   @Deployment
   public static Archive<?> deploy() {
      WebArchive war = TestUtil.prepareArchive(CompletionStageResponseTest.class.getSimpleName());
      war.addClass(CompletionStageResponseTestClass.class);
      war.addClass(CompletionStageProxy.class);
      war.setManifest(new StringAsset("Manifest-Version: 1.0\n"
              + "Dependencies: org.jboss.resteasy.resteasy-rxjava2 services, org.reactivestreams\n"));
      return TestUtil.finishContainerPrepare(war, null, CompletionStageResponseMessageBodyWriter.class,
            CompletionStageResponseResource.class, SingleProvider.class,
            AsyncResponseCallback.class);
   }

   private static String generateURL(String path) {
      return PortProviderUtil.generateURL(path, CompletionStageResponseTest.class.getSimpleName());
   }

   @BeforeClass
   public static void setup() throws Exception {
      client = (ResteasyClient)ClientBuilder.newClient();

      // Undertow's default behavior is to send an HTML error page only if the client and
      // server are communicating on a loopback connection. Otherwise, it returns "".
      Invocation.Builder request = client.target(generateURL("/host")).request();
      Response response = request.get();
      String host = response.readEntity(String.class);
      InetAddress addr = InetAddress.getByName(host);
      serverIsLocal = addr.isLoopbackAddress();
   }

   @AfterClass
   public static void close() {
      client.close();
      client = null;
   }


   /**
    * @tpTestDetails Resource method returns CompletionStage<String>.
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testText() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/text")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals(CompletionStageResponseResource.HELLO, entity);

      // make sure the completion callback was called with no error
      request = client.target(generateURL("/callback-called-no-error?p=text")).request();
      response = request.get();
      Assert.assertEquals(200, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Resource method returns CompletableFuture<String>.
    * @tpSince RESTEasy 4.7
    */
   @Test
   public void testCompletabeFutureText() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/cftext")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals(CompletionStageResponseResource.HELLO, entity);

      // make sure the completion callback was called with no error
      request = client.target(generateURL("/callback-called-no-error?p=cftext")).request();
      response = request.get();
      Assert.assertEquals(200, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Resource method returns CompletionStage<Response>.
    * Response has MediaType "text/plain" overriding @Produces("text/xxx").
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testResponse() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/response")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals("text/plain;charset=UTF-8", response.getHeaderString("Content-Type"));
      Assert.assertEquals(CompletionStageResponseResource.HELLO, entity);
   }

   /**
    * @tpTestDetails Resource method returns CompletionStage<CompletionStageResponseTestClass>,
    * where CompletionStageResponseTestClass is handled by CompletionStageResponseMessageBodyWriter,
    * which has annotation @Produces("abc/xyz").
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testTestClass() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/testclass")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals("abc/xyz", response.getHeaderString("Content-Type"));
      Assert.assertEquals("pdq", entity);
   }

   /**
    * @tpTestDetails Resource method returns CompletionStage<Response>, where the Response
    * emtity is a CompletionStageResponseTestClass, and where
    * CompletionStageResponseTestClass is handled by CompletionStageResponseMessageBodyWriter,
    * which has annotation @Produces("abc/xyz").
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testResponseTestClass() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/responsetestclass")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals("abc/xyz", response.getHeaderString("Content-Type"));
      Assert.assertEquals("pdq", entity);
   }

   /**
    * @tpTestDetails Resource method return type is CompletionStage<String>, and it passes
    * null to CompleteableFuture.complete().
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testNull() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/null")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(204, response.getStatus());
      Assert.assertEquals(null, entity);
   }

   /**
    * @tpTestDetails Resource method passes a WebApplicationException to
    * to CompleteableFuture.completeExceptionally().
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testExceptionDelay() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/exception/delay")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(444, response.getStatus());
      Assert.assertEquals(CompletionStageResponseResource.EXCEPTION, entity);

      // make sure the completion callback was called with with an error
      request = client.target(generateURL("/callback-called-with-error?p=exception/delay")).request();
      response = request.get();
      Assert.assertEquals(200, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Resource method throws a WebApplicationException in a CompletionStage
    * pipeline
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testExceptionDelayWrapped() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/exception/delay-wrapped")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(444, response.getStatus());
      Assert.assertEquals(CompletionStageResponseResource.EXCEPTION, entity);

      // make sure the completion callback was called with with an error
      request = client.target(generateURL("/callback-called-with-error?p=exception/delay-wrapped")).request();
      response = request.get();
      Assert.assertEquals(200, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Resource method return type is CompletionStage<String>, but it
    * throws a RuntimeException without creating a CompletionStage.
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testExceptionImmediateRuntime() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/exception/immediate/runtime")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(500, response.getStatus());
      response.close();

      // make sure the completion callback was called with with an error
      request = client.target(generateURL("/callback-called-with-error?p=exception/immediate/runtime")).request();
      response = request.get();
      Assert.assertEquals(200, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Resource method return type is CompletionStage<String>, but it
    * throws an Exception without creating a CompletionStage.
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testExceptionImmediateNotRuntime() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/exception/immediate/notruntime")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(500, response.getStatus());
      response.close();

      // make sure the completion callback was called with with an error
      request = client.target(generateURL("/callback-called-with-error?p=exception/immediate/notruntime")).request();
      response = request.get();
      Assert.assertEquals(200, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Resource method returns CompletionStage<String>.
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void testTextSingle() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/textSingle")).request();
      Response response = request.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals(CompletionStageResponseResource.HELLO, entity);
   }

   /**
    * @tpTestDetails Resource method returns CompletionStage<String>, data are computed after end-point method ends
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void getDataWithDelayTest() throws Exception
   {
      Invocation.Builder request = client.target(generateURL("/sleep")).request();
      Future<Response> future = request.async().get();
      Assert.assertFalse(future.isDone());
      Response response = future.get();
      String entity = response.readEntity(String.class);
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals(CompletionStageResponseResource.HELLO, entity);
   }


   /**
    * @tpTestDetails Resource method returns CompletionStage<String>, client try to use proxy
    *                Regression check for https://issues.jboss.org/browse/RESTEASY-1798
    *                                       - RESTEasy proxy client can't use RxClient and CompletionStage
    * @tpSince RESTEasy 3.5
    */
   @Test
   public void proxyTest() throws Exception
   {
      CompletionStageProxy proxy = client.target(generateURL("/")).proxy(CompletionStageProxy.class);
      Future<String> future = proxy.sleep().toCompletableFuture();
      Assert.assertFalse(future.isDone());
      Assert.assertEquals(CompletionStageResponseResource.HELLO, future.get());
   }


}
