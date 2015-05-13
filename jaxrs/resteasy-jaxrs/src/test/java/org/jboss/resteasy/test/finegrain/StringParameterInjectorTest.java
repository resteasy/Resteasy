package org.jboss.resteasy.test.finegrain;

import org.jboss.resteasy.annotations.StringParameterUnmarshallerBinder;
import org.jboss.resteasy.core.StringParameterInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.junit.Test;

import javax.ws.rs.core.Context;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class StringParameterInjectorTest
{

   private static final String MY_SPECIAL_STRING = "MySpecialString";

   @Test
   public void shouldInjectForAnnotationConfiguredUnmarshaller() throws Exception
   {
      ResteasyProviderFactory.pushContext(Injected.class, new Injected(MY_SPECIAL_STRING));

      Field declaredField = MyType.class.getDeclaredField("name");
      StringParameterInjector injector = new StringParameterInjector(String.class, String.class, "name",
              MyType.class, null, declaredField,
              declaredField.getAnnotations(), new ResteasyProviderFactory());

      assertSame(MY_SPECIAL_STRING, injector.extractValue("ignored"));
   }

   public static class MyType
   {
      @SpecialString
      public String name;
   }

   public static class SpecialStringUnmarshaller implements
           StringParameterUnmarshaller<String>
   {

      @Context
      private Injected in;

      @Override
      public void setAnnotations(Annotation[] annotations)
      {
      }

      @Override
      public String fromString(String str)
      {
         return in.value;
      }

   }

   public static class Injected
   {

      private final String value;

      public Injected(String value)
      {
         this.value = value;
      }

   }

   @Retention(RetentionPolicy.RUNTIME)
   @StringParameterUnmarshallerBinder(SpecialStringUnmarshaller.class)
   public static @interface SpecialString
   {
   }
   
   // ***************************************************************************
   
   @Test
   public void instantiation() throws Exception
   {
      final Type type = GenericType.class.getDeclaredMethod("returnSomething").getGenericReturnType();
      final StringParameterInjector injector = new StringParameterInjector(
               List.class, type, "ignored", String.class, null, null,
               new Annotation[0], new ResteasyProviderFactory());
      final Object result = injector.extractValue("");
      assertNotNull(result);
   }
   
   public static class GenericType<T>
   {
      public GenericType(String ignore)
      {
      }

      public List<GenericType<T>> returnSomething()
      {
         return null;
      }
   }

   // ***************************************************************************

   @Test
   public void usesParamConverterEvenIfNull(){
      ResteasyProviderFactory providerFactory = new ResteasyProviderFactory();
      providerFactory.registerProviderInstance(new TestParamConverterProvider());
      final StringParameterInjector injector = new StringParameterInjector(
              String.class, null, "ignored", String.class, null, null,
              new Annotation[0], providerFactory);
      assertEquals("it was null", injector.extractValue(null));
      assertEquals("asdf", injector.extractValue("asdf"));

   }

   public static class TestParamConverterProvider implements ParamConverterProvider {
      @Override
      public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
         return (ParamConverter<T>) new TestParamConverter();
      }
   }

   public static class TestParamConverter implements ParamConverter<String> {

      @Override
      public String fromString(String value) {
         if (value == null){
            return "it was null";
         }
         return value;
      }

      @Override
      public String toString(String value) {
         return value;
      }
   }
}

