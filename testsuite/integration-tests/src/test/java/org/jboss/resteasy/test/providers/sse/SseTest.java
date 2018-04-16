package org.jboss.resteasy.test.providers.sse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import javax.xml.bind.JAXBElement;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.sse.client.SseEventSourceImpl;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@RunAsClient
public class SseTest
{

   private final static Logger logger = Logger.getLogger(SseTest.class);

   @Deployment
   public static Archive<?> deploy()
   {
      WebArchive war = TestUtil.prepareArchive(SseTest.class.getSimpleName());
      war.addClass(SseTest.class);
      war.addAsWebInfResource("org/jboss/resteasy/test/providers/sse/web.xml", "web.xml");
      war.addAsWebResource("org/jboss/resteasy/test/providers/sse/index.html", "index.html");
      war.addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
      return TestUtil.finishContainerPrepare(war, null, SseApplication.class, GreenHouse.class, SseResource.class,
            AnotherSseResource.class, EscapingSseResource.class, ExecutorServletContextListener.class);
   }

   private String generateURL(String path)
   {
      return PortProviderUtil.generateURL(path, SseTest.class.getSimpleName());
   }

   @Test
   @InSequence(1)
   public void testAddMessage() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(5);
      final AtomicInteger errors = new AtomicInteger(0);
      final List<String> results = new ArrayList<String>();
      final List<String> sent = new ArrayList<String>();
      Client client = ClientBuilder.newBuilder().build();
      WebTarget target = client.target(generateURL("/service/server-sent-events"));
      SseEventSource msgEventSource = SseEventSource.target(target).build();
      try (SseEventSource eventSource = msgEventSource)
      {
         Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
         eventSource.register(event -> {
            results.add(event.readData(String.class));
            latch.countDown();
         }, ex -> {
            errors.incrementAndGet();
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
         });
         eventSource.open();

         Client messageClient = new ResteasyClientBuilder().connectionPoolSize(10).build();
         WebTarget messageTarget = messageClient.target(generateURL("/service/server-sent-events"));
         for (int counter = 0; counter < 5; counter++)
         {
            String msg = "message " + counter;
            sent.add(msg);
            messageTarget.request().post(Entity.text(msg));
         }
         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertEquals(0, errors.get());
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
         messageTarget.request().delete();
         messageClient.close();
      }
      Assert.assertFalse("SseEventSource is not closed", msgEventSource.isOpen());
      Assert.assertTrue("5 messages are expected, but is : " + results.size(), results.size() == 5);
      for (String s : sent)
      {
         Assert.assertTrue("Sent message \"" + s + "\" not found as result.", results.contains(s));
      }
   }

   //Test for Last-Event-Id. This test uses the message items stores in testAddMessage()
   @Test
   @InSequence(2)
   public void testLastEventId() throws Exception
   {
      final CountDownLatch missedEventLatch = new CountDownLatch(3);
      final List<String> missedEvents = new ArrayList<String>();
      WebTarget lastEventTarget = new ResteasyClientBuilder().connectionPoolSize(10).build()
            .target(generateURL("/service/server-sent-events"));
      SseEventSourceImpl lastEventSource = (SseEventSourceImpl) SseEventSource.target(lastEventTarget).build();
      lastEventSource.register(event -> {
         missedEvents.add(event.toString());
         missedEventLatch.countDown();
      }, ex -> {
         throw new RuntimeException(ex);
      });
      lastEventSource.open("1");
      Assert.assertTrue(
            "Waiting for missed events to be delivered has timed our, received events :"
                  + Arrays.toString(missedEvents.toArray(new String[]
                  {})), missedEventLatch.await(30, TimeUnit.SECONDS));
      Assert.assertTrue("3 messages are expected, but is : " + missedEvents.toArray(new String[]
      {}), missedEvents.size() == 3);
      lastEventTarget.request().delete();
      lastEventSource.close();
   }

   @Test
   @InSequence(3)
   public void testSseEvent() throws Exception
   {
      final List<String> results = new ArrayList<String>();
      final CountDownLatch latch = new CountDownLatch(6);
      final AtomicInteger errors = new AtomicInteger(0);
      Client client = new ResteasyClientBuilder().connectionPoolSize(10).build();
      WebTarget target = client.target(generateURL("/service/server-sent-events")).path("domains").path("1");

      SseEventSource eventSource = SseEventSource.target(target).build();
      Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
      eventSource.register(event -> {
         results.add(event.readData());
         latch.countDown();
      }, ex -> {
         errors.incrementAndGet();
         logger.error(ex.getMessage(), ex);
         throw new RuntimeException(ex);
      });
      eventSource.open();

      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertEquals(0, errors.get());
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertTrue("6 SseInboundEvent expected", results.size() == 6);
      Assert.assertTrue("Expect the last event is Done event, but it is :" + results.toArray(new String[]
      {})[5], results.toArray(new String[]
      {})[5].indexOf("Done") > -1);
      eventSource.close();
      client.close();
   }

   @Test
   @InSequence(4)
   public void testBroadcast() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(3);
      Client client = new ResteasyClientBuilder().build();
      WebTarget target = client.target(generateURL("/service/server-sent-events/subscribe"));
      final String textMessage = "This is broadcast message";
      Consumer<InboundSseEvent> checkConsumer = insse -> {
         if (latch.getCount() > 0)
         {
            Assert.assertTrue("Unexpected sever sent event data", textMessage.equals(insse.readData()));
         }
         latch.countDown();
      };
      SseEventSource eventSource = SseEventSource.target(target).build();
      Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
      eventSource.register(checkConsumer);
      eventSource.open();

      Client client2 = new ResteasyClientBuilder().build();
      WebTarget target2 = client2.target(generateURL("/service/sse/subscribe"));

      SseEventSource eventSource2 = SseEventSource.target(target2).build();
      eventSource2.register(checkConsumer);
      eventSource2.open();
      //Test for eventSource subscriber

      client.target(generateURL("/service/server-sent-events/broadcast")).request()
            .post(Entity.entity(textMessage, MediaType.SERVER_SENT_EVENTS));
      client2.target(generateURL("/service/sse/broadcast")).request()
      .post(Entity.entity(textMessage, MediaType.SERVER_SENT_EVENTS));
      Assert.assertTrue("Waiting for broadcast event to be delivered has timed out.", latch.await(20, TimeUnit.SECONDS));

      //one subscriber is closed and test if another subscriber works
      CountDownLatch latch3 = new CountDownLatch(5);
      CountDownLatch latch4 = new CountDownLatch(5);
      eventSource.register(insse -> {
         latch3.countDown();
      });
      eventSource2.register(insse -> {
         latch4.countDown();
      });
      client2.target(generateURL("/service/server-sent-events/broadcast")).request()
            .post(Entity.entity("repeat", MediaType.SERVER_SENT_EVENTS));
      Assert.assertTrue("Waiting for repeatable broadcast events to be delivered has timed out.",
            latch3.await(20, TimeUnit.SECONDS));
      Assert.assertTrue("Waiting for repeatable broadcast events to be delivered has timed out.",
            latch4.await(20, TimeUnit.SECONDS));

      client.close();
      CountDownLatch latch5 = new CountDownLatch(5);
      CountDownLatch latch6 = new CountDownLatch(5);

      eventSource.register(insse -> {
         latch5.countDown();
      });
      eventSource2.register(insse -> {
         latch6.countDown();
      });

      Assert.assertTrue("Eventsource should not receive event after close", latch5.getCount() == 5);
      Assert.assertTrue("Waiting for eventsource2 receive broadcast events to be delivered has timed out.",
            latch6.await(20, TimeUnit.SECONDS));

      client2.target(generateURL("/service/server-sent-events/broadcast")).request()
            .post(Entity.entity("close one subscriber", MediaType.SERVER_SENT_EVENTS));

      Client closeClient = new ResteasyClientBuilder().build();
      WebTarget closeTarget = closeClient.target(generateURL("/service/sse"));
      Assert.assertTrue("Subscribed eventsink is not closed", closeTarget.request().delete().readEntity(Boolean.class));
      eventSource2.close();
   }

   //This test is checking SseEventSource reconnect ability. When request post /addMessageAndDisconnect path, server will 
   //disconnect the connection, but events is continued to add to eventsStore. SseEventSource will automatically reconnect
   //with LastEventId and receive the missed events  
   @Test
   @InSequence(5)
   public void testReconnect() throws Exception
   {
      int proxyPort = 9090;
      SimpleProxyServer proxy = new SimpleProxyServer(PortProviderUtil.getHost(), PortProviderUtil.getPort(), proxyPort);
      proxy.start();
      final CountDownLatch latch = new CountDownLatch(10);
      final List<String> results = new ArrayList<String>();
      final AtomicInteger errors = new AtomicInteger(0);
      ResteasyClient client = new ResteasyClientBuilder().connectionPoolSize(10).build();
      String requestPath = PortProviderUtil.generateURL("/service/server-sent-events",
            SseTest.class.getSimpleName(), PortProviderUtil.getHost(), proxyPort);
      WebTarget target = client.target(requestPath);
      try (SseEventSource eventSource = SseEventSource.target(target).reconnectingEvery(500, TimeUnit.MILLISECONDS).build())
      {
         Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
         eventSource.register(event -> {
            results.add(event.toString());
            latch.countDown();
            if (latch.getCount() == 8)
            {
               new Thread()
               {
                  public void run()
                  {
                     proxy.stop();
                     try
                     {
                        Thread.sleep(300);
                     }
                     catch (Exception e)
                     {
                        logger.error("Exception thrown when sleep some time to start proxy ", e);
                     }
                     proxy.start();
                  }
               }.start();
            }
         }, ex -> {
            errors.incrementAndGet();
            logger.error("test reconnect error", ex);
            throw new RuntimeException(ex);
         });
         eventSource.open();

         Client messageClient = new ResteasyClientBuilder().build();
         WebTarget messageTarget = messageClient
               .target(generateURL("/service/server-sent-events/addMessageAndDisconnect"));
         messageTarget.request().post(Entity.text("msg"));
         messageClient.close();

         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertEquals(0, errors.get());
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
         Assert.assertTrue("10 events are expected, but is : " + results.size(), results.size() == 10);
         target.request().delete();
         proxy.stop();
      }
   }


   @Test
   @InSequence(6)
   public void testEventSourceConsumer() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(1);
      Client client = new ResteasyClientBuilder().connectionPoolSize(10).build();
      WebTarget target = client.target(generateURL("/service/server-sent-events/error"));
      List<Throwable> errorList = new ArrayList<Throwable>();
      Thread t = new Thread(new Runnable()
      {
         @Override
         public void run()
         {
            try (SseEventSource eventSource = SseEventSource.target(target).build())
            {
               eventSource.register(event -> {
                  latch.countDown();
               }, ex -> {
                  if (ex instanceof InternalServerErrorException)
                  {
                     errorList.add(ex);
                     latch.countDown();
                  }
               });
               eventSource.open();
            }
         }
      });
      t.start();
      if (latch.await(30, TimeUnit.SECONDS))
      {
         t.interrupt();
      }
      Assert.assertFalse("InternalServerErrorException isn't processed in error consumer", errorList.isEmpty());
   }

   @Test
   @InSequence(7)
   public void testMultipleDataFields() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(7);
      final AtomicInteger errors = new AtomicInteger(0);
      final SortedSet<String> results = new TreeSet<String>();
      Client client = ClientBuilder.newBuilder().build();
      WebTarget target = client.target(generateURL("/service/server-sent-events"));
      SseEventSource msgEventSource = SseEventSource.target(target).build();
      try (SseEventSource eventSource = msgEventSource)
      {
         Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
         eventSource.register(event -> {
            results.add(event.readData());
            latch.countDown();
         }, ex -> {
            errors.incrementAndGet();
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
         });
         eventSource.open();

         Client messageClient = new ResteasyClientBuilder().connectionPoolSize(10).build();
         WebTarget messageTarget = messageClient.target(generateURL("/service/server-sent-events"));
         messageTarget.request().post(Entity.text("data0a"));
         messageTarget.request().post(Entity.text("data1a\ndata1b\n\rdata1c"));
         messageTarget.request().post(Entity.text("data2a\r\ndata2b"));
         messageTarget.request().post(Entity.text("data3a\n\rdata3b"));
         messageTarget.request().post(Entity.text("data4a\r\ndata4b"));
         messageTarget.request().post(Entity.text("data5a\r\r\r\ndata5b"));
         messageTarget.request().post(Entity.text("data6a\n\n\r\r\ndata6b"));
         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertEquals(0, errors.get());
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
         messageTarget.request().delete();
         messageClient.close();
      }
      Assert.assertFalse("SseEventSource is not closed", msgEventSource.isOpen());
      Assert.assertTrue("5 messages are expected, but is : " + results.size(), results.size() == 7);
      String[] lines = results.toArray(new String[]
      {})[1].split("\n");
      Assert.assertTrue("3 data fields are expected, but is : " + lines.length, lines.length == 3);
      Assert.assertEquals("expect second data field value is : " + lines[1], "data1b", lines[1]);

   }

   @Test
   @InSequence(8)
   public void testEscapedMessage() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(3);
      final AtomicInteger errors = new AtomicInteger(0);
      final List<String> results = new ArrayList<String>();
      final List<String> sent = new ArrayList<String>();
      sent.add("foo1\nbar");
      sent.add("foo2\nbar");
      sent.add("foo3\nbar");
      Client client = ClientBuilder.newBuilder().build();
      WebTarget target = client.target(generateURL("/service/sse-escaping"));
      SseEventSource msgEventSource = SseEventSource.target(target).build();
      try (SseEventSource eventSource = msgEventSource)
      {
         Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
         eventSource.register(event -> {
            results.add(event.readData(String.class));
            latch.countDown();
         }, ex -> {
            errors.incrementAndGet();
            logger.error(ex.getMessage(), ex);
            throw new RuntimeException(ex);
         });
         eventSource.open();

         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertEquals(0, errors.get());
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      }
      Assert.assertFalse("SseEventSource is not closed", msgEventSource.isOpen());
      Assert.assertTrue("3 messages are expected, but is : " + results.size(), results.size() == 3);
      for (String s : sent)
      {
         Assert.assertTrue("Sent message \"" + s + "\" not found as result.", results.contains(s));
      }
   }

   @Test
   @InSequence(10)
   public void testXmlEvent() throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(1);
      final AtomicInteger errors = new AtomicInteger(0);
      final List<InboundSseEvent> results = new ArrayList<InboundSseEvent>();
      Client client = ClientBuilder.newBuilder().build();
      WebTarget target = client.target(generateURL("/service/server-sent-events/xmlevent"));
      SseEventSource msgEventSource = SseEventSource.target(target).build();
      try (SseEventSource eventSource = msgEventSource)
      {
         Assert.assertEquals(SseEventSourceImpl.class, eventSource.getClass());
         eventSource.register(event -> {
            results.add(event);
            latch.countDown();
         }, ex -> {
            errors.incrementAndGet();
            ex.printStackTrace();
            throw new RuntimeException(ex);
         });
         eventSource.open();

         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertEquals(0, errors.get());
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      }
      JAXBElement<String> jaxbElement = results.get(0).readData(new javax.ws.rs.core.GenericType<JAXBElement<String>>()
      {
      }, MediaType.APPLICATION_XML_TYPE);
      Assert.assertEquals("xmldata is expceted", jaxbElement.getValue(), "xmldata");
   }

   @Test
   @InSequence(11)
   public void testGetSseEvent() throws Exception
   {
      Client client = ClientBuilder.newClient();
      WebTarget target = client.target(generateURL("/service/server-sent-events/events"));
      Response response = target.request().get();
      Assert.assertEquals("response OK is expected", response.getStatus(), 200);
      Assert.assertEquals("text/event-stream is expected", response.getMediaType(), MediaType.SERVER_SENT_EVENTS_TYPE);
      client.close();
   }
   
   @Test
   @InSequence(12)
   public void testNoContent() throws Exception
   {
      Client client = ClientBuilder.newBuilder().build();
      final AtomicInteger errors = new AtomicInteger(0);
      WebTarget target = client.target(generateURL("/service/server-sent-events/noContent"));
      try (SseEventSource source = SseEventSource.target(target).build())
      {
         source.register(event -> {
            System.out.println(event);
         }, ex -> {
            ex.printStackTrace();
            errors.incrementAndGet();
         });
         source.open();
         Thread.sleep(1000);
      }
      catch (InterruptedException e)
      {
         // falls through
         e.printStackTrace();
      }
      Assert.assertTrue("error is not expected", errors.get() == 0);
   }

   //    @Test
   //    //This will open a browser and test with html sse client
   //    public void testHtmlSse() throws Exception
   //    {
   //       
   //       Runtime runtime = Runtime.getRuntime();
   //       try
   //       {
   //          runtime.exec("xdg-open " + generateURL(""));
   //       }
   //       catch (IOException e)
   //       {
   //
   //       }
   //       Thread.sleep(30 * 1000);
   //    }
}
