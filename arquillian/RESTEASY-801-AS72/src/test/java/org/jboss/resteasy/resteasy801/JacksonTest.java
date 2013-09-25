package org.jboss.resteasy.resteasy801;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.client.ProxyFactory;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@RunWith(Arquillian.class)
public class JacksonTest
{
	@Deployment
	public static Archive<?> createTestArchive()
	{
		WebArchive war = ShrinkWrap.create(WebArchive.class, "RESTEASY-801.war")
				.addClasses(JaxRsActivator.class)
				.addClasses(JacksonProxy.class, Product.class, JacksonService.class)
				.addClasses(XmlProduct.class, XmlService.class)
//				.addAsWebInfResource("web.xml");
				;
		System.out.println(war.toString(true));
		return war;
	}

   @Test
   public void testJacksonString() throws Exception
   {
      ClientRequest request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products/333");
      ClientResponse<String> response = request.get(String.class);
      System.out.println(response.getEntity());
      Assert.assertEquals(200, response.getStatus());
      Assert.assertEquals("{\"name\":\"Iphone\",\"id\":333}", response.getEntity());

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products");
      ClientResponse<String> response2 = request.get(String.class);
      System.out.println(response2.getEntity());
      Assert.assertEquals(200, response2.getStatus());
      Assert.assertEquals("[{\"name\":\"Iphone\",\"id\":333},{\"name\":\"macbook\",\"id\":44}]", response2.getEntity());

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products/333?callback=product");
      ClientResponse<String> response3 = request.get(String.class);
      System.out.println(response3.getEntity());
      Assert.assertEquals(200, response3.getStatus());
      Assert.assertEquals("product({\"name\":\"Iphone\",\"id\":333})", response3.getEntity());
      response3.releaseConnection();

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products?callback=products");
      ClientResponse<String> response4 = request.get(String.class);
      System.out.println(response4.getEntity());
      Assert.assertEquals(200, response4.getStatus());
      Assert.assertEquals("products([{\"name\":\"Iphone\",\"id\":333},{\"name\":\"macbook\",\"id\":44}])", response4.getEntity());
      response4.releaseConnection();
   }

   @Test
   public void testXmlString() throws Exception
   {
	  ClientRequest request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/xml/products/333");
      ClientResponse<String> response = request.get(String.class);
      System.out.println(response.getEntity());
      Assert.assertEquals(200, response.getStatus());
      Assert.assertTrue(response.getEntity().startsWith("{\"product"));

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/xml/products");
      ClientResponse<String> response2 = request.get(String.class);
      System.out.println(response2.getEntity());
      Assert.assertEquals(200, response2.getStatus());
      Assert.assertTrue(response2.getEntity().startsWith("[{\"product"));

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/xml/products/333?callback=product");
      ClientResponse<String> response3 = request.get(String.class);
      System.out.println(response3.getEntity());
      Assert.assertEquals(200, response3.getStatus());
      Assert.assertTrue(response3.getEntity().startsWith("product({\"product"));
      response3.releaseConnection();

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/xml/products?callback=products");
      ClientResponse<String> response4 = request.get(String.class);
      System.out.println(response4.getEntity());
      Assert.assertEquals(200, response4.getStatus());
      Assert.assertTrue(response4.getEntity().startsWith("products([{\"product"));
      response4.releaseConnection();
   }

   @Test
   public void testJackson() throws Exception
   {
      ClientRequest request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products/333");
      ClientResponse<Product> response = request.get(Product.class);
      Product p = response.getEntity();
      Assert.assertEquals(333, p.getId());
      Assert.assertEquals("Iphone", p.getName());
      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products");
      ClientResponse<String> response2 = request.get(String.class);
      System.out.println(response2.getEntity());
      Assert.assertEquals(200, response2.getStatus());

      request = new ClientRequest("http://localhost:8080/RESTEASY-801/rest/products/333");
      request.body("application/foo+json", p);
      response = request.post(Product.class);
      p = response.getEntity();
      Assert.assertEquals(333, p.getId());
      Assert.assertEquals("Iphone", p.getName());


   }
/*

// todo find a better way of doing JAXB + jackson

    @XmlRootElement
    public static class XmlResourceWithJAXB {
        String attr1;
        String attr2;

        @XmlElement(name = "attr_1")
        public String getAttr1() {
            return attr1;
        }

        public void setAttr1(String attr1) {
            this.attr1 = attr1;
        }

        @XmlElement
        public String getAttr2() {
            return attr2;
        }

        public void setAttr2(String attr2) {
            this.attr2 = attr2;
        }
    }

    public static class XmlResourceWithJacksonAnnotation {
        String attr1;
        String attr2;

        @JsonProperty("attr_1")
        public String getAttr1() {
            return attr1;
        }

        public void setAttr1(String attr1) {
            this.attr1 = attr1;
        }

        @XmlElement
        public String getAttr2() {
            return attr2;
        }

        public void setAttr2(String attr2) {
            this.attr2 = attr2;
        }
    }


    @Path("/jaxb")
    public static class JAXBService {

        @GET
        @Produces("application/json")
        public XmlResourceWithJAXB getJAXBResource() {
            XmlResourceWithJAXB resourceWithJAXB = new XmlResourceWithJAXB();
            resourceWithJAXB.setAttr1("XXX");
            resourceWithJAXB.setAttr2("YYY");
            return resourceWithJAXB;
        }

        @GET
        @Path(("/json"))
        @Produces("application/json")
        public XmlResourceWithJacksonAnnotation getJacksonAnnotatedResource() {
            XmlResourceWithJacksonAnnotation resource = new XmlResourceWithJacksonAnnotation();
            resource.setAttr1("XXX");
            resource.setAttr2("YYY");
            return resource;
        }


    }

    @Test
    public void testJacksonJAXB() throws Exception {

        {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(generateBaseUrl() + "/jaxb");
            HttpResponse response = client.execute(get);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            Assert.assertTrue(reader.readLine().contains("attr_1"));
        }

        {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(generateBaseUrl() + "/jaxb/json");
            HttpResponse response = client.execute(get);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            Assert.assertTrue(reader.readLine().contains("attr_1"));

        }

    }
*/

   @Test
   public void testJacksonProxy() throws Exception
   {
	  JacksonProxy proxy = ProxyFactory.create(JacksonProxy.class, "http://localhost:8080/RESTEASY-801/rest");
      Product p = new Product(1, "Stuff");
      p = proxy.post(1, p);
      Assert.assertEquals(1, p.getId());
      Assert.assertEquals("Stuff", p.getName());
   }
}
