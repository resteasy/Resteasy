package org.jboss.resteasy.test.resteasy1056;

import static org.junit.Assert.assertEquals;

import java.net.URI;

import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.resteasy.api.validation.ResteasyConstraintViolation;
import org.jboss.resteasy.api.validation.ResteasyViolationException;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.validation.ResteasyViolationExceptionImpl;
import org.jboss.resteasy.resteasy1056.TestApplication;
import org.jboss.resteasy.resteasy1056.TestResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * RESTEASY-1056
 *
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version $Revision: 1.1 $
 *          <p>
 *          Copyright June 7, 2014
 */
@RunWith(Arquillian.class)
@RunAsClient
public class MissingCDITest {
   @Deployment
   public static Archive<?> createTestArchive() {
      WebArchive war = ShrinkWrap.create(WebArchive.class, "RESTEASY-1056.war")
            .addClasses(TestApplication.class, TestResource.class)
            .addAsWebInfResource("resteasy1056/web.xml");
      return war;
   }

   @ArquillianResource
   URI baseUri;

   @Test
   public void testMissingCDIValid() throws Exception {
      Response response = ResteasyClientBuilder.newClient().target(baseUri.toString() + "test/17").request().get();
      String entity = response.readEntity(String.class);
      assertEquals(200, response.getStatus());
      Assert.assertEquals("17", entity);
   }

   @Test
   public void testMissingCDIInvalid() throws Exception {
      Response response = ResteasyClientBuilder.newClient().target(baseUri.toString() + "test/0").request().get();
      String entity = response.readEntity(String.class);
      assertEquals(400, response.getStatus());
      ResteasyViolationException e = new ResteasyViolationExceptionImpl(entity);
      countViolations(e, 1, 0, 0, 1, 0);
      ResteasyConstraintViolation cv = e.getParameterViolations().iterator().next();
      Assert.assertTrue(cv.getMessage().equals("must be greater than or equal to 7"));
   }

   protected void countViolations(ResteasyViolationException e, int totalCount, int propertyCount, int classCount, int parameterCount, int returnValueCount) {
      Assert.assertEquals(totalCount, e.getViolations().size());
      Assert.assertEquals(propertyCount, e.getPropertyViolations().size());
      Assert.assertEquals(classCount, e.getClassViolations().size());
      Assert.assertEquals(parameterCount, e.getParameterViolations().size());
      Assert.assertEquals(returnValueCount, e.getReturnValueViolations().size());
   }
}
