package org.jboss.resteasy.plugins.providers.sse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.sse.OutboundSseEvent;

import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.MediaTypeHelper;

@Provider
@Produces({"text/event-stream", "application/x-stream-general"})
@Consumes({"text/event-stream", "application/x-stream-general"})
public class SseEventProvider implements MessageBodyWriter<OutboundSseEvent>, MessageBodyReader<SseEventInputImpl>
{
   public static final MediaType GENERAL_STREAM_TYPE = new MediaType("application", "x-stream-general");

   @Override
   public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
   {
      return OutboundSseEvent.class.isAssignableFrom(type) &&
            (MediaType.SERVER_SENT_EVENTS_TYPE.isCompatible(mediaType) ||
            GENERAL_STREAM_TYPE.isCompatible(mediaType));
   }

   @Override
   public long getSize(OutboundSseEvent t, Class<?> type, Type genericType, Annotation[] annotations,
         MediaType mediaType)
   {
      return -1;
   }

   @Override
   @SuppressWarnings({"unchecked"})
   public void writeTo(OutboundSseEvent event, Class<?> type, Type genericType, Annotation[] annotations,
      MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
         throws IOException, WebApplicationException
   {
      Charset charset = StandardCharsets.UTF_8;
      boolean textLike = MediaTypeHelper.isTextLike(mediaType);
      boolean escape = event instanceof OutboundSseEventImpl ? ((OutboundSseEventImpl)event).isEscape() : false;
      if (event.getComment() != null)
      {
         for (final String comment : event.getComment().split("\n"))
         {
            entityStream.write(SseConstants.COMMENT_LEAD);
            entityStream.write(comment.getBytes(charset));
            entityStream.write(SseConstants.EOL);
         }
      }

      if (event.getType() != null)
      {
         if (event.getName() != null)
         {
            entityStream.write(SseConstants.NAME_LEAD);
            entityStream.write(event.getName().getBytes(charset));
            entityStream.write(SseConstants.EOL);
         }
         if (event.getId() != null)
         {
            entityStream.write(SseConstants.ID_LEAD);
            entityStream.write(event.getId().getBytes(charset));
            entityStream.write(SseConstants.EOL);
         }
         if (event.getReconnectDelay() > -1)
         {
            entityStream.write(SseConstants.RETRY_LEAD);
            entityStream.write(Long.toString(event.getReconnectDelay()).getBytes(StandardCharsets.UTF_8));
            entityStream.write(SseConstants.EOL);
         }

         if (event.getData() != null)
         {
            Class<?> payloadClass = event.getType();
            Type payloadType = event.getGenericType();
            if (payloadType == null)
            {
               payloadType = payloadClass;
            }

            if (payloadType == null && payloadClass == null)
            {
               payloadType = Object.class;
               payloadClass = Object.class;
            }

            entityStream.write(SseConstants.DATA_LEAD);
            ResteasyProviderFactory f = ResteasyProviderFactory.peekInstance();
            MessageBodyWriter writer = f != null ? f.getMessageBodyWriter(payloadClass,
                  payloadType, annotations, event.getMediaType()) : null;

            if (writer == null)
            {
               throw new ServerErrorException(Messages.MESSAGES.notFoundMBW(payloadClass.getName()),
                     Response.Status.INTERNAL_SERVER_ERROR);
            }
            writer.writeTo(event.getData(), payloadClass, payloadType, annotations, event.getMediaType(), httpHeaders,
               new OutputStream()
               {
                  boolean isNewLine = false;

                  @Override
                  public void write(int b) throws IOException
                  {
                     if (textLike)
                     {
                        if (b == '\n' || b == '\r')
                        {
                           if (!isNewLine)
                           {
                              entityStream.write(SseConstants.EOL);
                           }
                           isNewLine = true;
                        }
                        else
                        {
                           if (isNewLine)
                           {
                              entityStream.write(SseConstants.DATA_LEAD);
                           }
                           entityStream.write(b);
                           isNewLine = false;
                        }
                     }
                     else
                     {
                        if (escape && (b == '\n' || b == '\r' || b == '\\'))
                        {
                           entityStream.write('\\');
                           entityStream.write(b);
                        }
                        else
                        {
                           entityStream.write(b);
                        }
                     }
                  }

                  @Override
                  public void flush() throws IOException
                  {
                     entityStream.flush();
                  }

                  @Override
                  public void close() throws IOException
                  {
                     if (!textLike)
                     {
                        entityStream.write(SseConstants.EOL);
                     }
                     entityStream.close();
                  }
               });
            entityStream.write(SseConstants.EOL);

         }

      }
      entityStream.write(SseConstants.EOL);
   }

   @Override
   public boolean isReadable(Class<?> cls, Type type, Annotation[] annotations, MediaType mediaType)
   {
      return SseEventInputImpl.class.isAssignableFrom(cls) &&
            (MediaType.SERVER_SENT_EVENTS_TYPE.isCompatible(mediaType) ||
            GENERAL_STREAM_TYPE.isCompatible(mediaType));
   }

   @Override
   public SseEventInputImpl readFrom(Class<SseEventInputImpl> cls, Type type, Annotation[] annotations,
         MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException,
         WebApplicationException
   {
      MediaType streamType = mediaType;
      if (mediaType.getParameters() != null)
      {
         Map<String, String> map = mediaType.getParameters();
         String elementType = map.get(SseConstants.SSE_ELEMENT_MEDIA_TYPE);
         if (elementType != null)
         {
            mediaType = MediaType.valueOf(elementType);
         }
      }
      return new SseEventInputImpl(annotations, streamType, mediaType, httpHeaders, entityStream);
   }

}
