package org.jboss.resteasy.test.rx.rxjava2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.rxjava2.ObservableRxInvoker;
import org.jboss.resteasy.rxjava2.ObservableRxInvokerProvider;
import org.jboss.resteasy.test.rx.resource.Bytes;
import org.jboss.resteasy.test.rx.resource.RxScheduledExecutorService;
import org.jboss.resteasy.test.rx.resource.TRACE;
import org.jboss.resteasy.test.rx.resource.TestException;
import org.jboss.resteasy.test.rx.resource.TestExceptionMapper;
import org.jboss.resteasy.test.rx.resource.Thing;
import org.jboss.resteasy.test.rx.rxjava2.resource.Rx2ObservableResourceImpl;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import io.reactivex.Observable;

/**
 * @tpSubChapter Reactive classes
 * @tpChapter Integration tests
 * @tpSince RESTEasy 4.2
 *
 * In these tests, the server uses Flowables to create results asynchronously and streams the elements
 * of the Flowables as they are created.
 *
 * The client makes invocations on an FlowableRxInvoker.
 */
@RunWith(Arquillian.class)
@RunAsClient
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Rx2ObservableExtTest {

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
   private static final MediaType X_STREAM_GENERAL = new MediaType("application", "x-stream-general");

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
      WebArchive war = TestUtil.prepareArchive(Rx2ObservableExtTest.class.getSimpleName());
      war.addClass(Thing.class);
      war.addClass(TRACE.class);
      war.addClass(Bytes.class);
      war.addClass(RxScheduledExecutorService.class);
      war.addClass(TestException.class);
      war.setManifest(new StringAsset("Manifest-Version: 1.0\n"
         + "Dependencies: org.jboss.resteasy.resteasy-rxjava2 services\n"));
      return TestUtil.finishContainerPrepare(war, null, Rx2ObservableResourceImpl.class, TestExceptionMapper.class);
   }

   private static String generateURL(String path) {
      return PortProviderUtil.generateURL(path, Rx2ObservableExtTest.class.getSimpleName());
   }

   //////////////////////////////////////////////////////////////////////////////
   @BeforeClass
   public static void beforeClass() throws Exception {
      client = (ResteasyClient)ClientBuilder.newClient();
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
   @Test
   public void testGet() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.getResponse(Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.getResponse(LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.getResponse(byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testPut() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/put/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.putResponse(aEntity, String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPutThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/put/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.putResponse(aEntity, Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPutThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/put/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.putResponse(aEntity, LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPutBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/put/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.putResponse(threeEntity, byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testPost() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.postResponse(aEntity, String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPostThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.postResponse(aEntity, Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPostThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.postResponse(aEntity, LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPostBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.postResponse(threeEntity, byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testDelete() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/delete/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.deleteResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testDeleteThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/delete/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.deleteResponse(Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testDeleteThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/delete/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.deleteResponse(LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>)  response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testDeleteBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/delete/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.deleteResponse(byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testHead() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/head/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.headResponse().toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String s) -> value.set(s), // HEAD - no body
         (Throwable t) -> throwableContains(t, "Input stream was empty"));
      Assert.assertNull(value.get());
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testOptions() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/options/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.optionsResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testOptionsThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/options/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.optionsResponse(Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testOptionsThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/options/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.optionsResponse(LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testOptionsBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/options/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.optionsResponse(byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
      ObservableRxInvoker invoker = client.target(generateURL("/trace/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.traceResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTraceThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/trace/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.traceResponse(Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTraceThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/trace/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.traceResponse(LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   @Ignore // TRACE turned off by default in Wildfly
   public void testTraceBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/trace/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.traceResponse(byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testMethodGet() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("GET", String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testMethodGetThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("GET", Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(xThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testMethodGetThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("GET", LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(thingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testMethodGetBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/get/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("GET", byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testMethodPost() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/string")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("POST", aEntity, String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable = (Observable<String>) response.getEntity();
      observable.subscribe(
         (String o) -> stringList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aStringList, stringList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testMethodPostThing() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/thing")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("POST", aEntity, Thing.class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      observable.subscribe(
         (Thing o) -> thingList.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingList, thingList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testMethodPostThingList() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/thing/list")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("POST", aEntity, LIST_OF_THING).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<List<Thing>> observable = (Observable<List<Thing>>) response.getEntity();
      observable.subscribe(
         (List<?> l) -> thingListList.add(l),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(aThingListList, thingListList);
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testMethodPostBytes() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.methodResponse("POST", threeEntity, byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
   public void testUnhandledException() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/exception/unhandled")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.getResponse(Thing.class).toCompletableFuture().get();
      Assert.assertEquals(500, response.getStatus());
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      AtomicReference<Object> value = new AtomicReference<Object>();
      observable.subscribe(
         (Thing t) -> thingList.add(t),
         (Throwable t) -> {value.set(t); latch.countDown();},
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Throwable t = (Throwable) value.get();
      Assert.assertEquals(InternalServerErrorException.class, t.getClass());
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testHandledException() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/exception/handled")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.getResponse(Thing.class).toCompletableFuture().get();
      Assert.assertEquals(444, response.getStatus());
      Observable<Thing> observable = (Observable<Thing>) response.getEntity();
      AtomicReference<Object> value = new AtomicReference<Object>();
      observable.subscribe(
         (Thing t) -> thingList.add(t),
         (Throwable t) -> {value.set(t); latch.countDown();},
         () -> latch.countDown());
      boolean waitResult = latch.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Throwable t = (Throwable) value.get();
      Assert.assertEquals(ClientErrorException.class, t.getClass());
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetTwoClients() throws Exception {
      CountDownLatch cdl = new CountDownLatch(2);
      CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<String>();

      ResteasyClient client1 = (ResteasyClient)ClientBuilder.newClient();
      client1.register(ObservableRxInvokerProvider.class);
      ObservableRxInvoker invoker1 = client1.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response1 = invoker1.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response1.getStatus());
      Assert.assertTrue(response1.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable1 = (Observable<String>) response1.getEntity();

      ResteasyClient client2 = (ResteasyClient)ClientBuilder.newClient();
      client2.register(ObservableRxInvokerProvider.class);
      ObservableRxInvoker invoker2 = client2.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response2 = invoker2.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response2.getStatus());
      Assert.assertTrue(response2.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable2 = (Observable<String>) response2.getEntity();

      observable1.subscribe(
         (String o) -> list.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> cdl.countDown());

      observable2.subscribe(
         (String o) -> list.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> cdl.countDown());

      boolean waitResult = cdl.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(6, list.size());
      for (int i = 0; i < 6; i++) {
         Assert.assertEquals("x", list.get(i));
      }
      client1.close();
      client2.close();
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetTwoInvokers() throws Exception {
      CountDownLatch cdl = new CountDownLatch(2);
      CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<String>();

      ObservableRxInvoker invoker1 = client.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response1 = invoker1.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response1.getStatus());
      Assert.assertTrue(response1.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable1 = (Observable<String>) response1.getEntity();

      ObservableRxInvoker invoker2 = client.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response2 = invoker2.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response2.getStatus());
      Assert.assertTrue(response2.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable2 = (Observable<String>) response2.getEntity();

      observable1.subscribe(
         (String o) -> list.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> cdl.countDown());

      observable2.subscribe(
         (String o) -> list.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> cdl.countDown());

      boolean waitResult = cdl.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(6, list.size());
      for (int i = 0; i < 6; i++) {
         Assert.assertEquals("x", list.get(i));
      }
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testGetTwoObservables() throws Exception {
      CountDownLatch cdl = new CountDownLatch(2);
      CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<String>();

      ObservableRxInvoker invoker = client.target(generateURL("/get/string")).request().rx(ObservableRxInvoker.class);
      Response response1 = invoker.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response1.getStatus());
      Assert.assertTrue(response1.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable1 = (Observable<String>) response1.getEntity();

      Response response2 = invoker.getResponse(String.class).toCompletableFuture().get();
      Assert.assertEquals(200, response2.getStatus());
      Assert.assertTrue(response2.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<String> observable2 = (Observable<String>) response2.getEntity();

      observable1.subscribe(
         (String o) -> list.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> cdl.countDown());

      observable2.subscribe(
         (String o) -> list.add(o),
         (Throwable t) -> errors.incrementAndGet(),
         () -> cdl.countDown());

      boolean waitResult = cdl.await(30, TimeUnit.SECONDS);
      Assert.assertTrue("Waiting for event to be delivered has timed out.", waitResult);
      Assert.assertEquals(0, errors.get());
      Assert.assertEquals(6, list.size());
      for (int i = 0; i < 6; i++) {
         Assert.assertEquals("x", list.get(i));
      }
   }

   @SuppressWarnings("unchecked")
   @Test
   public void testPostBytesLong() throws Exception {
      ObservableRxInvoker invoker = client.target(generateURL("/post/bytes")).request().rx(ObservableRxInvoker.class);
      Response response = invoker.postResponse(Entity.entity("1000", MediaType.TEXT_PLAIN_TYPE), byte[].class).toCompletableFuture().get();
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getMediaType().isCompatible(X_STREAM_GENERAL));
      Observable<byte[]> observable = (Observable<byte[]>) response.getEntity();
      observable.subscribe(
         (byte[] b) -> bytesList.add(b),
         (Throwable t) -> errors.incrementAndGet(),
         () -> latch.countDown());
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
}
