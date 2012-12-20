package org.jboss.resteasy.test.skeleton.key;

import junit.framework.Assert;
import org.jboss.resteasy.skeleton.key.RSATokenVerifier;
import org.jboss.resteasy.skeleton.key.ResourceMetadata;
import org.jboss.resteasy.skeleton.key.SkeletonKeyTokenVerification;
import org.jboss.resteasy.skeleton.key.VerificationException;
import org.jboss.resteasy.skeleton.key.representations.AccessTokenResponse;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class LoginTest extends SkeletonTestBase
{
   @BeforeClass
   public static void setupTest() throws Exception
   {
      setupIDM("testrealm.json");
   }

   @Test
   public void testEnv() throws Exception
   {
      String val = "foo bar ${config} xyz config ${config} ";
      System.setProperty("config", "hello");
      System.out.println("***** START *****");
      Pattern p = Pattern.compile("[$][{]([^}]+)[}]");
      Matcher matcher = p.matcher(val);
      StringBuffer buf = new StringBuffer();
      while (matcher.find())
      {
         String envVar = matcher.group(1);
         String envVal = System.getProperty(envVar);
         matcher.appendReplacement(buf, envVal);
      }
      matcher.appendTail(buf);
      System.out.println(buf.toString());
   }

   @Test
   public void testLogin() throws Exception
   {
      System.out.println(realmInfo.getAuthorizationUrl());
      WebTarget authUrl = client.target(realmInfo.getAuthorizationUrl())
              .queryParam("client_id", "loginclient")
              .queryParam("redirect_uri", "http://localhost:8081/loginclient/redirect");

      String form = authUrl.request().get(String.class);
      System.out.println(form);

      Pattern p = Pattern.compile("action=\"([^\"]+)\"");
      Matcher matcher = p.matcher(form);
      String loginUrl = null;
      if (matcher.find())
      {
         loginUrl = matcher.group(1);
      }
      Assert.assertNotNull(loginUrl);

      Form loginform = new Form()
              .param("username", "wburke")
              .param("Password", "userpassword")
              .param("client_id", "loginclient")
              .param("redirect_uri", "http://localhost:8081/loginclient/redirect");

      System.out.println("LoginUrl: " + loginUrl);
      Response response = client.target(loginUrl).request().post(Entity.form(loginform));
      Assert.assertEquals(302, response.getStatus());
      URI uri = response.getLocation();
      response.close();
      Assert.assertNotNull(uri);
      System.out.println(uri);
      Pattern q = Pattern.compile("code=([^&]+)");
      matcher = q.matcher(uri.getRawQuery());
      String code = null;
      if (matcher.find())
      {
         code = matcher.group(1);
      }
      System.out.println("Code: " + code);
      Assert.assertNotNull(code);
      WebTarget codes = client.target(realmInfo.getCodeUrl());
      Form codeForm = new Form()
              .param("code", code)
              .param("client_id", "loginclient")
              .param("Password", "clientpassword");
      Response res = codes.request().post(Entity.form(codeForm));
      if (res.getStatus() == 400)
      {
         System.out.println(res.readEntity(String.class));
      }
      Assert.assertEquals(200, res.getStatus());
      AccessTokenResponse tokenResponse = res.readEntity(AccessTokenResponse.class);
      res.close();

      ResourceMetadata metadata = new ResourceMetadata();
      metadata.setRealm("test-realm");
      metadata.setResourceName("Application");
      metadata.setRealmKey(realmInfo.getPublicKey());
      SkeletonKeyTokenVerification verification = RSATokenVerifier.verify((X509Certificate[])null, tokenResponse.getToken(), metadata);
      Assert.assertEquals(verification.getPrincipal().getName(), "wburke");
      Assert.assertTrue(verification.getRoles().contains("user"));
   }

   @Test
   public void testScoped() throws Exception
   {
      System.out.println(realmInfo.getAuthorizationUrl());
      WebTarget authUrl = client.target(realmInfo.getAuthorizationUrl())
              .queryParam("client_id", "oauthclient")
              .queryParam("redirect_uri", "http://localhost:8081/oauthclient/redirect");

      String form = authUrl.request().get(String.class);
      System.out.println(form);

      Pattern p = Pattern.compile("action=\"([^\"]+)\"");
      Matcher matcher = p.matcher(form);
      String loginUrl = null;
      if (matcher.find())
      {
         loginUrl = matcher.group(1);
      }
      Assert.assertNotNull(loginUrl);

      Pattern sp = Pattern.compile("name=\"scope\" value=\"([^\"]+)\"");
      matcher = sp.matcher(form);
      String scopeParam = null;
      if (matcher.find())
      {
         scopeParam = matcher.group(1);
      }
      Assert.assertNotNull(scopeParam);


      Form loginform = new Form()
              .param("username", "wburke")
              .param("Password", "userpassword")
              .param("client_id", "oauthclient")
              .param("scope", scopeParam)
              .param("redirect_uri", "http://localhost:8081/loginclient/redirect");

      System.out.println("LoginUrl: " + loginUrl);
      Response response = client.target(loginUrl).request().post(Entity.form(loginform));
      Assert.assertEquals(302, response.getStatus());
      URI uri = response.getLocation();
      response.close();
      Assert.assertNotNull(uri);
      System.out.println(uri);
      Pattern q = Pattern.compile("code=([^&]+)");
      matcher = q.matcher(uri.getRawQuery());
      String code = null;
      if (matcher.find())
      {
         code = matcher.group(1);
      }
      System.out.println("Code: " + code);
      Assert.assertNotNull(code);
      WebTarget codes = client.target(realmInfo.getCodeUrl());
      Form codeForm = new Form()
              .param("code", code)
              .param("client_id", "oauthclient")
              .param("Password", "clientpassword");
      Response res = codes.request().post(Entity.form(codeForm));
      if (res.getStatus() == 400)
      {
         System.out.println(res.readEntity(String.class));
      }
      Assert.assertEquals(200, res.getStatus());
      AccessTokenResponse tokenResponse = res.readEntity(AccessTokenResponse.class);
      res.close();

      ResourceMetadata metadata = new ResourceMetadata();
      metadata.setRealm("test-realm");
      metadata.setResourceName("Application");
      metadata.setRealmKey(realmInfo.getPublicKey());
      SkeletonKeyTokenVerification verification = RSATokenVerifier.verify((X509Certificate[])null, tokenResponse.getToken(), metadata);
      Assert.assertEquals(verification.getPrincipal().getName(), "wburke");
      Assert.assertTrue(verification.getRoles().contains("user"));
      metadata.setRealm("test-realm");
      metadata.setResourceName("OtherApp");
      metadata.setRealmKey(realmInfo.getPublicKey());
      try
      {
         verification = RSATokenVerifier.verify((X509Certificate[])null, tokenResponse.getToken(), metadata);
         Assert.fail("should not verify");
      }
      catch (VerificationException e)
      {
      }


   }

}
