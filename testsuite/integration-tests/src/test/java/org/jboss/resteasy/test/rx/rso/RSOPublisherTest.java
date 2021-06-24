package org.jboss.resteasy.test.rx.rso;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.shared.CLIServerSetupTask;
import org.jboss.resteasy.category.ExpectedFailingOnWildFly22;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.rso.PublisherRxInvoker;
import org.jboss.resteasy.rso.PublisherRxInvokerProvider;
import org.jboss.resteasy.test.rx.resource.Bytes;
import org.jboss.resteasy.test.rx.resource.RxScheduledExecutorService;
import org.jboss.resteasy.test.rx.resource.TRACE;
import org.jboss.resteasy.test.rx.resource.TestException;
import org.jboss.resteasy.test.rx.resource.TestExceptionMapper;
import org.jboss.resteasy.test.rx.resource.Thing;
import org.jboss.resteasy.test.rx.rso.resource.RSOPublisherResourceImpl;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * @tpSubChapter Reactive classes
 * @tpChapter Integration tests
 * @tpSince RESTEasy 3.6
 *
 * In these tests, the server uses Publishers to create results asynchronously and streams the elements
 * of the Publishers as they are created.
 *
 * The client makes invocations on an PublisherRxInvoker.
 */
@RunWith(Arquillian.class)
@RunAsClient
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ServerSetup(EnableReactiveExtensionsSetupTask.class)
@Category({ExpectedFailingOnWildFly22.class})
public class RSOPublisherTest {

   private static ResteasyClient client;
   private static CountDownLatch latch;
   private static AtomicInteger errors;

   private static final List<String> xStringList = new ArrayList<String>();
   private static final List<String> aStringList = new ArrayList<String>();
   private static final List<Thing>  xThingList =  new ArrayList<Thing>();
   private static final List<Thing>  aThingList =  new ArrayList<Thing>();
   private static final List<List<Thing>> xThingListList = new ArrayList<List<Thing>>();
   private static final List<List<Thing>> aThingListList = new ArrayList<List<Thing>>();
   private static final Entity<String> aEntity = Entity.entity("a", MediaType.TEXT_PLAIN_TYPE);
   private static final Entity<String> threeEntity = Entity.entity("3", MediaType.TEXT_PLAIN_TYPE);

   private static AtomicReference<Object> value = new AtomicReference<Object>();
   private static ArrayList<String> stringList = new ArrayList<String>();
   private static ArrayList<Thing>  thingList = new ArrayList<Thing>();
   private static ArrayList<List<?>> thingListList = new ArrayList<List<?>>();
   private static ArrayList<byte[]> bytesList = new ArrayList<byte[]>();
   private static GenericType<List<Thing>> LIST_OF_THING = new GenericType<List<Thing>>() {};

   static {
      for (int i = 0; i < 3; i++) {xStringList.add("x");}
      for (int i = 0; i < 3; i++) {aStringList.add("a");}
      for (int i = 0; i < 3; i++) {xThingList.add(new Thing("x"));}
      for (int i = 0; i < 3; i++) {aThingList.add(new Thing("a"));}
      for (int i = 0; i < 2; i++) {xThingListList.add(xThingList);}
      for (int i = 0; i < 2; i++) {aThingListList.add(aThingList);}
   }

   @Deployment
   public static Archive<?> deploy() {
      WebArchive war = TestUtil.prepareArchive(RSOPublisherTest.class.getSimpleName());
      war.addClass(Thing.class);
      war.addClass(TRACE.class);
      war.addClass(Bytes.class);
      war.addClass(TestException.class);
      war.addPackage("org.jboss.resteasy.rso");
      war.addClasses(EnableReactiveExtensionsSetupTask.class, CLIServerSetupTask.class);
      return TestUtil.finishContainerPrepare(war, null, RSOPublisherResourceImpl.class, TestExceptionMapper.class);
   }

   private static String generateURL(String path) {
      return PortProviderUtil.generateURL(path, RSOPublisherTest.class.getSimpleName());
   }

   //////////////////////////////////////////////////////////////////////////////
   @BeforeClass
   public static void beforeClass() throws Exception {
      client = new ResteasyClientBuilder().build();
   }

   @Before
   public void before() throws Exception {
      stringList.clear();
      thingList.clear();
      thingListList.clear();
      bytesList.clear();
      latch = new CountDownLatch(1);
      errors = new AtomicInteger(0);
      value.set(null);
   }

   @AfterClass
   public static void after() throws Exception {
      client.close();
   }

   //////////////////////////////////////////////////////////////////////////////

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGet() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.get();
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()
            ));
      boolean waitResult = latch.await(5, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetRx() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.get();
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(5, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.get(Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.get(LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.get(byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPut() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/put/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.put(aEntity);
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPutThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/put/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.put(aEntity, Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPutThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/put/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.put(aEntity, LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPutBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/put/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.put(threeEntity, byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPost() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.post(aEntity);
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPostThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.post(aEntity, Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPostThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.post(aEntity, LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPostBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.post(threeEntity, byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testDelete() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/delete/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.delete();
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testDeleteThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/delete/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.delete(Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testDeleteThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/delete/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.delete(LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testDeleteBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/delete/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.delete(byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testHead() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/head/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.head();
      publisher.subscribe(new TestSubscriber<String>(
            (String s) -> value.set(s), // HEAD - no body
            (Throwable t) -> throwableContains(t, "Input stream was empty"),
            null));
      Assert.assertNull(value.get());
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testOptions() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/options/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.options();
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testOptionsThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/options/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.options(Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testOptionsThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/options/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.options(LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testOptionsBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/options/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.options(byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTrace() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/trace/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.trace();
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTraceThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/trace/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.trace(Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTraceThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/trace/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.trace(LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTraceBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/trace/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.get(byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodGet() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.method("GET");
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodGetThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.method("GET", Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodGetThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.method("GET", LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(thingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodGetBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/get/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.method("GET", byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodPost() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher = (Publisher<String>) invoker.method("POST", aEntity);
      publisher.subscribe(new TestSubscriber<String>(
            (String o) -> stringList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodPostThing() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/thing")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.method("POST", aEntity, Thing.class);
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing o) -> thingList.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodPostThingList() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/thing/list")).request().rx(PublisherRxInvoker.class);
      Publisher<List<Thing>> publisher = (Publisher<List<Thing>>) invoker.method("POST", aEntity, LIST_OF_THING);
      publisher.subscribe(new TestSubscriber<List<Thing>>(
            (List<?> l) -> thingListList.add(l),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testMethodPostBytes() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.method("POST", threeEntity, byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(3, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testScheduledExecutorService () throws Exception {
      {
         RxScheduledExecutorService.used = false;
         PublisherRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
         Publisher<String> publisher = (Publisher<String>) invoker.get();
         publisher.subscribe(new TestSubscriber<String>(
               (String o) -> stringList.add(o),
               (Throwable t) -> errors.incrementAndGet(),
               () -> latch.countDown()));
         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
         Assert.assertEquals(0, errors.get());
         Assert.assertFalse(RxScheduledExecutorService.used);
         Assert.assertEquals(xStringList, stringList);
      }

      {
         stringList.clear();
         latch = new CountDownLatch(1);
         RxScheduledExecutorService.used = false;
         RxScheduledExecutorService executor = new RxScheduledExecutorService();
         ResteasyClient client = ((ResteasyClientBuilder) new ResteasyClientBuilder().executorService(executor)).build();
         client.register(PublisherRxInvokerProvider.class);
         PublisherRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
         Publisher<String> publisher = (Publisher<String>) invoker.get();
         stringList.clear();
         publisher.subscribe(new TestSubscriber<String>(
               (String o) -> stringList.add(o),
               (Throwable t) -> errors.incrementAndGet(),
               () -> latch.countDown()));
         boolean waitResult = latch.await(30, TimeUnit.SECONDS);
         Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
         Assert.assertEquals(0, errors.get());
         Assert.assertTrue(RxScheduledExecutorService.used);
         Assert.assertEquals(xStringList, stringList);
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testUnhandledException() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/exception/unhandled")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.get(Thing.class);
      AtomicReference<Object> value = new AtomicReference<Object>();
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing t) -> thingList.add(t),
            (Throwable t) -> {value.set(t); latch.countDown();},
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Throwable t = (Throwable) value.get();
      Assert.assertEquals(InternalServerErrorException.class, t.getClass());
      Assert.assertTrue(t.getMessage().contains("500"));
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testHandledException() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/exception/handled")).request().rx(PublisherRxInvoker.class);
      Publisher<Thing> publisher = (Publisher<Thing>) invoker.get(Thing.class);
      AtomicReference<Object> value = new AtomicReference<Object>();
      publisher.subscribe(new TestSubscriber<Thing>(
            (Thing t) -> thingList.add(t),
            (Throwable t) -> {value.set(t); latch.countDown();},
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Throwable t = (Throwable) value.get();
      Assert.assertEquals(ClientErrorException.class, t.getClass());
      Assert.assertTrue(t.getMessage().contains("444"));
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetTwoClients() throws Exception {
      CountDownLatch cdl = new CountDownLatch(2);
      CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<String>();

      ResteasyClient client1 = new ResteasyClientBuilder().build();
      client1.register(PublisherRxInvokerProvider.class);
      PublisherRxInvoker invoker1 = client1.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher1 = (Publisher<String>) invoker1.get();

      ResteasyClient client2 = new ResteasyClientBuilder().build();
      client2.register(PublisherRxInvokerProvider.class);
      PublisherRxInvoker invoker2 = client2.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher2 = (Publisher<String>) invoker2.get();

      publisher1.subscribe(new TestSubscriber<String>(
            (String o) -> list.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> cdl.countDown()));

      publisher2.subscribe(new TestSubscriber<String>(
            (String o) -> list.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> cdl.countDown()));

      boolean waitResult = cdl.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(6, list.size());
      for (int i = 0; i < 6; i++) {
         Assert.assertEquals("x", list.get(i));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetTwoInvokers() throws Exception {
      CountDownLatch cdl = new CountDownLatch(2);
      CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<String>();

      PublisherRxInvoker invoker1 = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher1 = (Publisher<String>) invoker1.get();

      PublisherRxInvoker invoker2 = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher2 = (Publisher<String>) invoker2.get();

      publisher1.subscribe(new TestSubscriber<String>(
            (String o) -> list.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> cdl.countDown()));

      publisher2.subscribe(new TestSubscriber<String>(
            (String o) -> list.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> cdl.countDown()));

      boolean waitResult = cdl.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(6, list.size());
      for (int i = 0; i < 6; i++) {
         Assert.assertEquals("x", list.get(i));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testGetTwoPublishers() throws Exception {
      CountDownLatch cdl = new CountDownLatch(2);
      CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<String>();

      PublisherRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(PublisherRxInvoker.class);
      Publisher<String> publisher1 = (Publisher<String>) invoker.get();
      Publisher<String> publisher2 = (Publisher<String>) invoker.get();

      publisher1.subscribe(new TestSubscriber<String>(
            (String o) -> list.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> cdl.countDown()));

      publisher2.subscribe(new TestSubscriber<String>(
            (String o) -> list.add(o),
            (Throwable t) -> errors.incrementAndGet(),
            () -> cdl.countDown()));

      boolean waitResult = cdl.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(6, list.size());
      for (int i = 0; i < 6; i++) {
         Assert.assertEquals("x", list.get(i));
      }
   }

   @SuppressWarnings("unchecked")
   @Test @Ignore
   public void testPostBytesLong() throws Exception {
      PublisherRxInvoker invoker = client.target(generateURL("/post/bytes")).request().rx(PublisherRxInvoker.class);
      Publisher<byte[]> publisher = (Publisher<byte[]>) invoker.post(Entity.entity("1000", MediaType.TEXT_PLAIN_TYPE), byte[].class);
      publisher.subscribe(new TestSubscriber<byte[]>(
            (byte[] b) -> bytesList.add(b),
            (Throwable t) -> errors.incrementAndGet(),
            () -> latch.countDown()));
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(1000, bytesList.size());
      for (byte[] b : bytesList) {
         Assert.assertTrue(Arrays.equals(Bytes.BYTES, b));
      }
   }

   private static boolean throwableContains(Throwable t, String s) {
      while (t != null) {
         if (t.getMessage().contains(s))
         {
            return true;
         }
         t = t.getCause();
      }
      return false;
   }

   static class TestSubscriber<T> implements Subscriber<T> {
      private Consumer<? super T> onNext;
      private Consumer<? super Throwable> onError;
      private Runnable onComplete;

      TestSubscriber(final Consumer<? super T> onNext, final Consumer<? super Throwable> onError, final Runnable onComplete) {
         this.onNext = onNext;
         this.onError = onError;
         this.onComplete = onComplete;
      }

      @Override
      public void onSubscribe(Subscription s) {
         //
      }

      @Override
      public void onNext(T t) {
         try {
            onNext.accept(t);
         } catch (Exception e) {
            onError(e);
         }
      }

      @Override
      public void onError(Throwable t) {
         try {
            onError.accept(t);
         } catch (Exception e) {
            throw new RuntimeException(t);
         }
      }

      @Override
      public void onComplete() {
         try {
            onComplete.run();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      public String toString() {
         return super.toString();
      }
   }
}
