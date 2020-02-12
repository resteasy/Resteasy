package org.jboss.resteasy.specimpl;

import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.interception.jaxrs.AbstractReaderInterceptorContext;
import org.jboss.resteasy.core.interception.jaxrs.ClientReaderInterceptorContext;
import org.jboss.resteasy.plugins.providers.sse.EventInput;
import org.jboss.resteasy.resteasy_jaxrs.i18n.LogMessages;
import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;
import org.jboss.resteasy.spi.HttpResponseCodes;
import org.jboss.resteasy.spi.MarshalledEntity;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.util.Types;
import org.jboss.resteasy.util.InputStreamToByteArray;
import org.jboss.resteasy.util.ReadFromStream;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ReaderInterceptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;

/**
 * A response object not attached to a client or server invocation.
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class BuiltResponse extends AbstractBuiltResponse
{

   public BuiltResponse()
   {
      super();
   }

   public BuiltResponse(final int status, final Headers<Object> metadata,
                        final Object entity, final Annotation[] entityAnnotations)
   {
      this(status, null, metadata, entity, entityAnnotations);
   }

   public BuiltResponse(final int status, final String reason,
                        final Headers<Object> metadata, final Object entity,
                        final Annotation[] entityAnnotations)
   {
      super(status, reason, metadata, entity, entityAnnotations);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T readEntity(Class<T> type, Type genericType, Annotation[] anns)
   {
      abortIfClosed();
      if (entity != null)
      {
         if (type.isInstance((this.entity)))
         {
            return (T)entity;
         }
         else if (entity instanceof InputStream)
         {
            setInputStream((InputStream)entity);
            entity = null;
         }
         else if (bufferedEntity == null)
         {
            throw new RuntimeException(Messages.MESSAGES.entityAlreadyRead(entity.getClass()));
         }
         else
         {
            entity = null;
         }
      }

      if (entity == null)
      {
         if (status == HttpResponseCodes.SC_NO_CONTENT)
            return null;

         try
         {
            entity = readFrom(type, genericType, getMediaType(), anns);
            if (entity == null || (entity != null
                    && !InputStream.class.isInstance(entity)
                    && !Reader.class.isInstance(entity)
                    && bufferedEntity == null))
            {
               try
               {
                  if (!EventInput.class.isInstance(entity))
                  {
                     close();
                  }
               }
               catch (Exception ignored)
               {
               }
            }
         }
         catch (RuntimeException e)
         {
            try
            {
               close();
            }
            catch (Exception ignored)
            {

            }
            LogMessages.LOGGER.clientReceiveProcessingFailure(e);
            throw e;
         }
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

         ResteasyProviderFactory providerFactory = ResteasyProviderFactory.peekInstance();
         ReaderInterceptor[] readerInterceptors = providerFactory
                 .getClientReaderInterceptorRegistry()
                 .postMatch(null, null);

         final Object finalObj;
         AbstractReaderInterceptorContext context = new ClientReaderInterceptorContext(
                 readerInterceptors, providerFactory, useType,
                 useGeneric, annotations, media, getStringHeaders(), is,
                 new HashMap<String, Object>(), null);

         finalObj = context.proceed();

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
   }

   protected InputStream getEntityStream()
   {
      if (bufferedEntity != null) {
         return new ByteArrayInputStream(bufferedEntity);
      }
      if (isClosed()) {
         throw new ProcessingException(Messages.MESSAGES.streamIsClosed());
      }
      InputStream is = getInputStream();
      return is != null ? new AbstractBuiltResponse.InputStreamWrapper<BuiltResponse>(is, this) : null;
   }

   protected void setInputStream(InputStream is)
   {
      this.is = is;
      resetEntity();
   }

   protected InputStream getInputStream()
   {
      if (is == null && entity != null && entity instanceof InputStream) {
         is = (InputStream) entity;
      }
      return is;
   }

   @Override
   public void releaseConnection() throws IOException
   {
      releaseConnection(false);
   }

   @Override
   public void releaseConnection(boolean consumeInputStream) throws IOException
   {
      try
      {
         if (is != null)
         {
            if (consumeInputStream)
            {
               while (is.read() > 0)
               {
               }
            }
            is.close();
            is = null;
         }
      }
      catch (IOException e)
      {

      }

   }

   @Override
   public boolean bufferEntity()
   {
      abortIfClosed();

      if (bufferedEntity != null) return true;
      if (streamRead) return false;
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
}
