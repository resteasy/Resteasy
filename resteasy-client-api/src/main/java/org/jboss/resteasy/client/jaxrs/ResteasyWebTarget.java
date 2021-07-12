package org.jboss.resteasy.client.jaxrs;

import java.lang.reflect.Method;
import java.util.Map;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedMap;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface ResteasyWebTarget extends WebTarget
{
   ResteasyClient getResteasyClient();

   <T> T proxy(Class<T> proxyInterface);

   <T> ProxyBuilder<T> proxyBuilder(Class<T> proxyInterface);

   @Override
   ResteasyWebTarget resolveTemplate(String name, Object value) throws NullPointerException;

   @Override
   ResteasyWebTarget resolveTemplates(Map<String, Object> templateValues) throws NullPointerException;

   @Override
   ResteasyWebTarget resolveTemplate(String name, Object value, boolean encodeSlashInPath) throws NullPointerException;

   @Override
   ResteasyWebTarget resolveTemplateFromEncoded(String name, Object value) throws NullPointerException;

   @Override
   ResteasyWebTarget resolveTemplatesFromEncoded(Map<String, Object> templateValues) throws NullPointerException;

   @Override
   ResteasyWebTarget resolveTemplates(Map<String, Object> templateValues, boolean encodeSlashInPath) throws NullPointerException;

   @Override
   ResteasyWebTarget path(String path) throws NullPointerException;

   @Override
   ResteasyWebTarget matrixParam(String name, Object... values) throws NullPointerException;

   @Override
   ResteasyWebTarget queryParam(String name, Object... values) throws NullPointerException;

   ResteasyWebTarget queryParams(MultivaluedMap<String, Object> parameters) throws IllegalArgumentException, NullPointerException;

   /**
    * Will encode any '{}' characters and not treat them as template parameters.
    * @param name name
    * @param values values
    * @return web resource target
    */
   ResteasyWebTarget queryParamNoTemplate(String name, boolean isEncoded, Object... values) throws NullPointerException;

   /**
    * Will encode any '{}' characters and not treat them as template parameters.
    * @param parameters parameters map
    * @return web resource target
    */
   ResteasyWebTarget queryParamsNoTemplate(MultivaluedMap<String, Object> parameters) throws IllegalArgumentException, NullPointerException;

   ResteasyWebTarget path(Class<?> resource) throws IllegalArgumentException;

   ResteasyWebTarget path(Method method) throws IllegalArgumentException;

   ResteasyWebTarget clone();

   @Override
   ResteasyWebTarget property(String name, Object value);

   @Override
   ResteasyWebTarget register(Class<?> componentClass);

   @Override
   ResteasyWebTarget register(Class<?> componentClass, int priority);

   @Override
   ResteasyWebTarget register(Class<?> componentClass, Class<?>... contracts);

   @Override
   ResteasyWebTarget register(Class<?> componentClass, Map<Class<?>, Integer> contracts);

   @Override
   ResteasyWebTarget register(Object component);

   @Override
   ResteasyWebTarget register(Object component, int priority);

   @Override
   ResteasyWebTarget register(Object component, Class<?>... contracts);

   @Override
   ResteasyWebTarget register(Object component, Map<Class<?>, Integer> contracts);

   ResteasyWebTarget setChunked(boolean chunked);
}
