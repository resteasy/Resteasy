package org.jboss.resteasy.test.cdi.basic;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.resteasy.test.cdi.basic.resource.EJBApplication;
import org.jboss.resteasy.test.cdi.basic.resource.EJBBook;
import org.jboss.resteasy.test.cdi.basic.resource.EJBBookReader;
import org.jboss.resteasy.test.cdi.basic.resource.EJBBookReaderImpl;
import org.jboss.resteasy.test.cdi.basic.resource.EJBBookResource;
import org.jboss.resteasy.test.cdi.basic.resource.EJBBookWriterImpl;
import org.jboss.resteasy.test.cdi.basic.resource.EJBLocalResource;
import org.jboss.resteasy.test.cdi.basic.resource.EJBRemoteResource;
import org.jboss.resteasy.test.cdi.basic.resource.EJBResourceParent;
import org.jboss.resteasy.test.cdi.util.Constants;
import org.jboss.resteasy.test.cdi.util.Counter;
import org.jboss.resteasy.test.cdi.util.Utilities;
import org.jboss.resteasy.test.cdi.util.UtilityProducer;
import org.jboss.resteasy.core.ThreadLocalResteasyProviderFactory;
import org.jboss.resteasy.spi.HttpResponseCodes;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.utils.PermissionUtil;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.lang.reflect.ReflectPermission;
import java.net.SocketPermission;
import java.security.SecurityPermission;
import java.util.Hashtable;
import java.util.PropertyPermission;
import java.util.logging.LoggingPermission;

import static org.junit.Assert.assertEquals;

/**
 * @tpSubChapter CDI
 * @tpChapter Integration tests
 * @tpTestCaseDetails EJB and RESTEasy integration test.
 * @tpSince RESTEasy 3.0.16
 */
@RunWith(Arquillian.class)
public class EJBTest {

   private static Logger log = Logger.getLogger(EJBTest.class);

   @Inject
   EJBLocalResource localResource;

   /**
    * value of DEPLOYMENT_NAME is also used in ejbtest_web.xml file
    */
   public static final String DEPLOYMENT_NAME = "resteasy-ejb-test";

   private ResteasyProviderFactory defaultProviderFactory;

   private Client client;

   @Deployment
   public static Archive<?> createTestArchive() {
      WebArchive war = ShrinkWrap.create(WebArchive.class, DEPLOYMENT_NAME + ".war");
      // test needs to use special annotations in Application class, TestApplication class could not be used
      war.addClass(EJBApplication.class);
      war.addClass(PortProviderUtil.class);
      war.addClasses(EJBBook.class, Constants.class, Counter.class, UtilityProducer.class, Utilities.class)
         .addClasses(EJBBookReader.class, EJBBookReaderImpl.class)
         .addClasses(EJBBookWriterImpl.class)
         .addClasses(EJBResourceParent.class, EJBLocalResource.class, EJBRemoteResource.class, EJBBookResource.class)
         .setWebXML(EJBTest.class.getPackage(), "ejbtest_web.xml")
         .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml");
      // Arquillian in the deployment
      war.addAsManifestResource(PermissionUtil.createPermissionsXmlAsset(new ReflectPermission("suppressAccessChecks"),
            new LoggingPermission("control", ""),
            new PropertyPermission("arquillian.*", "read"),
            new PropertyPermission("ipv6", "read"),
            new PropertyPermission("node", "read"),
            new PropertyPermission("org.jboss.resteasy.port", "read"),
            new RuntimePermission("accessDeclaredMembers"),
            new SecurityPermission("insertProvider"),
            new RuntimePermission("getenv.RESTEASY_PORT"),
            new SocketPermission(PortProviderUtil.getHost(), "connect,resolve")
      ), "permissions.xml");
      return war;
   }

   @BeforeClass
   public static void readResteasyProviderFactory() {
   }

   @AfterClass
   public static void verifyResteasyProviderFactory() {
   }

   private String generateURL(String path) {
      return PortProviderUtil.generateURL(path, DEPLOYMENT_NAME);
   }

   /**
    * client needs to be non-static. BeforeClass and AfterClass methods are not executed on server (@RunAsClient annotation is not used).
    */
   @Before
   public void init() {
      defaultProviderFactory = ResteasyProviderFactory.peekInstance();
      client = ClientBuilder.newClient();
   }

   @After
   public void close() {
      client.close();
      ResteasyProviderFactory rpf = ResteasyProviderFactory.peekInstance();
      if (rpf instanceof ThreadLocalResteasyProviderFactory) {
         if (defaultProviderFactory == null) {
            //NOOP, usual case when first deployment is processed...
         } else if (defaultProviderFactory instanceof ThreadLocalResteasyProviderFactory) {
            Assert.assertEquals(((ThreadLocalResteasyProviderFactory)defaultProviderFactory).getDelegate(), ((ThreadLocalResteasyProviderFactory)rpf).getDelegate());
         }
      } else {
         Assert.assertEquals(defaultProviderFactory, rpf);
      }
      defaultProviderFactory = null;
   }

   /**
    * @tpTestDetails Verify that EJBBookReaderImpl, EJBBookWriterImpl, and EJBBookResource
    *                are placed in the correct scope.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testVerifyScopesJaxRs() throws Exception {
      log.info("starting testVerifyScopesJaxRs()");

      WebTarget base = client.target(generateURL("/verifyScopes/"));
      Response response = base.request().get();
      assertEquals("Wrong response status", HttpResponseCodes.SC_OK, response.getStatus());
      assertEquals("Wrong response content", HttpResponseCodes.SC_OK, response.readEntity(Integer.class).intValue());
   }

   /**
    * @tpTestDetails Verify that EJBBookReaderImpl, EJBBookWriterImpl, and EJBBookResource
    *                are placed in the correct scope on local.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testVerifyScopesLocalEJB() throws Exception {
      log.info("starting testVerifyScopesLocalEJB()");
      int result = localResource.verifyScopes();
      assertEquals(HttpResponseCodes.SC_OK, result);
   }

   /**
    * @tpTestDetails Verify that EJBBookReaderImpl, EJBBookWriterImpl, and EJBBookResource
    *                are placed in the correct scope on remote.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testVerifyScopesRemoteEJB() throws Exception {
      log.info("starting testVerifyScopesRemoteEJB()");

      // Get proxy to JAX-RS resource as EJB.
      EJBRemoteResource remoteResource = getRemoteResource();
      log.info("remote: " + remoteResource);
      int result = remoteResource.verifyScopes();
      log.info("result: " + result);
      assertEquals(HttpResponseCodes.SC_OK, result);
   }

   /**
    * @tpTestDetails Verify that EJBBookReader and EJBBookWriterImpl are correctly injected
    *                into EJBBookResource.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testVerifyInjectionJaxRs() throws Exception {
      log.info("starting testVerifyInjectionJaxRs()");
      WebTarget base = client.target(generateURL("/verifyInjection/"));
      Response response = base.request().get();
      assertEquals("Wrong response status", HttpResponseCodes.SC_OK, response.getStatus());
      assertEquals("Wrong response content", HttpResponseCodes.SC_OK, response.readEntity(Integer.class).intValue());
   }

   /**
    * @tpTestDetails Verify that EJBBookReader and EJBBookWriterImpl are correctly injected
    *                into EJBBookResource.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testVerifyInjectionLocalEJB() throws Exception {
      log.info("starting testVerifyInjectionLocalEJB()");
      int result = localResource.verifyInjection();
      log.info("testVerifyInjectionLocalEJB result: " + result);
      assertEquals(HttpResponseCodes.SC_OK, result);
   }

   /**
    * @tpTestDetails Verify that EJBBookReader and EJBBookWriterImpl are correctly injected
    *                into EJBBookResource.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testVerifyInjectionRemoteEJB() throws Exception {
      log.info("starting testVerifyInjectionRemoteEJB()");

      // Get proxy to JAX-RS resource as EJB.
      EJBRemoteResource remoteResource = getRemoteResource();
      log.info("remote: " + remoteResource);
      int result = remoteResource.verifyInjection();
      log.info("result: " + result);
      assertEquals(HttpResponseCodes.SC_OK, result);
   }

   /**
    * @tpTestDetails Further addresses the use of EJBs as JAX-RS components.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testAsJaxRSResource() throws Exception {
      log.info("entering testAsJaxRSResource()");

      // Create book.
      WebTarget base = client.target(generateURL("/create/"));
      EJBBook book1 = new EJBBook("RESTEasy: the Sequel");
      Response response = base.request().post(Entity.entity(book1, Constants.MEDIA_TYPE_TEST_XML));
      log.info("Status: " + response.getStatus());
      assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
      int id1 = response.readEntity(int.class);
      log.info("id: " + id1);
      assertEquals("Wrong id of Book1 id", Counter.INITIAL_VALUE, id1);

      // Create another book.
      EJBBook book2 = new EJBBook("RESTEasy: It's Alive");
      response = base.request().post(Entity.entity(book2, Constants.MEDIA_TYPE_TEST_XML));
      log.info("Status: " + response.getStatus());
      assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
      int id2 = response.readEntity(int.class);
      log.info("id: " + id2);
      assertEquals("Wrong id of Book2 id", Counter.INITIAL_VALUE + 1, id2);

      // Retrieve first book.
      base = client.target(generateURL("/book/" + id1));
      response = base.request().accept(Constants.MEDIA_TYPE_TEST_XML).get();
      log.info("Status: " + response.getStatus());
      assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
      EJBBook result = response.readEntity(EJBBook.class);
      log.info("book: " + book1);
      assertEquals("Wrong book1 received from server", book1, result);

      // Retrieve second book.
      base = client.target(generateURL("/book/" + id2));
      response = base.request().accept(Constants.MEDIA_TYPE_TEST_XML).get();
      log.info("Status: " + response.getStatus());
      assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
      result = response.readEntity(EJBBook.class);
      log.info("book: " + book2);
      assertEquals("Wrong book2 received from server", book2, result);

      // Verify that EJBBookReader and EJBBookWriter have been used, twice on each side.
      base = client.target(generateURL("/uses/4"));
      response = base.request().get();
      log.info("Status: " + response.getStatus());
      assertEquals(HttpResponseCodes.SC_OK, response.getStatus());
      response.close();

      // Reset counter.
      base = client.target(generateURL("/reset"));
      response = base.request().get();
      log.info("Status: " + response.getStatus());
      assertEquals(HttpResponseCodes.SC_NO_CONTENT, response.getStatus());
      response.close();
   }

   /**
    * @tpTestDetails Invokes additional methods of JAX-RS resource as local EJB.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testAsLocalEJB() throws Exception {
      log.info("entering testAsLocalEJB()");

      // Create book.
      EJBBook book1 = new EJBBook("RESTEasy: the Sequel");
      int id1 = localResource.createBook(book1);
      log.info("id1: " + id1);
      assertEquals("Wrong id of Book1 id", Counter.INITIAL_VALUE, id1);

      // Create another book.
      EJBBook book2 = new EJBBook("RESTEasy: It's Alive");
      int id2 = localResource.createBook(book2);
      log.info("id2: " + id2);
      assertEquals("Wrong id of Book2 id", Counter.INITIAL_VALUE + 1, id2);

      // Retrieve first book.
      EJBBook bookResponse1 = localResource.lookupBookById(id1);
      log.info("book1 response: " + bookResponse1);
      assertEquals("Wrong book1 received from server", book1, bookResponse1);

      // Retrieve second book.
      EJBBook bookResponse2 = localResource.lookupBookById(id2);
      log.info("book2 response: " + bookResponse2);
      assertEquals("Wrong book2 received from server", book2, bookResponse2);

      // Verify that EJBBookReader and EJBBookWriter haven't been used.
      localResource.testUse(0);

      // Reset counter.
      localResource.reset();
   }

   /**
    * @tpTestDetails Invokes additional methods of JAX-RS resource as remote EJB.
    * @tpSince RESTEasy 3.0.16
    */
   @Test
   public void testAsRemoteEJB() throws Exception {
      log.info("entering testAsRemoteEJB()");

      // Get proxy to JAX-RS resource as EJB.
      EJBRemoteResource remoteResource = getRemoteResource();
      log.info("remote: " + remoteResource);

      // Create book.
      EJBBook book1 = new EJBBook("RESTEasy: the Sequel");
      int id1 = remoteResource.createBook(book1);
      log.info("id1: " + id1);
      assertEquals("Wrong id of Book1 id", Counter.INITIAL_VALUE, id1);

      // Create another book.
      EJBBook book2 = new EJBBook("RESTEasy: It's Alive");
      int id2 = remoteResource.createBook(book2);
      log.info("id2: " + id2);
      assertEquals("Wrong id of Book2 id", Counter.INITIAL_VALUE + 1, id2);

      // Retrieve first book.
      EJBBook bookResponse1 = remoteResource.lookupBookById(id1);
      log.info("book1 response: " + bookResponse1);
      assertEquals("Wrong book1 received from server", book1, bookResponse1);

      // Retrieve second book.
      EJBBook bookResponse2 = remoteResource.lookupBookById(id2);
      log.info("book2 response: " + bookResponse2);
      assertEquals("Wrong book2 received from server", book2, bookResponse2);

      // Verify that EJBBookReader and EJBBookWriter haven't been used.
      remoteResource.testUse(0);

      // Reset counter.
      remoteResource.reset();
   }

   private static EJBRemoteResource getRemoteResource() throws Exception {
      final Hashtable<String, String> jndiProperties = new Hashtable<>();
      jndiProperties.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
      final Context context = new InitialContext(jndiProperties);
      String name = "ejb:/" + DEPLOYMENT_NAME + "/EJBBookResource!" + EJBRemoteResource.class.getName();
      return EJBRemoteResource.class.cast(context.lookup(name));
   }
}
