package org.jboss.resteasy.plugins.providers.jsonb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Priority;
import javax.json.bind.Jsonb;
import javax.ws.rs.Consumes;
import javax.ws.rs.Priorities;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.input.ProxyInputStream;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.plugins.providers.jsonb.i18n.LogMessages;
import org.jboss.resteasy.plugins.providers.jsonb.i18n.Messages;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;
import org.jboss.resteasy.spi.AsyncMessageBodyWriter;
import org.jboss.resteasy.spi.AsyncOutputStream;
import org.jboss.resteasy.spi.ResteasyConfiguration;
import org.jboss.resteasy.util.DelegatingOutputStream;

/**
 * Created by rsearls on 6/26/17.
 */
@Provider
@Produces({"application/json", "application/*+json", "text/json"})
@Consumes({"application/json", "application/*+json", "text/json"})
@Priority(Priorities.USER-100)
public class JsonBindingProvider extends AbstractJsonBindingProvider
      implements MessageBodyReader<Object>, AsyncMessageBodyWriter<Object> {

   private final boolean disabled;

   public JsonBindingProvider() {
      super();
      ResteasyConfiguration context = ResteasyContext.getContextData(ResteasyConfiguration.class);
      if (context == null) {
         disabled = Boolean.getBoolean(ResteasyContextParameters.RESTEASY_PREFER_JACKSON_OVER_JSONB);
      } else {
         disabled = (Boolean.parseBoolean(context.getParameter(ResteasyContextParameters.RESTEASY_PREFER_JACKSON_OVER_JSONB))
                 || Boolean.parseBoolean(context.getParameter("resteasy.jsonp.enable")));
      }
   }

   @Override
   public boolean isReadable(Class<?> type, Type genericType,
                             Annotation[] annotations, MediaType mediaType) {
      if (disabled)
      {
         return false;
      }
      return isSupportedMediaType(mediaType);
   }

   @Override
   public Object readFrom(Class<Object> type, Type genericType,
                                 Annotation[] annotations, MediaType mediaType,
                                 MultivaluedMap<String, String> httpHeaders,
                                 InputStream entityStream) throws java.io.IOException, javax.ws.rs.WebApplicationException {
      final EmptyCheckInputStream is = new EmptyCheckInputStream(entityStream);

      try (Jsonb jsonb = getJsonb(type)) {
         return jsonb.fromJson(is, genericType);
         // If null is returned, considered to be empty stream
      } catch (Throwable e)
      {
         if (is.isEmpty()) {
            return null;
         }
         // detail text provided in logger message
         throw new ProcessingException(Messages.MESSAGES.jsonBDeserializationError(e.toString()), e);
      }
   }

   private class EmptyCheckInputStream extends ProxyInputStream
   {
      boolean read = false;
      boolean empty = false;

      EmptyCheckInputStream(final InputStream proxy)
      {
         super(proxy);
      }

      @Override
      protected synchronized void afterRead(final int n) throws IOException {
         if (!read && n <= 0) {
            empty = true;
         }
         read = true;
      }

      public boolean isEmpty() {
         return empty;
      }
   };

   @Override
   public boolean isWriteable(Class<?> type, Type genericType,
                              Annotation[] annotations, MediaType mediaType) {
      if (disabled)
      {
         return false;
      }
      return isSupportedMediaType(mediaType);
   }

   @Override
   public long getSize(Object t, Class<?> type, Type genericType, Annotation[] annotations,
                       MediaType mediaType) {
      return -1L;
   }

   @Override
   public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations,
                       MediaType mediaType,
                       MultivaluedMap<String, Object> httpHeaders,
                       OutputStream entityStream)
         throws java.io.IOException, javax.ws.rs.WebApplicationException {
      try (Jsonb jsonb = getJsonb(type))
      {
         entityStream = new DelegatingOutputStream(entityStream) {
            @Override
            public void flush() throws IOException {
               // don't flush as this is a performance hit on Undertow.
               // and causes chunked encoding to happen.
            }
         };
         entityStream.write(jsonb.toJson(t).getBytes(getCharset(mediaType)));
         entityStream.flush();
      } catch (Throwable e)
      {
         throw new ProcessingException(Messages.MESSAGES.jsonBSerializationError(e.toString()), e);
      }
   }

   @Override
   public CompletionStage<Void> asyncWriteTo(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                                             MultivaluedMap<String, Object> httpHeaders, AsyncOutputStream entityStream) {
      Jsonb jsonb = getJsonb(type);
      try
      {
         return entityStream.asyncWrite(jsonb.toJson(t).getBytes(getCharset(mediaType)))
                 .whenComplete((unused, throwable) -> {
                    try {
                       jsonb.close();
                    } catch (Exception e) {
                       LogMessages.LOGGER.debug("Failed to close the JSONB context.", e);
                    }
                 });
      } catch (Throwable e)
      {
         CompletableFuture<Void> ret = new CompletableFuture<>();
         ret.completeExceptionally(new ProcessingException(Messages.MESSAGES.jsonBSerializationError(e.toString()), e));
         return ret;
      }
   }
}
