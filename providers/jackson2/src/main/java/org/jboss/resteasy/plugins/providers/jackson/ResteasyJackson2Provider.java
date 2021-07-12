package org.jboss.resteasy.plugins.providers.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.providers.jackson.Formatted;
import org.jboss.resteasy.core.interception.jaxrs.DecoratorMatcher;
import org.jboss.resteasy.core.messagebody.AsyncBufferedMessageBodyWriter;
import org.jboss.resteasy.resteasy_jaxrs.i18n.LogMessages;
import org.jboss.resteasy.util.DelegatingOutputStream;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterInjector;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterModifier;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JsonEndpointConfig;
import com.fasterxml.jackson.jaxrs.util.ClassKey;

/**
 * Only different from Jackson one is *+json in @Produces/@Consumes
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@Provider
@Consumes({"application/json", "application/*+json", "text/json"})
@Produces({"application/json", "application/*+json", "text/json"})
public class ResteasyJackson2Provider extends JacksonJaxbJsonProvider implements AsyncBufferedMessageBodyWriter<Object>
{

   DecoratorMatcher decoratorMatcher = new DecoratorMatcher();


   @Override
   public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType)
   {
      return super.isReadable(aClass, type, annotations, mediaType);
   }

   @Override
   public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType)
   {
      return super.isWriteable(aClass, type, annotations, mediaType);
   }

   // Currently we need to override readFrom and writeTo because Jackson 2.2.1 does not cache correctly
   // It does not allow to have a ContextResolver that chooses different mappers per Java type.

   private static class ClassAnnotationKey
   {
      private final  Annotation[] annotations;
      private final ClassKey classKey;
      private final int hash;

      private ClassAnnotationKey(final Class<?> clazz, final Annotation[] annotations)
      {
         this.classKey = new ClassKey(clazz);
         this.annotations = annotations;
         hash = Arrays.hashCode(this.annotations) + classKey.hashCode();
      }

      @Override
      public boolean equals(Object o)
      {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         ClassAnnotationKey that = (ClassAnnotationKey) o;
         if (!classKey.equals(that.classKey)) return false;
         //if method annotations the same
         if (annotations != null && annotations.equals(that.annotations)) {
            return true;
         }
         return Arrays.equals(annotations, that.annotations);
      }

      @Override
      public int hashCode()
      {
         return hash;
      }
   }

   protected final ConcurrentHashMap<ClassAnnotationKey, JsonEndpointConfig> _readers
         = new ConcurrentHashMap<ClassAnnotationKey, JsonEndpointConfig>();
   protected final ConcurrentHashMap<ClassAnnotationKey, Boolean> decorators
         = new ConcurrentHashMap<ClassAnnotationKey, Boolean>();

   @Override
   public Object readFrom(Class<Object> type, final Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String,String> httpHeaders, InputStream entityStream)
         throws IOException
   {
      LogMessages.LOGGER.debugf("Provider : %s,  Method : readFrom", getClass().getName());
      ClassAnnotationKey key = new ClassAnnotationKey(type, annotations);
      JsonEndpointConfig endpoint;
      endpoint = _readers.get(key);
      // not yet resolved (or not cached any more)? Resolve!
      if (endpoint == null) {
         ObjectMapper mapper = locateMapper(type, mediaType);
         PolymorphicTypeValidator ptv = mapper.getPolymorphicTypeValidator();
         //the check is protected by test org.jboss.resteasy.test.providers.jackson2.whitelist.JacksonConfig,
         //be sure to keep that in synch if changing anything here.
         if (ptv == null || ptv instanceof LaissezFaireSubTypeValidator) {
            mapper.setPolymorphicTypeValidator(new WhiteListPolymorphicTypeValidatorBuilder().build());
         }
         endpoint = _configForReading(mapper, annotations, null);
         _readers.put(key, endpoint);
      }
      final ObjectReader reader = endpoint.getReader();
      final JsonParser jp = _createParser(reader, entityStream);
      // If null is returned, considered to be empty stream
      if (jp == null) {
         return null;
      } else if (jp.nextToken() == null) {
         jp.close();
         return null;
      }

      // [Issue#1]: allow 'binding' to JsonParser
      if (((Class<?>) type) == JsonParser.class) {
         return jp;
      }

      Object result = null;
      try {
         if (System.getSecurityManager() == null) {
            result = reader.forType(reader.getTypeFactory().constructType(genericType)).readValue(jp);
         } else {
            result = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
               @Override
               public Object run() throws Exception {
                  return reader.forType(reader.getTypeFactory().constructType(genericType)).readValue(jp);
               }
            });
         }
      } catch (PrivilegedActionException pae) {
         throw new IOException(pae);
      } finally {
         jp.close();
      }
      return result;
   }

   protected final ConcurrentHashMap<ClassAnnotationKey, JsonEndpointConfig> _writers
         = new ConcurrentHashMap<ClassAnnotationKey, JsonEndpointConfig>();

   @Override
   public void writeTo(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                       MultivaluedMap<String,Object> httpHeaders, OutputStream entityStream)
         throws IOException
   {
      LogMessages.LOGGER.debugf("Provider : %s,  Method : writeTo", getClass().getName());
      entityStream = new DelegatingOutputStream(entityStream) {
         @Override
         public void flush() throws IOException {
              // don't flush as this is a performance hit on Undertow.
              // and causes chunked encoding to happen.
         }
      };
      ClassAnnotationKey key = new ClassAnnotationKey(type, annotations);
      JsonEndpointConfig endpoint;
      endpoint = _writers.get(key);

      // not yet resolved (or not cached any more)? Resolve!
      if (endpoint == null) {
         ObjectMapper mapper = locateMapper(type, mediaType);
         PolymorphicTypeValidator ptv = mapper.getPolymorphicTypeValidator();
         //the check is protected by test org.jboss.resteasy.test.providers.jackson2.whitelist.JacksonConfig,
         //be sure to keep that in synch if changing anything here.
         if (ptv == null || ptv instanceof LaissezFaireSubTypeValidator) {
            mapper.setPolymorphicTypeValidator(new WhiteListPolymorphicTypeValidatorBuilder().build());
         }
         endpoint = _configForWriting(mapper, annotations, null);

         // and cache for future reuse
         _writers.put(key, endpoint);
      }

      ObjectWriter writer = endpoint.getWriter();
      boolean withIndentOutput = false; // no way to replace _serializationConfig

      // we can't cache this.
      if (annotations != null) {
         for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(Formatted.class)) {
               withIndentOutput = true;
               break;
            }
         }
      }

      /* 27-Feb-2009, tatu: Where can we find desired encoding? Within
       *   HTTP headers?
       */
      JsonEncoding enc = findEncoding(mediaType, httpHeaders);
      final JsonGenerator jg = writer.getFactory().createGenerator(entityStream, enc);
      jg.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

      try {
         // Want indentation?
         if (writer.isEnabled(SerializationFeature.INDENT_OUTPUT) || withIndentOutput) {
            jg.useDefaultPrettyPrinter();
         }
         // 04-Mar-2010, tatu: How about type we were given? (if any)
         JavaType rootType = null;

         if (genericType != null && value != null) {
            /* 10-Jan-2011, tatu: as per [JACKSON-456], it's not safe to just force root
            *    type since it prevents polymorphic type serialization. Since we really
            *    just need this for generics, let's only use generic type if it's truly
            *    generic.
            */
            if (genericType.getClass() != Class.class) { // generic types are other impls of 'java.lang.reflect.Type'
               /* This is still not exactly right; should root type be further
               * specialized with 'value.getClass()'? Let's see how well this works before
               * trying to come up with more complete solution.
               */
               rootType = writer.getTypeFactory().constructType(genericType);
               /* 26-Feb-2011, tatu: To help with [JACKSON-518], we better recognize cases where
               *    type degenerates back into "Object.class" (as is the case with plain TypeVariable,
               *    for example), and not use that.
               */
               if (rootType.getRawClass() == Object.class) {
                  rootType = null;
               }
            }
         }

         // Most of the configuration now handled through EndpointConfig, ObjectWriter
         // but we may need to force root type:
         if (rootType != null) {
            writer = writer.forType(rootType);
         }
         value = endpoint.modifyBeforeWrite(value);
         ObjectWriterModifier mod = ObjectWriterInjector.getAndClear();
         if (mod == null) {
            ClassLoader tccl;
            if (System.getSecurityManager() == null)
            {
               tccl = Thread.currentThread().getContextClassLoader();
            }
            else
            {
               tccl = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>()
               {
                  @Override
                  public ClassLoader run()
                  {
                     return Thread.currentThread().getContextClassLoader();
                  }
               });
            }
            mod = ResteasyObjectWriterInjector.get(tccl);
         }
         if (mod != null) {
            writer = mod.modify(endpoint, httpHeaders, value, writer, jg);
         }

         // [RESTEASY-1317] Support Jackson in Atom links
          Object hasDecorator = decorators.get(key);
         if (hasDecorator == null) {
            if (decoratorMatcher.hasDecorator(DecoratedEntityContainer.class, annotations))
            {
               decoratorMatcher
                     .decorate(DecoratedEntityContainer.class, new DecoratedEntityContainer(value), type, annotations,
                           mediaType);
               decorators.put(key, Boolean.TRUE);
            } else {
               decorators.put(key, Boolean.FALSE);
            }
         } else {
            if ((Boolean)hasDecorator) {
               decoratorMatcher
                     .decorate(DecoratedEntityContainer.class, new DecoratedEntityContainer(value), type, annotations,
                           mediaType);
            }
         }
         if (System.getSecurityManager() == null) {
            writer.writeValue(jg, value);
         } else {
            final ObjectWriter smWriter = writer;
            final Object smValue = value;
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
               @Override
               public Object run() throws Exception {

                  smWriter.writeValue(jg, smValue);
                  return null;
               }
            });
         }
      } catch(PrivilegedActionException pae) {
         throw new IOException(pae);
      } finally {
         jg.close();
      }
   }
}
