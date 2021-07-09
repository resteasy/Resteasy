package org.jboss.resteasy.plugins.providers.jsonb;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * Created by rsearls
 */
public class AbstractJsonBindingProvider {

   private static final String JSON = "json";
   private static final String PLUS_JSON = "+json";

   @Context
   private Providers providers;

   protected Jsonb getJsonb(Class<?> type) {
      ContextResolver<Jsonb> contextResolver = providers.getContextResolver(Jsonb.class, MediaType.APPLICATION_JSON_TYPE);
      if (contextResolver != null)
      {
         final Jsonb result = contextResolver.getContext(type);
         if (result != null) {
            return new ManagedJsonb(result);
         }
         return null;
      }
      else
      {
         return JsonbBuilder.create();
      }
   }

   public static Charset getCharset(final MediaType mediaType) {
      return StandardCharsets.UTF_8;
   }

   public static boolean isSupportedMediaType(final MediaType mediaType) {
      return mediaType.getSubtype().equals(JSON) || mediaType.getSubtype().endsWith(PLUS_JSON);
   }
}
