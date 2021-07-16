package org.jboss.resteasy.test.microprofile.restclient;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.resteasy.category.MicroProfileDependent;
import org.jboss.resteasy.microprofile.client.RestClientBuilderImpl;
import org.jboss.resteasy.test.microprofile.restclient.resource.FollowRedirectsResource;
import org.jboss.resteasy.test.microprofile.restclient.resource.FollowRedirectsService;
import org.jboss.resteasy.test.microprofile.restclient.resource.FollowRedirectsServiceIntf;
import org.jboss.resteasy.utils.PortProviderUtil;
import org.jboss.resteasy.utils.TestUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.net.URI;

/**
 * @tpSubChapter MicroProfile rest client
 * @tpChapter Integration tests
 * @tpTestCaseDetails Show followsRedirects flag works.
 * @tpSince RESTEasy 3.15.0
 */
@RunWith(Arquillian.class)
@RunAsClient
@Category(MicroProfileDependent.class)
public class FollowRedirectsTest {
    protected static final Logger LOG = Logger.getLogger(FollowRedirectsTest.class.getName());
    private static final String WAR_SERVICE = "followRedirects_service";
    private static final String WAR_CLIENT = "followRedirects_client";
    private static final String THE_PATRON = "thePatron";

    @Deployment(name=WAR_SERVICE)
    public static Archive<?> serviceDeploy() {
        WebArchive war = TestUtil.prepareArchive(WAR_SERVICE);
        war.addClasses(FollowRedirectsService.class,
                PortProviderUtil.class);
        return TestUtil.finishContainerPrepare(war, null, null);
    }

    @Deployment(name=WAR_CLIENT)
    public static Archive<?> clientDeploy() {
        WebArchive war = TestUtil.prepareArchive(WAR_CLIENT);
        war.addClasses(FollowRedirectsResource.class,
                FollowRedirectsServiceIntf.class,
                PortProviderUtil.class);
        return TestUtil.finishContainerPrepare(war, null, null);
    }

    static FollowRedirectsServiceIntf followRedirectsServiceIntf;
    @BeforeClass
    public static void before() throws Exception {
        RestClientBuilderImpl builder = new RestClientBuilderImpl();
        followRedirectsServiceIntf = builder
                .baseUri(URI.create(generateURL("", WAR_SERVICE)))
                .followRedirects(true)
                .build(FollowRedirectsServiceIntf.class);
    }

    private static String generateURL(String path, String deployName) {
        return PortProviderUtil.generateURL(path, deployName);
    }

    /*
     * Default setting for followRedirects is FALSE.
     * Confirm no redirection.
     */

    @Test
    public void defaultFollowRedirects() {
        RestClientBuilderImpl builder = new RestClientBuilderImpl();
        FollowRedirectsServiceIntf result = builder
                .baseUri(URI.create(generateURL("", WAR_SERVICE)))
                .build(FollowRedirectsServiceIntf.class);

        Response response = result.tmpRedirect(THE_PATRON, WAR_CLIENT);
        Assert.assertEquals(307, response.getStatus());
        response.close();
    }

    /*
     * Set followRedirects ON and confirm it is working.
     */
    @Test
    public void followTemporaryRedirect() {
        Response response = followRedirectsServiceIntf.tmpRedirect(THE_PATRON, WAR_CLIENT);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("OK", response.readEntity(String.class));
        response.close();
    }

    /*
     * Confirm 303 status redirect with POST works.
     */
    @Test
    public void postRedirect() {
        Response response = followRedirectsServiceIntf.postRedirect(WAR_CLIENT);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("OK", response.readEntity(String.class));
        response.close();
    }

    /*
     * Confirm 301 status with "location" header.
     */
    @Test
    public void movedPermanently() {
        Response response = followRedirectsServiceIntf.movedPermanently(THE_PATRON, WAR_CLIENT);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("ok - direct response",
                response.readEntity(String.class));
        response.close();
    }

    /*
     * Confirm 302 status with "location" header.
     */
    @Test
    public void found() {
        Response response = followRedirectsServiceIntf.found(THE_PATRON, WAR_CLIENT);
        Assert.assertEquals(200, response.getStatus());
        Assert.assertEquals("ok - direct response",
                response.readEntity(String.class));
        response.close();
    }
}
