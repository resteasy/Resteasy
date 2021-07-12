package org.jboss.resteasy.client.jaxrs.internal;

import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.specimpl.ResteasyUriBuilderImpl;
import org.jboss.resteasy.spi.ResteasyUriBuilder;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ClientWebTarget implements ResteasyWebTarget
{
   protected ResteasyClient client;
   protected UriBuilder uriBuilder;
   protected ClientConfiguration configuration;
   protected boolean chunked = false;

   protected ClientWebTarget(final ResteasyClient client, final ClientConfiguration configuration)
   {
      this.configuration = new ClientConfiguration(configuration);
      this.client = client;
   }

   public ClientWebTarget(final ResteasyClient client, final String uri, final ClientConfiguration configuration) throws IllegalArgumentException, NullPointerException
   {
      this(client, configuration);
      uriBuilder = uriBuilderFromUri(uri);
   }

   public ClientWebTarget(final ResteasyClient client, final URI uri, final ClientConfiguration configuration) throws NullPointerException
   {
      this(client, configuration);
      uriBuilder = uriBuilderFromUri(uri);
   }

   public ClientWebTarget(final ResteasyClient client, final UriBuilder uriBuilder, final ClientConfiguration configuration) throws NullPointerException
   {
      this(client, configuration);
      this.uriBuilder = uriBuilder.clone();
   }

   /**
    * Get a new UriBuilder explicitly using RESTEasy implementation
    * (instead of running UriBuilder.fromUri(uri) which relies on
    * current registered JAX-RS implementation)
    *
    * @param uri
    * @return
    */
   private static UriBuilder uriBuilderFromUri(URI uri)
   {
      return new ResteasyUriBuilderImpl().uri(uri);
   }

   private static UriBuilder uriBuilderFromUri(String uri)
   {
      return new ResteasyUriBuilderImpl().uri(uri);
   }

   @Override
   public ResteasyWebTarget clone()
   {
      client.abortIfClosed();
      UriBuilder copy = uriBuilder.clone();
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyClient getResteasyClient()
   {
      client.abortIfClosed();
      return client;
   }

   @Override
   public <T> T proxy(Class<T> proxyInterface)
   {
      client.abortIfClosed();
      return createProxyBuilder(proxyInterface, this).build();
   }

   @Override
   public <T> ProxyBuilder<T> proxyBuilder(Class<T> proxyInterface)
   {
      client.abortIfClosed();
      if (proxyInterface == null) throw new NullPointerException(Messages.MESSAGES.proxyInterfaceWasNull());
      return createProxyBuilder(proxyInterface, this);
   }

   protected <T> ProxyBuilder<T> createProxyBuilder(Class<T> proxyInterface, ClientWebTarget cwt)
   {
      return ProxyBuilder.builder(proxyInterface, cwt);
   }

   @Override
   public URI getUri()
   {
      client.abortIfClosed();
      return uriBuilder.build();
   }

   @Override
   public UriBuilder getUriBuilder()
   {
      client.abortIfClosed();
      return uriBuilder.clone();
   }

   @Override
   public Configuration getConfiguration()
   {
      client.abortIfClosed();
      return configuration;
   }

   @Override
   public ResteasyWebTarget path(String path) throws NullPointerException
   {
      client.abortIfClosed();
      if (path == null) throw new NullPointerException(Messages.MESSAGES.pathWasNull());
      UriBuilder copy = uriBuilder.clone().path(path);
      return  newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget path(Class<?> resource) throws IllegalArgumentException
   {
      client.abortIfClosed();
      if (resource == null) throw new NullPointerException(Messages.MESSAGES.resourceWasNull());
      UriBuilder copy = uriBuilder.clone().path(resource);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget path(Method method) throws IllegalArgumentException
   {
      client.abortIfClosed();
      if (method == null) throw new NullPointerException(Messages.MESSAGES.methodWasNull());
      UriBuilder copy = uriBuilder.clone().path(method);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget resolveTemplate(String name, Object value) throws NullPointerException
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());
      if (value == null) throw new NullPointerException(Messages.MESSAGES.valueWasNull());
      String val = configuration.toString(value);
      UriBuilder copy = uriBuilder.clone().resolveTemplate(name, val);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget resolveTemplates(Map<String, Object> templateValues) throws NullPointerException
   {
      client.abortIfClosed();
      if (templateValues == null) throw new NullPointerException(Messages.MESSAGES.templateValuesWasNull());
      if (templateValues.isEmpty()) return this;
      Map<String, Object> vals = new HashMap<>();
      for (Map.Entry<String, Object> entry : templateValues.entrySet())
      {
         if (entry.getKey() == null || entry.getValue() == null) throw new NullPointerException(Messages.MESSAGES.templateValuesEntryWasNull());
         vals.put(entry.getKey(), configuration.toString(entry.getValue()));
      }
      UriBuilder copy = uriBuilder.clone().resolveTemplates(vals);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget resolveTemplate(String name, Object value, boolean encodeSlashInPath) throws NullPointerException
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());
      if (value == null) throw new NullPointerException(Messages.MESSAGES.valueWasNull());
      String val = configuration.toString(value);
      UriBuilder copy = uriBuilder.clone().resolveTemplate(name, val, encodeSlashInPath);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget resolveTemplateFromEncoded(String name, Object value) throws NullPointerException
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());
      if (value == null) throw new NullPointerException(Messages.MESSAGES.valueWasNull());
      String val = configuration.toString(value);
      UriBuilder copy = uriBuilder.clone().resolveTemplateFromEncoded(name, val);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget resolveTemplatesFromEncoded(Map<String, Object> templateValues) throws NullPointerException
   {
      client.abortIfClosed();
      if (templateValues == null) throw new NullPointerException(Messages.MESSAGES.templateValuesWasNull());
      if (templateValues.isEmpty()) return this;
      Map<String, Object> vals = new HashMap<>();
      for (Map.Entry<String, Object> entry : templateValues.entrySet())
      {
         if (entry.getKey() == null || entry.getValue() == null) throw new NullPointerException(Messages.MESSAGES.templateValuesEntryWasNull());
         vals.put(entry.getKey(), configuration.toString(entry.getValue()));
      }
      UriBuilder copy = uriBuilder.clone().resolveTemplatesFromEncoded(vals);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget resolveTemplates(Map<String, Object> templateValues, boolean encodeSlashInPath) throws NullPointerException
   {
      client.abortIfClosed();
      if (templateValues == null) throw new NullPointerException(Messages.MESSAGES.templateValuesWasNull());
      if (templateValues.isEmpty()) return this;
      Map<String, Object> vals = new HashMap<>();
      for (Map.Entry<String, Object> entry : templateValues.entrySet())
      {
         if (entry.getKey() == null || entry.getValue() == null) throw new NullPointerException(Messages.MESSAGES.templateValuesEntryWasNull());
         vals.put(entry.getKey(), configuration.toString(entry.getValue()));
      }
      UriBuilder copy = uriBuilder.clone().resolveTemplates(vals, encodeSlashInPath);
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget matrixParam(String name, Object... values) throws NullPointerException
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());
      UriBuilder copy = uriBuilder.clone();
      if (values.length == 1 && values[0] == null)
      {
         copy.replaceMatrixParam(name, (Object[]) null);
      }
      else
      {
         copy.matrixParam(name, (Object[]) toStringValues(values));
      }
      return newInstance(client, copy, configuration);
   }

   private String[] toStringValues(Object[] values)
   {
      String[] stringValues = new String[values.length];
      for (int i = 0; i < stringValues.length; i++)
      {
         stringValues[i] = configuration.toString(values[i]);
      }
      return stringValues;
   }

   @Override
   public ResteasyWebTarget queryParam(String name, Object... values) throws NullPointerException
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());
      UriBuilder copy = uriBuilder.clone();
      if (values == null || (values.length == 1 && values[0] == null))
      {
         copy.replaceQueryParam(name, (Object[]) null);
      }
      else
      {
         copy.queryParam(name, (Object[]) toStringValues(values));
      }
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget queryParams(MultivaluedMap<String, Object> parameters) throws IllegalArgumentException, NullPointerException
   {
      client.abortIfClosed();
      if (parameters == null) throw new NullPointerException(Messages.MESSAGES.parametersWasNull());
      UriBuilder copy = uriBuilder.clone();
      for (Map.Entry<String, List<Object>> entry : parameters.entrySet())
      {
         copy.queryParam(entry.getKey(), (Object[]) toStringValues(entry.getValue().toArray()));
      }
      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget queryParamNoTemplate(String name, boolean isEncoded, Object... values) throws NullPointerException
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());

      //The whole array can be represented as one object, so we need to cast it to array of objects
      if (values.length == 1 && values[0].getClass().isArray() && !values[0].getClass().getComponentType().isPrimitive()) {
         values = (Object[]) values[0];
      }

      ResteasyUriBuilder copy;
      if (uriBuilder instanceof ResteasyUriBuilder) {
         copy = (ResteasyUriBuilder)uriBuilder.clone();
      } else {
         copy = ResteasyUriBuilder.fromTemplate(uriBuilder.toTemplate());
      }

      copy.clientQueryParam(name, isEncoded, (Object[]) toStringValues(values));

      return newInstance(client, copy, configuration);
   }

   @Override
   public ResteasyWebTarget queryParamsNoTemplate(MultivaluedMap<String, Object> parameters) throws IllegalArgumentException, NullPointerException
   {
      client.abortIfClosed();
      if (parameters == null) throw new NullPointerException(Messages.MESSAGES.parametersWasNull());
      ResteasyUriBuilder copy;
      if (uriBuilder instanceof ResteasyUriBuilder) {
         copy = (ResteasyUriBuilder)uriBuilder.clone();
      } else {
         copy = ResteasyUriBuilder.fromTemplate(uriBuilder.toTemplate());
      }
      for (Map.Entry<String, List<Object>> entry : parameters.entrySet())
      {
         copy.clientQueryParam(entry.getKey(), false, (Object[]) toStringValues(entry.getValue().toArray()));
      }
      return newInstance(client, copy, configuration);
   }

   protected ClientWebTarget newInstance(ResteasyClient client, UriBuilder uriBuilder, ClientConfiguration configuration) {
      return new ClientWebTarget(client, uriBuilder, configuration);
   }

   @Override
   public Invocation.Builder request()
   {
      client.abortIfClosed();
      ClientInvocationBuilder builder = createClientInvocationBuilder(client, uriBuilder.build(), configuration);
      builder.setChunked(chunked);
      builder.setTarget(this);
      return builder;
   }

   @Override
   public Invocation.Builder request(String... acceptedResponseTypes)
   {
      client.abortIfClosed();
      ClientInvocationBuilder builder = createClientInvocationBuilder(client, uriBuilder.build(), configuration);
      builder.getHeaders().accept(acceptedResponseTypes);
      builder.setChunked(chunked);
      builder.setTarget(this);
      return builder;
   }

   @Override
   public Invocation.Builder request(MediaType... acceptedResponseTypes)
   {
      client.abortIfClosed();
      ClientInvocationBuilder builder = createClientInvocationBuilder(client, uriBuilder.build(), configuration);
      builder.getHeaders().accept(acceptedResponseTypes);
      builder.setChunked(chunked);
      builder.setTarget(this);
      return builder;
   }

   protected ClientInvocationBuilder createClientInvocationBuilder(ResteasyClient client, URI uri, ClientConfiguration configuration)
   {
      return new ClientInvocationBuilder(client, uri, configuration);
   }

   @Override
   public ResteasyWebTarget property(String name, Object value)
   {
      client.abortIfClosed();
      if (name == null) throw new NullPointerException(Messages.MESSAGES.nameWasNull());
      configuration.property(name, value);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Class<?> componentClass)
   {
      client.abortIfClosed();
      configuration.register(componentClass);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Class<?> componentClass, int priority)
   {
      client.abortIfClosed();
      configuration.register(componentClass, priority);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Class<?> componentClass, Class<?>... contracts)
   {
      client.abortIfClosed();
      configuration.register(componentClass, contracts);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Class<?> componentClass, Map<Class<?>, Integer> contracts)
   {
      client.abortIfClosed();
      configuration.register(componentClass, contracts);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Object component)
   {
      client.abortIfClosed();
      configuration.register(component);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Object component, int priority)
   {
      client.abortIfClosed();
      configuration.register(component, priority);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Object component, Class<?>... contracts)
   {
      client.abortIfClosed();
      configuration.register(component, contracts);
      return this;
   }

   @Override
   public ResteasyWebTarget register(Object component, Map<Class<?>, Integer> contracts)
   {
      client.abortIfClosed();
      configuration.register(component, contracts);
      return this;
   }

   @Override
   public ResteasyWebTarget setChunked(boolean chunked)
   {
      this.chunked = chunked;
      return this;
   }
}
