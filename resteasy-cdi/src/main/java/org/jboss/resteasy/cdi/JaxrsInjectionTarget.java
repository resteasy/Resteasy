package org.jboss.resteasy.cdi;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Function;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;

import org.jboss.resteasy.cdi.i18n.LogMessages;
import org.jboss.resteasy.cdi.i18n.Messages;
import org.jboss.resteasy.core.PropertyInjectorImpl;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.core.ThreadLocalResteasyProviderFactory;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.PropertyInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.util.Types;
import org.jboss.resteasy.spi.validation.GeneralValidatorCDI;
import org.jboss.resteasy.util.GetRestful;

/**
 * This implementation of InjectionTarget is a wrapper that allows JAX-RS
 * property injection to be performed just after CDI injection.
 *
 * @author Jozef Hartinger
 *
 */
public class JaxrsInjectionTarget<T> implements InjectionTarget<T>
{

   private InjectionTarget<T> delegate;
   private Class<T> clazz;
   private PropertyInjector propertyInjector;
   private GeneralValidatorCDI validator;
   private final ResteasyProviderFactory providerFactory;

   private boolean hasPostConstruct;

   private static final Function<Method, Boolean> validatePostConstructParameters
      = (Method m) -> {if (m.getParameterCount() == 0) return true;
                       else if (m.getParameterCount() == 1
                             && InvocationContext.class.equals(m.getParameterTypes()[0])
                             && m.getAnnotation(AroundInvoke.class) != null) return true;
                       else return false;};

   public JaxrsInjectionTarget(final InjectionTarget<T> delegate, final Class<T> clazz)
   {
      this.delegate = delegate;
      this.clazz = clazz;
      hasPostConstruct = Types.hasPostConstruct(clazz, validatePostConstructParameters);
      this.providerFactory = new ResteasyProviderFactoryImpl(null, true);
      RegisterBuiltin.register(this.providerFactory);
   }

   public void inject(T instance, CreationalContext<T> ctx)
   {
      delegate.inject(instance, ctx);

      // We need to load PropertyInjector lazily since RESTEasy starts
      // after the CDI lifecycle events are executed
      if (propertyInjector == null)
      {
         propertyInjector = getPropertyInjector();
      }

      HttpRequest request = ResteasyContext.getContextData(HttpRequest.class);
      HttpResponse response = ResteasyContext.getContextData(HttpResponse.class);

      if ((request != null) && (response != null))
      {
         propertyInjector.inject(request, response, instance, false);
      }
      else
      {
         propertyInjector.inject(instance, false);
      }

      if (request != null && !hasPostConstruct)
      {
         validate(request, instance);
      }
      else
      {
         LogMessages.LOGGER.debug(Messages.MESSAGES.skippingValidationOutsideResteasyContext());
      }
   }

   public void postConstruct(T instance)
   {
      delegate.postConstruct(instance);
      if (hasPostConstruct)
      {
         HttpRequest request = ResteasyContext.getContextData(HttpRequest.class);
         if (request != null)
         {
            validate(request, instance);
         }
         else
         {
            LogMessages.LOGGER.debug(Messages.MESSAGES.skippingValidationOutsideResteasyContext());
         }
      }
   }

   public void preDestroy(T instance)
   {
      delegate.preDestroy(instance);
   }

   public void dispose(T instance)
   {
      delegate.dispose(instance);
   }

   public Set<InjectionPoint> getInjectionPoints()
   {
      return delegate.getInjectionPoints();
   }

   public T produce(CreationalContext<T> ctx)
   {
      ResteasyProviderFactory defaultProviderFactory = ResteasyProviderFactory.peekInstance();
      ThreadLocalResteasyProviderFactory tlFactory = null;
      try {
         if (defaultProviderFactory == null) {
            ResteasyProviderFactory.setInstance(providerFactory);
         } else {
            if (!(defaultProviderFactory instanceof ThreadLocalResteasyProviderFactory)) {
               tlFactory = new ThreadLocalResteasyProviderFactory(defaultProviderFactory);
               ResteasyProviderFactory.setInstance(tlFactory);
            }
            ThreadLocalResteasyProviderFactory.push(providerFactory);
         }
         return delegate.produce(ctx);
      } finally {
         if (defaultProviderFactory == null) {
            ResteasyProviderFactory.clearInstanceIfEqual(providerFactory);
         } else {
            if (!(defaultProviderFactory instanceof ThreadLocalResteasyProviderFactory)) {
               ResteasyProviderFactory.clearInstanceIfEqual(tlFactory);
            }
            ThreadLocalResteasyProviderFactory.pop();
          }
      }
   }

   private PropertyInjector getPropertyInjector()
   {
      return new PropertyInjectorImpl(clazz, providerFactory);
   }

   private void validate(HttpRequest request, T instance)
   {
      if (GetRestful.isRootResource(clazz))
      {
         ContextResolver<GeneralValidatorCDI> resolver = providerFactory.getContextResolver(GeneralValidatorCDI.class, MediaType.WILDCARD_TYPE);
         if (resolver != null)
         {
            validator = providerFactory.getContextResolver(GeneralValidatorCDI.class, MediaType.WILDCARD_TYPE).getContext(null);
         }
         if (validator != null && validator.isValidatableFromCDI(clazz))
         {
            validator.validate(request, instance);
            validator.checkViolationsfromCDI(request);
         }
      }
   }
}
