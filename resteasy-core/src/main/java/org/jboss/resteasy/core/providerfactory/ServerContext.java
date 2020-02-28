package org.jboss.resteasy.core.providerfactory;

import java.util.Map;

/**
 * Makes server context available to client running in a JAX-RS resource.
 */
public class ServerContext {

   private Map<Class<?>, Object> delegate;

   public ServerContext(final Map<Class<?>, Object> delegate)
   {
      this.delegate = delegate;
   }

   public Object get(Class<?> key)
   {
      return delegate.get(key);
   }
}