package org.jboss.resteasy.test.validation;

import static org.junit.Assert.assertEquals;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.api.validation.ResteasyViolationException;
import org.jboss.resteasy.api.validation.Validation;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.test.validation.resource.EmptyArrayValidationFoo;
import org.jboss.resteasy.test.validation.resource.EmptyArrayValidationResource;
import org.jboss.resteasy.util.HttpResponseCodes;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @tpSubChapter Resteasy-client
 * @tpChapter Client tests
 * @tpSince RESTEasy 3.14.0
 * @tpTestCaseDetails Regression test for RESTEASY-2765
 */
@RunWith(Arquillian.class)
@RunAsClient
public class EmptyArrayValidationTest {

   @Deployment
   public static Archive<?> deploy() {
      WebArchive war = TestUtil.prepareArchive(EmptyArrayValidationTest.class.getSimpleName());
      war.addClass(EmptyArrayValidationFoo.class);
      return TestUtil.finishContainerPrepare(war, null, EmptyArrayValidationResource.class);
   }

   private String generateURL(String path) {
      return PortProviderUtil.generateURL(path, EmptyArrayValidationTest.class.getSimpleName());
   }

   /**
    * @tpTestDetails Verify validation of empty array doesn't throw ArrayIndexOutOfBoundsException.
    * @tpSince RESTEasy 3.14.0
    */
   @Test
   public void testEmptyArray() throws Exception {
      ResteasyClient client = new ResteasyClientBuilder().build();
      EmptyArrayValidationFoo foo = new EmptyArrayValidationFoo(new Object[] {});
      Response response = client.target(generateURL("/emptyarray")).request().post(Entity.entity(foo, MediaType.APPLICATION_JSON), Response.class);
      Object header = response.getHeaderString(Validation.VALIDATION_HEADER);
      Assert.assertTrue("Header has wrong format", header instanceof String);
      Assert.assertTrue("Header has wrong format", Boolean.valueOf(String.class.cast(header)));
      String answer = response.readEntity(String.class);
      assertEquals(HttpResponseCodes.SC_BAD_REQUEST, response.getStatus());
      ResteasyViolationException e = new ResteasyViolationException(String.class.cast(answer));
      TestUtil.countViolations(e, 1, 0, 0, 0, 1, 0);
   }
}
