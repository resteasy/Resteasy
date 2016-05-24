package org.jboss.resteasy.client.jaxrs;

import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ClientProxy;
import org.jboss.resteasy.client.jaxrs.internal.proxy.MethodInvoker;
import org.jboss.resteasy.client.jaxrs.internal.proxy.ResteasyClientProxy;
import org.jboss.resteasy.client.jaxrs.internal.proxy.SubResourceInvoker;
import org.jboss.resteasy.util.IsHttpMethod;

import javax.ws.rs.Path;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

public class ProxyBuilder<T>
{
   private static final Class<?>[] cClassArgArray = {Class.class};
   
	private final Class<T> iface;
	private final ResteasyWebTarget webTarget;
	private ClassLoader loader = Thread.currentThread().getContextClassLoader();
	private MediaType serverConsumes;
	private MediaType serverProduces;

   public static <T> ProxyBuilder<T> builder(Class<T> iface, WebTarget webTarget)
   {
      return new ProxyBuilder<T>(iface, (ResteasyWebTarget)webTarget);
   }

   private static boolean isDefault(Method method) {
      return ((method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) ==
         Modifier.PUBLIC) && method.getDeclaringClass().isInterface();
   }

	@SuppressWarnings("unchecked")
	public static <T> T proxy(final Class<T> iface, WebTarget base, final ProxyConfig config)
	{
      if (iface.isAnnotationPresent(Path.class))
      {
         Path path = iface.getAnnotation(Path.class);
         if (!path.value().equals("") && !path.value().equals("/"))
         {
            base = base.path(path.value());
         }
      }
		HashMap<Method, MethodInvoker> methodMap = new HashMap<Method, MethodInvoker>();
		for (Method method : iface.getMethods())
		{
         // ignore the as method to allow declaration in client interfaces
		   if ("as".equals(method.getName()) && Arrays.equals(method.getParameterTypes(), cClassArgArray))
		   {
		      continue;
		   }
         MethodInvoker invoker;
         Set<String> httpMethods = IsHttpMethod.getHttpMethods(method);
         if (isDefault(method)) {
            continue;
         }
         else if ((httpMethods == null || httpMethods.size() == 0) && method.isAnnotationPresent(Path.class) && method.getReturnType().isInterface())
         {
            invoker = new SubResourceInvoker((ResteasyWebTarget)base, method, config);
         }
         else
         {
            invoker = createClientInvoker(iface, method, (ResteasyWebTarget)base, config);
         }
         methodMap.put(method, invoker);
		}

		Class<?>[] intfs =
		{
				iface, ResteasyClientProxy.class
		};

		ClientProxy clientProxy = new ClientProxy(methodMap, base, config);
		// this is done so that equals and hashCode work ok. Adding the proxy to a
		// Collection will cause equals and hashCode to be invoked. The Spring
		// infrastructure had some problems without this.
		clientProxy.setClazz(iface);

		return (T) Proxy.newProxyInstance(config.getLoader(), intfs, clientProxy);
	}

   private static <T> ClientInvoker createClientInvoker(Class<T> clazz, Method method, ResteasyWebTarget base, ProxyConfig config)
   {
      Set<String> httpMethods = IsHttpMethod.getHttpMethods(method);
      if (httpMethods == null || httpMethods.size() != 1)
      {
         throw new RuntimeException(Messages.MESSAGES.mustUseExactlyOneHttpMethod(method.toString()));
      }
      ClientInvoker invoker = new ClientInvoker(base, clazz, method, config);
      invoker.setHttpMethod(httpMethods.iterator().next());
      return invoker;
   }

   private ProxyBuilder(Class<T> iface, ResteasyWebTarget webTarget)
   {
      this.iface = iface;
      this.webTarget = webTarget;
   }

   public ProxyBuilder<T> classloader(ClassLoader cl)
	{
		this.loader = cl;
		return this;
	}

	public ProxyBuilder<T> defaultProduces(MediaType type)
	{
		this.serverProduces = type;
		return this;
	}

	public ProxyBuilder<T> defaultConsumes(MediaType type)
	{
		this.serverConsumes = type;
		return this;
	}

   public ProxyBuilder<T> defaultProduces(String type)
   {
      this.serverProduces = MediaType.valueOf(type);
      return this;
   }

   public ProxyBuilder<T> defaultConsumes(String type)
   {
      this.serverConsumes = MediaType.valueOf(type);
      return this;
   }
	public T build()
	{
      return proxy(iface, webTarget, new ProxyConfig(loader, serverConsumes, serverProduces));
	}



}
