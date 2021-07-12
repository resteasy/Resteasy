package org.jboss.resteasy.test.resource.param;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.test.resource.param.resource.UserDefinedHeaderParamResource;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.ClientBuilder;

/**
 * @tpSubChapter Resteasy-client
 * @tpChapter Integration tests
 * @tpTestCaseDetails Regression test for RESTEASY-2874
 * @tpSince RESTEasy 4.7.0
 */
@RunWith(Arquillian.class)
@RunAsClient
public class UserDefinedHeaderParamTest {

    ResteasyClient client;

    private static final String HEADER_PARAM = "image/jpeg";

    @Path("")
    public interface UserHeaderParamInterface {

        @POST
        @Path("/header")
        @Consumes({"application/json", "text/plain", "image/jpeg"})
        String sendHeaderFirst(@HeaderParam("Content-Type") String contentType, String text);

        @POST
        @Path("/header")
        @Consumes({"application/json", "text/plain", "image/jpeg"})
        String sendTextFirst(String text, @HeaderParam("Content-Type") String contentType);

        @POST
        @Path("/header")
        @Consumes({"application/json", "text/plain", "image/jpeg"})
        String sendDefaultType(String text);

        @POST
        @Path("/header")
        @Consumes({"application/json", "text/plain", "image/jpeg"})
        String sendMultipleTypes(String text, @HeaderParam("Content-Type") String contentType,
                                 @HeaderParam("Content-Type") String secondContentType,
                                 @HeaderParam("Content-Type") String thirdContentType);
    }

    @Before
    public void setUp() {
        client = (ResteasyClient) ClientBuilder.newClient();
    }

    @After
    public void after() throws Exception {
        client.close();
    }

    @Deployment
    public static Archive<?> deploy() throws Exception {
        WebArchive war = TestUtil.prepareArchive(UserDefinedHeaderParamTest.class.getSimpleName());
        return TestUtil.finishContainerPrepare(war, null, UserDefinedHeaderParamResource.class);
    }

    private String generateURL() {
        return PortProviderUtil.generateBaseUrl(UserDefinedHeaderParamTest.class.getSimpleName());
    }

    /**
     * @tpTestDetails Checks whether the correct content type header is returned in case user specified header param
     * as a first argument in proxy method.
     * @tpPassCrit Expected header is returned
     * @tpSince RESTEasy 4.7.0
     */
    @Test
    public void testHeaderParamFirst(){
        ResteasyWebTarget target = client.target(generateURL());
        UserHeaderParamInterface proxy = target.proxy(UserDefinedHeaderParamTest.UserHeaderParamInterface.class);

        String response = proxy.sendHeaderFirst(HEADER_PARAM, "text");
        Assert.assertEquals("Incorrect header param returned,", HEADER_PARAM, response);
    }

    /**
     * @tpTestDetails Checks whether the correct content type header is returned in case user specified header param
     * as a second argument in proxy method.
     * @tpPassCrit Expected header is returned
     * @tpSince RESTEasy 4.7.0
     */
    @Test
    public void testTextFirst(){
        ResteasyWebTarget target = client.target(generateURL());
        UserHeaderParamInterface proxy = target.proxy(UserDefinedHeaderParamTest.UserHeaderParamInterface.class);

        String response = proxy.sendTextFirst("text", HEADER_PARAM);
        Assert.assertEquals("Incorrect header param returned,", HEADER_PARAM, response);
    }

    /**
     * @tpTestDetails Checks whether the correct content type header is returned in case user didn't specify header
     * param in proxy method. This should be the first content type in case of multiple @Consumes values.
     * @tpPassCrit Expected header is returned
     * @tpSince RESTEasy 4.7.0
     */
    @Test
    public void testDefaultHeaderParam(){
        ResteasyWebTarget target = client.target(generateURL());
        UserHeaderParamInterface proxy = target.proxy(UserDefinedHeaderParamTest.UserHeaderParamInterface.class);

        String response = proxy.sendDefaultType("text");
        Assert.assertEquals("Incorrect header param returned,", "application/json", response);
    }

    /**
     * @tpTestDetails Checks whether the correct content type header is returned in case user specified header
     * param in proxy method with multiple other header params. This should be the last content type in arguments.
     * @tpPassCrit Expected header is returned
     * @tpSince RESTEasy 4.7.0
     */
    @Test
    public void testMultipleHeaderParams(){
        ResteasyWebTarget target = client.target(generateURL());
        UserHeaderParamInterface proxy = target.proxy(UserDefinedHeaderParamTest.UserHeaderParamInterface.class);

        String response = proxy.sendMultipleTypes("text", "text/plain","application/json","image/jpeg");
        Assert.assertEquals("Incorrect header param returned,", HEADER_PARAM, response);
    }


}