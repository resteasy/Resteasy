package org.jboss.resteasy.client.jaxrs.engines;

import static org.junit.Assert.assertEquals;
import static org.jboss.resteasy.client.jaxrs.engines.ManualClosingApacheHttpClient43Engine.FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ManualClosingApacheHttpClient43EngineTest {

   private static final Map<String, String> preTestProperties = new HashMap<>();

   @BeforeClass
   public static void saveCurrentStateOfMemThresholdProperty()
   {
      if (System.getProperties().containsKey(FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY))
      {
         String value = System.getProperty(FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY);
         preTestProperties.put(FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY, value);
      }
   }

   @AfterClass
   public static void resetPropertiesToPreTestValues()
   {
      if (!preTestProperties.containsKey(FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY))
      {
         System.clearProperty(FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY);
      }
      else
      {
         preTestProperties.forEach(System::setProperty);
      }
   }

   @Before
   public void unsetMemThresholdProperty()
   {
      System.clearProperty(ManualClosingApacheHttpClient43Engine.FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY);
   }
   @Test
   public void testMemThresholdDefault()
   {
      assertEquals(1, new ManualClosingApacheHttpClient43Engine().getFileUploadInMemoryThresholdLimit());
   }

   @Test
   public void testMemThresholdConfigProperty()
   {
      System.setProperty(ManualClosingApacheHttpClient43Engine.FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY, "8");
      assertEquals(8, new ManualClosingApacheHttpClient43Engine().getFileUploadInMemoryThresholdLimit());
   }

   @Test
   public void testInvalidMemThresholdConfigPropertyReturnsDefault_NegativeNumber()
   {
      System.setProperty(ManualClosingApacheHttpClient43Engine.FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY, "-8");
      assertEquals(1, new ManualClosingApacheHttpClient43Engine().getFileUploadInMemoryThresholdLimit());
   }

   @Test
   public void testInvalidMemThresholdConfigPropertyReturnsDefault_DecimalNumber()
   {
      System.setProperty(ManualClosingApacheHttpClient43Engine.FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY, "2048.2");
      assertEquals(1, new ManualClosingApacheHttpClient43Engine().getFileUploadInMemoryThresholdLimit());
   }

   @Test
   public void testInvalidMemThresholdConfigPropertyReturnsDefault_NotANumber()
   {
      System.setProperty(ManualClosingApacheHttpClient43Engine.FILE_UPLOAD_IN_MEMORY_THRESHOLD_PROPERTY, "Infinity");
      assertEquals(1, new ManualClosingApacheHttpClient43Engine().getFileUploadInMemoryThresholdLimit());
   }
}
