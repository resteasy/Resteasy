package org.jboss.resteasy.plugins.providers.sse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.SseEventSink;

import org.jboss.resteasy.annotations.SseElementType;
import org.jboss.resteasy.annotations.Stream;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.ServerResponseWriter;
import org.jboss.resteasy.plugins.server.Cleanable;
import org.jboss.resteasy.plugins.server.Cleanables;
import org.jboss.resteasy.resteasy_jaxrs.i18n.LogMessages;
import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;
import org.jboss.resteasy.specimpl.BuiltResponse;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyAsynchronousContext;
import org.jboss.resteasy.spi.ResteasyAsynchronousResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

public class SseEventOutputImpl extends GenericType<OutboundSseEvent> implements SseEventSink
{
   private final MessageBodyWriter<OutboundSseEvent> writer;

   private final ResteasyAsynchronousContext asyncContext;

   private final HttpResponse response;

   private final HttpRequest request;

   private volatile boolean closed;

   private final Map<Class<?>, Object> contextDataMap;

   private boolean responseFlushed = false;

   private final Object lock = new Object();

   public SseEventOutputImpl(final MessageBodyWriter<OutboundSseEvent> writer)
   {
      this.writer = writer;
      contextDataMap = ResteasyContext.getContextDataMap();

      request = ResteasyContext.getContextData(org.jboss.resteasy.spi.HttpRequest.class);
      asyncContext = request.getAsyncContext();

      if (!asyncContext.isSuspended())
      {
         try
         {
            asyncContext.suspend();
         }
         catch (IllegalStateException ex)
         {
            LogMessages.LOGGER.failedToSetRequestAsync();
         }
      }

      response = ResteasyContext.getContextData(HttpResponse.class);
   }

   @Override
   public void close()
   {
      synchronized (lock)
      {
         closed = true;
         if (asyncContext.isSuspended())
         {
            ResteasyAsynchronousResponse asyncResponse = asyncContext.getAsyncResponse();
            if (asyncResponse != null)
            {
               asyncResponse.complete();
            }
         }
         clearContextData();
      }
   }

   public void clearContextData()
   {
      Map<Class<?>, Object> map = ResteasyContext.getContextDataMap(false);
      Cleanables cleanables = map != null ? (Cleanables) map.get(Cleanables.class) : null;
      if (cleanables != null)
      {
         for (Iterator<Cleanable> it = cleanables.getCleanables().iterator(); it.hasNext(); )
         {
            try
            {
               it.next().clean();
            }
            catch(Exception e)
            {
               // Empty
            }
         }
         ResteasyContext.clearContextData();
      }
   }

   protected void flushResponseToClient()
   {
      try
      {
         internalFlushResponseToClient(false);
      }
      catch (IOException e)
      {
      }
   }

   private void internalFlushResponseToClient(boolean throwIOException) throws IOException
   {
      synchronized (lock)
      {
         if (!responseFlushed)
         {
            BuiltResponse jaxrsResponse = null;
            if (this.closed)
            {
               jaxrsResponse = (BuiltResponse) Response.noContent().build();
            }
            else //set back to client 200 OK to implies the SseEventOutput is ready
            {
               ResourceMethodInvoker method =(ResourceMethodInvoker) request.getAttribute(ResourceMethodInvoker.class.getName());
               Produces produces = method.getMethod().getAnnotation(Produces.class);
               if (produces != null && contains(produces.value(), MediaType.SERVER_SENT_EVENTS))
               {
                  // @Produces("text/event-stream")
                  SseElementType sseElementType = method.getMethod().getAnnotation(SseElementType.class);
                  if (sseElementType != null)
                  {
                     // Get element media type from @SseElementType.
                     Map<String, String> parameterMap = new HashMap<String, String>();
                     parameterMap.put(SseConstants.SSE_ELEMENT_MEDIA_TYPE, sseElementType.value());
                     MediaType mediaType = new MediaType(MediaType.SERVER_SENT_EVENTS_TYPE.getType(), MediaType.SERVER_SENT_EVENTS_TYPE.getSubtype(), parameterMap);
                     jaxrsResponse = (BuiltResponse) Response.ok().type(mediaType).build();
                  }
                  else
                  {
                     // No element media type declared.
                     jaxrsResponse = (BuiltResponse) Response.ok().type(MediaType.SERVER_SENT_EVENTS).build();
//                   // use "element-type=text/plain"?
                  }
               }
               else
               {
                  Stream stream = method.getMethod().getAnnotation(Stream.class);
                  if (stream != null)
                  {
                     // Get element media type from @Produces.
                     jaxrsResponse = (BuiltResponse) Response.ok("").build();
                     MediaType elementType = ServerResponseWriter.getResponseMediaType(jaxrsResponse, request, response, ResteasyProviderFactory.peekInstance(), method);
                     Map<String, String> parameterMap = new HashMap<String, String>();
                     parameterMap.put(SseConstants.SSE_ELEMENT_MEDIA_TYPE, elementType.toString());
                     String[] streamType = getStreamType(method);
                     MediaType mediaType = new MediaType(streamType[0], streamType[1], parameterMap);
                     jaxrsResponse = (BuiltResponse) Response.ok().type(mediaType).build();
                  }
                  else
                  {
                     throw new RuntimeException(Messages.MESSAGES.expectedStreamOrSseMediaType());
                  }
               }
            }

            try
            {
               ServerResponseWriter.writeNomapResponse(jaxrsResponse, request, response,
                     ResteasyProviderFactory.peekInstance(), t -> {
                  }, true);
               response.getOutputStream().write(SseConstants.EOL);
               response.getOutputStream().write(SseConstants.EOL);
               response.flushBuffer();
               responseFlushed = true;
            }
            catch (IOException e)
            {
               close();
               if (throwIOException)
               {
                  throw e;
               }
               throw new ProcessingException(Messages.MESSAGES.failedToCreateSseEventOutput(), e);
            }
         }
      }
   }

   @Override
   public boolean isClosed()
   {
      return closed;
   }

   @Override
   public CompletionStage<?> send(OutboundSseEvent event)
   {
      synchronized (lock)
      {
         if (closed)
         {
            throw new IllegalStateException(Messages.MESSAGES.sseEventSinkIsClosed());
         }
         try
         {
            internalFlushResponseToClient(true);
            writeEvent(event);

         }
         catch (Exception ex)
         {
            CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            completableFuture.completeExceptionally(ex);
            return completableFuture;
         }
         return CompletableFuture.completedFuture(event);
      }
   }

   protected void writeEvent(OutboundSseEvent event) throws IOException
   {
      synchronized (lock)
      {
         ResteasyContext.pushContextDataMap(contextDataMap);
         try
         {
            if (event != null)
            {
               //// Check media type?
               ByteArrayOutputStream bout = new ByteArrayOutputStream();
               MediaType mediaType = event.getMediaType();
               boolean mediaTypeSet = event instanceof OutboundSseEventImpl ? ((OutboundSseEventImpl) event).isMediaTypeSet() : true;
               if (mediaType == null || !mediaTypeSet)
               {
                  Object o = response.getOutputHeaders().getFirst("Content-Type");
                  if (o != null)
                  {
                     if (o instanceof MediaType)
                     {
                        MediaType mt = (MediaType) o;
                        String s = mt.getParameters().get(SseConstants.SSE_ELEMENT_MEDIA_TYPE);
                        if (s != null)
                        {
                           mediaType = MediaType.valueOf(s);
                        }
                     }
                     else if (o instanceof String)
                     {
                        MediaType mt = MediaType.valueOf((String) o);
                        String s = mt.getParameters().get(SseConstants.SSE_ELEMENT_MEDIA_TYPE);
                        if (s != null)
                        {
                           mediaType = MediaType.valueOf(s);
                        }
                     }
                     else
                     {
                        throw new RuntimeException(Messages.MESSAGES.expectedStringOrMediaType(o));
                     }
                  }
               }
               if (mediaType == null)
               {
                  mediaType = MediaType.TEXT_PLAIN_TYPE;
               }
               if (event instanceof OutboundSseEventImpl)
               {
                  ((OutboundSseEventImpl) event).setMediaType(mediaType);
               }
               writer.writeTo(event, event.getClass(), null, new Annotation[]{}, mediaType, null, bout);
               response.getOutputStream().write(bout.toByteArray());
               response.flushBuffer();
            }
         }
         catch (IOException e)
         {
            //The connection could be broken or closed. whenever IO error happens, mark closed to true to
            //stop event writing
            close();
            LogMessages.LOGGER.failedToWriteSseEvent(event.toString(), e);
            throw e;
         }
         catch (Exception e)
         {
            LogMessages.LOGGER.failedToWriteSseEvent(event.toString(), e);
            throw new ProcessingException(e);
         }
         finally
         {
            ResteasyContext.removeContextDataLevel();
         }
      }
   }

   private String[] getStreamType(ResourceMethodInvoker method)
   {
      Stream stream = method.getMethod().getAnnotation(Stream.class);
      Stream.MODE mode = stream != null ? stream.value() : null;
      if (mode == null)
      {
         return new String[]{"text", "event-stream"};
      }
      else if (Stream.MODE.GENERAL.equals(mode))
      {
         return new String[] {"application", "x-stream-general"};
      }
      else if (Stream.MODE.RAW.equals(mode))
      {
         return new String[] {"application", "x-stream-raw"};
      }
      throw new RuntimeException(Messages.MESSAGES.expectedStreamModeGeneralOrRaw(mode));
   }

   private boolean contains(String[] ss, String t)
   {
      for (String s : ss)
      {
         if (s.startsWith(t))
         {
            return true;
         }
      }
      return false;
   }

   @Override
   public boolean equals(Object o) {
      return this == o;
   }

   @Override
   public int hashCode() {
      return ((Object)this).hashCode();
   }
}
