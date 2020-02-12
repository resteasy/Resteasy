package org.jboss.resteasy.core.messagebody;

import org.jboss.resteasy.core.interception.jaxrs.AbstractWriterInterceptorContext;
import org.jboss.resteasy.core.interception.jaxrs.ClientWriterInterceptorContext;
import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.WriterInterceptor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for accessing RESTEasy's MessageBodyWrite setup
 *
 * @author <a href="mailto:sduskis@gmail.com">Solomon Duskis</a>
 * @version $Revision: 1 $
 */
@Deprecated
public abstract class WriterUtility
{
   private ResteasyProviderFactory factory;
   private WriterInterceptor[] interceptors;

   public static String asString(Object toOutput, String contentType)
         throws IOException
   {
      return new String(getBytes(toOutput, contentType));
   }

   public static byte[] getBytes(Object toOutput, String contentType)
         throws IOException
   {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      write(toOutput, MediaType.valueOf(contentType), bos);
      return bos.toByteArray();
   }

   public static void write(Object toOutput, MediaType mt, OutputStream os)
         throws IOException
   {
      new WriterUtility()
      {
         @Override
         public RuntimeException createWriterNotFound(Type genericType,
                                                      MediaType mediaType)
         {
            throw new RuntimeException(Messages.MESSAGES.couldNotReadType(genericType, mediaType));
         }
      }.doWrite(toOutput, mt, os);
   }

   public WriterUtility()
   {
      this(ResteasyProviderFactory.getInstance(), null);
   }

   public WriterUtility(final ResteasyProviderFactory factory,
                        final WriterInterceptor[] interceptors)
   {
      this.factory = factory;
      this.interceptors = interceptors;
   }

   public void doWrite(Object toOutput, MediaType mediaType, OutputStream os)
         throws IOException
   {
      doWrite(toOutput, toOutput.getClass(), mediaType, os);
   }


   @SuppressWarnings("rawtypes")
   public void doWrite(Object toOutput, Class type, MediaType mediaType, OutputStream os)
         throws IOException
   {
      doWrite(toOutput, type, type, mediaType, null, null, os);
   }

   @SuppressWarnings("rawtypes")
   public void doWrite(Object toOutput, Class type, Type genericType, MediaType mediaType,
                       MultivaluedMap<String, Object> requestHeaders, OutputStream os)
         throws IOException
   {
      doWrite(toOutput, type, genericType, mediaType, null, requestHeaders, os);
   }

   @SuppressWarnings("rawtypes")
   public void doWrite(HttpResponse response, Object toOutput, Class type, Type genericType,
                       Annotation[] annotations, MediaType mediaType) throws IOException
   {
      doWrite(toOutput, type, genericType, mediaType, annotations, response
              .getOutputHeaders(), response.getOutputStream());
   }

   @SuppressWarnings("rawtypes")
   public void doWrite(Object toOutput, Class type, Type genericType,
                       MediaType mediaType, Annotation[] annotations,
                       MultivaluedMap<String, Object> requestHeaders,
                       OutputStream outputStream) throws IOException
   {
      final Map<String, Object> attributes = new HashMap<String, Object>();
      AbstractWriterInterceptorContext messageBodyWriterContext = new ClientWriterInterceptorContext(interceptors, factory, toOutput, type,
              genericType, annotations, mediaType, requestHeaders, outputStream, attributes, null);
      messageBodyWriterContext
              .proceed();
   }

   public abstract RuntimeException createWriterNotFound(Type genericType,
                                                         MediaType mediaType);
}
