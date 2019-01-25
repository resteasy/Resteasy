package org.jboss.resteasy.test.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class ClientResponseWithEntityTest {

   @XmlRootElement
   public static class Message {
      private String message;

      public Message() {
      }

      public String getMessage() {
         return this.message;
      }

      public void setMessage(String message) {
         this.message = message;
      }
   }

   @Path("echo")
   @Produces(MediaType.APPLICATION_XML)
   public static class EchoResource {

      @GET
      public Response echo(@QueryParam("msg") String msg) {
         Message message = new Message();
         message.setMessage(String.valueOf(msg));
         return Response.ok(message).build();
      }

   }

   private static Client client;
   private static final String DEP = "ClientResponseWithEntityTest";

   @Deployment
   public static Archive<?> deploy() {
      WebArchive war = TestUtil.prepareArchive(DEP);
      war.addClass(Message.class);
      war.addClass(EchoResource.class);
      return TestUtil.finishContainerPrepare(war, null, EchoResource.class);
   }

   @BeforeClass
   public static void setup() {
      client = ClientBuilder.newClient();
   }

   @AfterClass
   public static void cleanup() {
      client.close();
   }

   private static String generateURL() {
      return PortProviderUtil.generateBaseUrl(DEP);
   }

   @Test
   public void Should_ReturnEntity_When_NoNull() throws Exception {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);
      try (ClientResponse response = (ClientResponse) request.get()) {
         Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
         Assert.assertTrue(response.hasEntity());
         Assert.assertNotNull(response.getEntity());
         Assert.assertNotNull(response.getEntityClass());
      }
   }

   @Test(expected = IllegalStateException.class)
   public void Should_ThrowIllegalStateException_When_EntityIsConsumed() throws Exception {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);
      try (ClientResponse response = (ClientResponse) request.get()) {
         Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
         Assert.assertTrue(response.hasEntity());
         InputStream entityStream = (InputStream) response.getEntity();
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         byte[] buffer = new byte[1024];
         int wasRead = 0;
         do {
            wasRead = entityStream.read(buffer);
            if (wasRead > 0) {
               baos.write(buffer, 0, wasRead);
            }
         } while (wasRead > -1);
         response.getEntity();
      }
   }

   /**
    *
    * According to {@link Response#getEntity()} java doc if the entity was previously fully consumed as an {@link InputStream input stream}
    * an {@link IllegalStateException} MUST be thrown.
    *
    * @throws IOException
    */
   @Test
   public void getEntity_Should_ThrowIllegalStateException_When_EntityIsInputStream_And_IsFullyConsumed() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //entity retrieved as an input stream using response.getEntity() and then fully consumed => response.getEntity() MUST throw an IllegalStateException
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         //Fully consumed the original response stream
         while (((InputStream) response.getEntity()).read() != -1)
         {
         }
         try
         {
            response.getEntity();
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
      }

      //entity retrieved as an input stream using response.readEntity(InputStream.class) and then fully consumed => response.getEntity() MUST throw an IllegalStateException
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         //Fully consumed the original response stream
         while (response.readEntity(InputStream.class).read() != -1)
         {
         }
         try
         {
            response.getEntity();
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
      }
   }

   @Test
   public void getEntity_Should_ReturnEntity_When_EntityIsInputStream_And_IsNotFullyConsumed() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //entity retrieved as an input stream using response.getEntity() and then partially consumed => response.getEntity() MUST return input stream
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         InputStream entityStream = (InputStream) response.getEntity();
         //Let consume a part of the entity stream
         Assert.assertTrue(-1 != ((InputStream) response.getEntity()).read());
         Assert.assertTrue(InputStream.class.isInstance(response.getEntity()));
      }

      //entity retrieved as an input stream using response.readEntity(InputStream.class) and then partially consumed => response.getEntity() MUST return input stream
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         //Let consume a part of the entity stream
         Assert.assertTrue(-1 != response.readEntity(InputStream.class).read());
         Assert.assertTrue(InputStream.class.isInstance(response.getEntity()));
      }
   }

   /**
    * According to {@link Response#bufferEntity()} java doc, if the response entity instance is not backed by an unconsumed input stream, the method invocation is
    * ignored and the method MUST returns false.
    *
    * @throws IOException
    */
   @Test
   public void bufferEntity_Should_ReturnFalse_When_EntityInputStreamIsNotUnconsumed() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //entity retrieved as an input stream using response.getEntity() and then consumed => response.bufferEntity() MUST return false
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(-1 != ((InputStream) response.getEntity()).read());
         Assert.assertFalse(response.bufferEntity());
      }

      //entity retrieved as an input stream using response.readEntity(InputStream.class) and then consumed => response.bufferEntity() MUST return false
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(-1 != response.readEntity(InputStream.class).read());
         Assert.assertFalse(response.bufferEntity());
      }
   }

   @Test
   public void bufferEntity_Should_ReturnTrue_When_EntityInputStreamIsUnconsumed()
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //entity retrieved as an input stream using response.getEntity() and not consumed => response.bufferEntity() MUST return true
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(InputStream.class.isInstance(response.getEntity()));
         Assert.assertTrue(response.bufferEntity());
      }

      //entity retrieved as an input stream using response.readEntity(InputStream.class) and not consumed => response.bufferEntity() MUST return true
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
         Assert.assertTrue(response.bufferEntity());
      }
   }

   /**
    * <p>
    * According to the java doc of:
    * <ul>
    * <li>{@link Response#readEntity(Class)}</li>
    * <li>{@link Response#readEntity(javax.ws.rs.core.GenericType)}</li>
    * <li>{@link Response#readEntity(Class, Annotation[])}</li>
    * <li>{@link Response#readEntity(javax.ws.rs.core.GenericType, Annotation[])}</li>
    * </ul>
    * those methods are supposed to close the original entity input stream (unless the supplied type is input stream)
    * and then cache the result for subsequent retrievals via {@link Response#getEntity()}.
    * </p>
    * <p>
    * So it clearly means that those methods MUST not {@link Response#close() close()} the response.<br>
    * Else subsequent retrievals via {@link Response#getEntity()} will always end up with {@link IllegalStateException} being thrown.
    * </p>
    */
   @Test
   public void readEntity_Should_NotCloseTheResponse_Once_EntityIsRead()
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //Entity read successfully
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
         try
         {
            Assert.assertEquals(entity, response.getEntity());
         }
         catch (IllegalStateException e)
         {
            Assert.fail("The response was not supposed to be closed");
         }
      }

      //Entity read unsuccessfully
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         try
         {
            response.readEntity(Date.class);
            Assert.fail("The content of the message was not supposed to be mapped to a Date");
         }
         catch (ProcessingException e)
         {
         }
         try
         {
            Assert.assertTrue(response.hasEntity());
         }
         catch (IllegalStateException e)
         {
            Assert.fail("The response was not supposed to be closed");
         }
      }

   }

   /**
    * <p>
    * According to the java doc of:
    * <ul>
    * <li>{@link Response#readEntity(Class)}</li>
    * <li>{@link Response#readEntity(javax.ws.rs.core.GenericType)}</li>
    * <li>{@link Response#readEntity(Class, Annotation[])}</li>
    * <li>{@link Response#readEntity(javax.ws.rs.core.GenericType, Annotation[])}</li>
    * </ul>
    * Subsequent call to one of those methods MUST throw an {@link IllegalStateException} if the original entity input stream has already been
    * fully consumed without buffering the entity data prior consuming.
    * </p>
    *
    * @throws IOException
    */
   @Test
   public void readEntity_Should_ThrowIllegalStateException_When_EntityInputStream_IsFullyConsumed() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //without buffering => second call to response.readEntity(Message.class)/response.readEntity(InputStream.class) => throw an IllegalStateException
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
         try
         {
            response.readEntity(Message.class);
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
         try
         {
            response.readEntity(InputStream.class);
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
      }

      //with buffering => second call to response.readEntity(Message.class)/response.readEntity(InputStream.class) => OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(response.bufferEntity());
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
         entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
      }
   }

   @Test
   public void readEntity_Should_ThrowIllegalStateException_When_EntityInputStream_IsFullyConsumed_2() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //without buffering, input stream retrieved using response.getEntity() and then fully consumed => call to response.readEntity(Message.class)/response.readEntity(InputStream.class) throws an IllegalStateException
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         InputStream entityStream = (InputStream) response.getEntity();
         //Fully consume the original response stream
         while (entityStream.read() != -1)
         {
         }
         try
         {
            response.readEntity(Message.class);
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
         try
         {
            response.readEntity(InputStream.class);
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
      }

      //with buffering, input stream retrieved using response.getEntity() and then fully consumed => call to response.readEntity(Message.class)/response.readEntity(InputStream.class) is OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(response.bufferEntity());
         InputStream entityStream = (InputStream) response.getEntity();
         //Fully consumed the original response stream
         while (entityStream.read() != -1)
         {
         }
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
      }
   }

   @Test
   public void readEntity_Should_ThrowIllegalStateException_When_EntityInputStream_IsFullyConsumed_3() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //without buffering, input stream retrieved using response.readEntity(InputStream.class) and then fully consumed => call to response.readEntity(Message.class)/response.readEntity(InputStream.class) throws an IllegalStateException
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         InputStream entityStream = response.readEntity(InputStream.class);
         //Fully consume the original response stream
         while (entityStream.read() != -1)
         {
         }
         try
         {
            response.readEntity(Message.class);
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
         try
         {
            response.readEntity(InputStream.class);
            Assert.fail("An IllegalStateException was expected.");
         }
         catch (Exception e)
         {
            Assert.assertTrue(IllegalStateException.class.isInstance(e));
            // Following is to be sure that previous IllegalStateException is not because of closed response
            checkResponseNotClosed(response);
         }
      }

      //with buffering, input stream retrieved using response.readEntity(InputStream.class) and then fully consumed => call to response.readEntity(Message.class)/response.readEntity(InputStream.class) is OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(response.bufferEntity());
         InputStream entityStream = response.readEntity(InputStream.class);
         //Fully consumed the original response stream
         while (entityStream.read() != -1)
         {
         }
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
      }
   }

   @Test
   public void readEntity_Should_ReturnEntityInputStream_When_EntityInputStream_IsNotFullyConsumed() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //input stream retrieved using response.getEntity() and then partially consumed => calls to response.readEntity(InputStream.class) is OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         InputStream entityStream = (InputStream) response.getEntity();
         Assert.assertTrue(-1 != entityStream.read());
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
      }

      //input stream retrieved using response.getEntity() and not consumed => calls to response.readEntity(InputStream.class) and response.readEntity(Message.class) are OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(InputStream.class.isInstance(response.getEntity()));
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
      }
   }

   @Test
   public void readEntity_Should_ReturnEntityInputStream_When_EntityInputStream_IsNotFullyConsumed_2() throws IOException
   {
      Invocation.Builder request = client.target(generateURL()).path("echo").queryParam("msg", "Hello world")
            .request(MediaType.APPLICATION_XML_TYPE);

      //input stream retrieved using response.readEntity(InputStream.class) and then partially consumed => calls to response.readEntity(InputStream.class) is OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         InputStream entityStream = response.readEntity(InputStream.class);
         Assert.assertTrue(-1 != entityStream.read());
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
      }

      //input stream retrieved using response.readEntity(InputStream.class) and not consumed => calls to response.readEntity(InputStream.class) and response.readEntity(Message.class) are OK
      try (Response response = request.get();)
      {
         Assert.assertTrue(response.hasEntity());
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
         Assert.assertTrue(InputStream.class.isInstance(response.readEntity(InputStream.class)));
         Message entity = response.readEntity(Message.class);
         Assert.assertEquals("Hello world", entity.message);
      }
   }

   private void checkResponseNotClosed(Response response)
   {
      try
      {
         Assert.assertTrue(response.hasEntity());
      }
      catch (IllegalStateException e2)
      {
         Assert.fail("The response was not supposed to be closed");
      }
   }

}
