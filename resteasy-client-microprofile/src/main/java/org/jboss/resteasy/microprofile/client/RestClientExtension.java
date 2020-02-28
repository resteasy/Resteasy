package org.jboss.resteasy.microprofile.client;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionTarget;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessInjectionTarget;
import javax.enterprise.inject.spi.WithAnnotations;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RestClientExtension implements Extension {

    private Set<RestClientData> proxyTypes = new LinkedHashSet<>();

    private Set<Throwable> errors = new LinkedHashSet<>();

    private static BeanManager manager;

    private static final Logger LOGGER = Logger.getLogger(RestClientExtension.class);


    /**
     * Verify that CDI is active.
     */
    public void observeBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event, BeanManager beanManager) {
       manager = beanManager;
     }

    public void registerRestClient(@Observes
                                   @WithAnnotations(RegisterRestClient.class) ProcessAnnotatedType<?> type) {
        Class<?> javaClass = type.getAnnotatedType().getJavaClass();
        if (javaClass.isInterface()) {
            RegisterRestClient annotation = type.getAnnotatedType().getAnnotation(RegisterRestClient.class);
            Optional<String> maybeUri = extractBaseUri(annotation);
            Optional<String> maybeConfigKey = extractConfigKey(annotation);

            proxyTypes.add(new RestClientData(javaClass, maybeUri, maybeConfigKey));
            type.veto();
        } else {
            errors.add(new IllegalArgumentException("Rest client needs to be an interface " + javaClass));
        }
    }

    /**
     * Wrap InjectionTarget of JAX-RS components within JaxrsInjectionTarget
     * which takes care of JAX-RS property injection.
     *
     * @param <T> type
     * @param event event
     */
    public <T> void observeInjectionTarget(@Observes ProcessInjectionTarget<T> event)
    {
       if (event.getAnnotatedType() == null)
       { // check for resin's bug http://bugs.caucho.com/view.php?id=3967
          LOGGER.warn("ProcessInjectionTarget.getAnnotatedType() returned null. As a result, JAX-RS property injection will not work.");
          return;
       }

       if (ClientHeadersFactory.class.isAssignableFrom(event.getAnnotatedType().getJavaClass()))
       {
          event.setInjectionTarget(wrapInjectionTarget(event));
       }
    }

    protected <T> InjectionTarget<T> wrapInjectionTarget(ProcessInjectionTarget<T> event)
    {
       return new RestClientInjectionTarget<T>(event.getInjectionTarget(), event.getAnnotatedType().getJavaClass());
    }

    private Optional<String> extractBaseUri(RegisterRestClient annotation) {
        String baseUri = annotation.baseUri();
        return Optional.ofNullable("".equals(baseUri) ? null : baseUri);
    }

    private Optional<String> extractConfigKey(RegisterRestClient annotation) {
        String configKey = annotation.configKey();
        return Optional.ofNullable("".equals(configKey) ? null : configKey);
    }

    public void createProxy(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {
        for (RestClientData clientData : proxyTypes) {
            afterBeanDiscovery.addBean(new RestClientDelegateBean(clientData.javaClass, beanManager, clientData.baseUri, clientData.configKey));
        }
    }

    public void reportErrors(@Observes AfterDeploymentValidation afterDeploymentValidation) {
        for (Throwable error : errors) {
            afterDeploymentValidation.addDeploymentProblem(error);
        }
    }

    public static boolean isCDIActive() {
       return manager != null;
    }

    private static class RestClientData {
        private final Class<?> javaClass;
        private final Optional<String> baseUri;
        private final Optional<String> configKey;

        private RestClientData(final Class<?> javaClass, final Optional<String> baseUri, final Optional<String> configKey) {
            this.javaClass = javaClass;
            this.baseUri = baseUri;
            this.configKey = configKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RestClientData that = (RestClientData) o;
            return javaClass.equals(that.javaClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(javaClass);
        }
    }

    /**
     * Lifted from CdiConstructorInjector in resteasy-cdi
     */
    public static Object construct(Class<?> clazz)
    {
       Set<Bean<?>> beans = manager.getBeans(clazz);
       if (beans.size() == 0) {
          return null;
       }

       if (beans.size() > 1)
       {
          Set<Bean<?>> modifiableBeans = new HashSet<Bean<?>>();
          modifiableBeans.addAll(beans);
          // Ambiguous dependency may occur if a resource has subclasses
          // Therefore we remove those beans
          for (Iterator<Bean<?>> iterator = modifiableBeans.iterator(); iterator.hasNext();)
          {
             Bean<?> bean = iterator.next();
             if (!bean.getBeanClass().equals(clazz) && !bean.isAlternative())
             {
                // remove Beans that have clazz in their type closure but not as a base class
                iterator.remove();
             }
          }
          beans = modifiableBeans;
       }
       Bean<?> bean = manager.resolve(beans);
       if (bean == null) {
          return null;
       }
       CreationalContext<?> context = manager.createCreationalContext(bean);
       if (context == null) {
          return null;
       }
       return manager.getReference(bean, clazz, context);
    }
}
