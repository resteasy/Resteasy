package org.jboss.resteasy.test.rx.rxjava;

import static org.junit.Assert.assertArrayEquals;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.test.rx.rxjava.resource.RxNoStreamResource;
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
 * @tpSubChapter Reactive classes
 * @tpChapter Integration tests
 * @tpSince RESTEasy 3.6
 */
@RunWith(Arquillian.class)
@RunAsClient
public class NoStreamRxTest
{
   private static ResteasyClient client;

   @Deployment
   public static Archive<?> deploy() {
      WebArchive war = TestUtil.prepareArchive(NoStreamRxTest.class.getSimpleName());
      war.setManifest(new StringAsset("Manifest-Version: 1.0\n"
         + "Dependencies: org.jboss.resteasy.resteasy-rxjava services, org.jboss.resteasy.resteasy-json-binding-provider services\n"));
      return TestUtil.finishContainerPrepare(war, null, RxNoStreamResource.class);
   }

   private static String generateURL(String path) {
      return PortProviderUtil.generateURL(path, NoStreamRxTest.class.getSimpleName());
   }

   //////////////////////////////////////////////////////////////////////////////
   @BeforeClass
   public static void beforeClass() throws Exception {
      client = new ResteasyClientBuilder().build();
   }

   @AfterClass
   public static void after() throws Exception {
      client.close();
   }

   @Test
   public void testRx() throws InterruptedException
   {
      String data = client.target(generateURL("/single")).request().get(String.class);
      Assert.assertEquals("got it", data);

      String[] data2 = client.target(generateURL("/observable")).request().get(String[].class);
      Assert.assertArrayEquals(new String[] {"one", "two"}, data2);

      String data3 = client.target(generateURL("/context/single")).request().get(String.class);
      Assert.assertEquals("got it", data3);

      String[] data4 = client.target(generateURL("/context/observable")).request().get(String[].class);
      assertArrayEquals(new String[] {"one", "two"}, data4);
   }
}