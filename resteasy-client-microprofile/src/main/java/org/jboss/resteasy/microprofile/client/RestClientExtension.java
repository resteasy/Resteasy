package org.jboss.resteasy.microprofile.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class RestClientExtension implements Extension {

    private Set<RestClientData> proxyTypes = new LinkedHashSet<>();

    private Set<Throwable> errors = new LinkedHashSet<>();

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
}
