package org.jboss.resteasy.client.jaxrs.internal;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.ReaderInterceptor;

import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ProvidersContextRetainer;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.interception.jaxrs.AbstractReaderInterceptorContext;
import org.jboss.resteasy.core.interception.jaxrs.ClientReaderInterceptorContext;
import org.jboss.resteasy.plugins.providers.sse.EventInput;
import org.jboss.resteasy.specimpl.BuiltResponse;
import org.jboss.resteasy.spi.HeaderValueProcessor;
import org.jboss.resteasy.spi.HttpResponseCodes;
import org.jboss.resteasy.spi.MarshalledEntity;
import org.jboss.resteasy.spi.util.Types;
import org.jboss.resteasy.tracing.RESTEasyTracingLogger;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.jboss.resteasy.util.InputStreamToByteArray;
import org.jboss.resteasy.util.ReadFromStream;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class ClientResponse extends BuiltResponse
{
   // One thing to note, I don't cache header objects because I was too lazy to proxy the headers multivalued map
   protected Map<String, Object> properties;
   protected ClientConfiguration configuration;
   protected byte[] bufferedEntity;
   protected volatile boolean streamRead;
   protected volatile boolean streamFullyRead;
   protected RESTEasyTracingLogger tracingLogger;

   @Deprecated
   protected ClientResponse(final ClientConfiguration configuration)
   {
      setClientConfiguration(configuration);
      tracingLogger = RESTEasyTracingLogger.empty();
   }

   protected ClientResponse(final ClientConfiguration configuration, final RESTEasyTracingLogger tracingLogger)
   {
      setClientConfiguration(configuration);
      this.tracingLogger = tracingLogger;
   }

   @SuppressWarnings({ "rawtypes", "unchecked" })
   public void setHeaders(MultivaluedMap<String, String> headers)
   {
      this.metadata = new Headers<Object>();
      this.metadata.putAll((Map)headers);
   }

   public void setProperties(Map<String, Object> properties)
   {
      this.properties = properties;
   }

   public Map<String, Object> getProperties()
   {
      return properties;
   }

   public void setClientConfiguration(ClientConfiguration configuration)
   {
      this.configuration = configuration;
      this.processor = configuration;
   }

   @Override
   public synchronized Object getEntity()
   {
      abortIfClosed();
      Object entity = super.getEntity();
      if (entity != null)
      {
         return checkEntityReadAsInputStreamFullyConsumed(entity);
      }
      return checkEntityReadAsInputStreamFullyConsumed(getEntityStream());
   }

   //Check if the entity was previously fully consumed
   private <T> T checkEntityReadAsInputStreamFullyConsumed(T entity)
   {
      if (bufferedEntity == null && entity instanceof InputStream && streamFullyRead)
      {
         throw new IllegalStateException();
      }
      return entity;
   }

   @Override
   public Class<?> getEntityClass()
   {
      Class<?> classs = super.getEntityClass();
      if (classs != null)
      {
         return classs;
      }
      Object entity = null;
      try
      {
         entity = getEntity();
      }
      catch (Exception e)
      {
      }
      return entity != null ? entity.getClass() : null;
   }

   @Override
   public boolean hasEntity()
   {
      abortIfClosed();
      return getInputStream() != null && (entity != null || getMediaType() != null);
   }

   /**
    * In case of an InputStream or Reader and a invocation that returns no Response object, we need to make
    * sure the GC does not close the returned InputStream or Reader
    */
   public void noReleaseConnection()
   {

      isClosed = true;
   }

   @Override
   public void close()
   {
      if (isClosed()) return;
      try {
         isClosed = true;
         releaseConnection();
      }
      catch (Exception e) {
         throw new ProcessingException(e);
      }
   }

   @Override
   // This method is synchronized to protect against premature calling of finalize by the GC
   protected synchronized void finalize() throws Throwable
   {
      if (isClosed()) return;
      try {
         close();
      }
      catch (Exception ignored) {
      }
   }

   @Override
   protected HeaderValueProcessor getHeaderValueProcessor()
   {
      return configuration;
   }

   protected abstract InputStream getInputStream();

   protected InputStream getEntityStream()
   {
      if (bufferedEntity != null) return new ByteArrayInputStream(bufferedEntity);
      if (isClosed()) throw new ProcessingException(Messages.MESSAGES.streamIsClosed());
      InputStream is = getInputStream();
      return is != null ? new InputStreamWrapper(is, this) : null;
   }

   private static class InputStreamWrapper extends FilterInputStream {

      private ClientResponse response;
      private boolean isClosed;

      protected InputStreamWrapper(final InputStream in, final ClientResponse response) {
         super(in);
         this.response = response;
      }

      public int read() throws IOException
      {
         return checkEOF(super.read());
      }

      public int read(byte[] b) throws IOException
      {
         return checkEOF(super.read(b));
      }

      public int read(byte[] b, int off, int len) throws IOException
      {
         return checkEOF(super.read(b, off, len));
      }

      private int checkEOF(int v)
      {
         response.streamRead=true;
         if (v < 0)
         {
            response.streamFullyRead = true;
         }
         return v;
      }

      @Override
      public void close() throws IOException {
         //check to prevent StackOverflowException when this.response.releaseConnection() will be invoked
         if(isClosed){
            return;
         }
         isClosed=true;
         response.streamRead=true;
         response.streamFullyRead = true;
         super.close();
         //Is this really needed ?
         this.response.releaseConnection();
      }
   }

   protected abstract void setInputStream(InputStream is);

   /**
    * Release underlying connection but do not close.
    *
    * @throws IOException if I/O error occurred
    */
   public abstract void releaseConnection() throws IOException;

   /**
    * Release underlying connection but do not close.
    *
    * @param consumeInputStream boolean to indicate either the underlying input stream must be fully read before releasing the connection or not.
    * <p>
    * For most HTTP connection implementations, consuming the underlying input stream before releasing the connection will help to ensure connection reusability with respect of Keep-Alive policy.
    * </p>
    * @throws IOException if I/O error occured
    */
   public abstract void releaseConnection(boolean consumeInputStream) throws IOException;

   // this is synchronized in conjunction with finalize to protect against premature finalize called by the GC
   @SuppressWarnings("unchecked")
   public synchronized <T> T readEntity(Class<T> type, Type genericType, Annotation[] anns)
   {
      abortIfClosed();
      if (entity instanceof InputStream && bufferedEntity == null)
      {
         setInputStream(checkEntityReadAsInputStreamFullyConsumed((InputStream) entity));
         entity = null;
      }
      else if (bufferedEntity == null && this.streamFullyRead)
      {
         if (entity != null)
         {
            throw new IllegalStateException(Messages.MESSAGES.entityAlreadyRead(entity.getClass()));
         }
         throw new IllegalStateException();
      }
      else
      {
         entity = null;
      }

      if (status == HttpResponseCodes.SC_NO_CONTENT)
         return null;

      try
      {
         entity = readFrom(type, genericType, getMediaType(), anns);
         if (entity == null || (!InputStream.class.isInstance(entity) && !Reader.class.isInstance(entity)
               && !EventInput.class.isInstance(entity) && bufferedEntity == null))
         {
            try
            {
               releaseConnection();
            }
            catch (Exception ignored)
            {
            }
         }
      }
      catch (RuntimeException e)
      {
         //logger.error("failed", e);
         try
         {
            releaseConnection();
         }
         catch (Exception ignored)
         {

         }
         throw e;
      }
      return (T) entity;
   }

   // this is synchronized in conjunction with finalize to protect against premature finalize called by the GC
   protected synchronized <T> Object readFrom(Class<T> type, Type genericType,
                                  MediaType media, Annotation[] annotations)
   {
      Type useGeneric = genericType == null ? type : genericType;
      Class<?> useType = type;
      media = media == null ? MediaType.WILDCARD_TYPE : media;
      annotations = annotations == null ? this.annotations : annotations;
      boolean isMarshalledEntity = false;
      if (type.equals(MarshalledEntity.class))
      {
         isMarshalledEntity = true;
         ParameterizedType param = (ParameterizedType) useGeneric;
         useGeneric = param.getActualTypeArguments()[0];
         useType = Types.getRawType(useGeneric);
      }


      Providers current = ResteasyContext.getContextData(Providers.class);
      ResteasyContext.pushContext(Providers.class, configuration);
      Object obj = null;
      try
      {
         InputStream is = getEntityStream();
         if (is == null)
         {
            throw new IllegalStateException(Messages.MESSAGES.inputStreamWasEmpty());
         }
         if (isMarshalledEntity)
         {
            is = new InputStreamToByteArray(is);

         }

         ReaderInterceptor[] readerInterceptors = configuration.getReaderInterceptors(null, null);

         final Object finalObj;

         final long timestamp = tracingLogger.timestamp("RI_SUMMARY");
         AbstractReaderInterceptorContext context = new ClientReaderInterceptorContext(readerInterceptors, configuration.getProviderFactory(), useType,
                 useGeneric, annotations, media, getStringHeaders(), is, properties);
         try {
            finalObj = context.proceed();
            obj = finalObj;
         } finally {
            tracingLogger.logDuration("RI_SUMMARY", timestamp, context.getProcessedInterceptorCount());
         }

         if (isMarshalledEntity)
         {
            InputStreamToByteArray isba = (InputStreamToByteArray) is;
            final byte[] bytes = isba.toByteArray();
            return new MarshalledEntity<Object>()
            {
               @Override
               public byte[] getMarshalledBytes()
               {
                  return bytes;
               }

               @Override
               public Object getEntity()
               {
                  return finalObj;
               }
            };
         }
         else
         {
            return finalObj;
         }

      }
      catch (ProcessingException pe)
      {
         throw pe;
      }
      catch (Exception ex)
      {
         throw new ProcessingException(ex);
      }
      finally
      {
         ResteasyContext.popContextData(Providers.class);
         if (current != null) ResteasyContext.pushContext(Providers.class, current);
         if (obj instanceof ProvidersContextRetainer)
         {
            ((ProvidersContextRetainer) obj).setProviders(configuration);
         }
      }
   }

   @Override
   public boolean bufferEntity()
   {
      abortIfClosed();
      if (bufferedEntity != null) return true;
      if (streamRead) return false;
      if (metadata.getFirst(HttpHeaderNames.CONTENT_TYPE) == null) return false;
      InputStream is = getInputStream();
      if (is == null) return false;
      try
      {
         bufferedEntity = ReadFromStream.readFromStream(1024, is);
      }
      catch (IOException e)
      {
         throw new ProcessingException(e);
      }
      finally
      {
         try {
            releaseConnection();
         }
         catch (IOException e) {
            throw new ProcessingException(e);
         }
      }
      return true;
   }

   protected void resetEntity()
   {
      entity = null;
      bufferedEntity = null;
      streamFullyRead = false;
   }

   @Override
   public void abortIfClosed()
   {
      if (bufferedEntity == null) super.abortIfClosed();
   }

}
