package org.jboss.resteasy.microprofile.client;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.URLConnectionClientEngineBuilder;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.microprofile.client.async.AsyncInterceptorRxInvokerProvider;
import org.jboss.resteasy.microprofile.client.async.AsyncInvocationInterceptorHandler;
import org.jboss.resteasy.microprofile.client.header.ClientHeaderProviders;
import org.jboss.resteasy.microprofile.client.header.ClientHeadersRequestFilter;
import org.jboss.resteasy.microprofile.client.impl.MpClientBuilderImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.ResteasyUriBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.BeanParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Priorities;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ParamConverterProvider;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder.*;

public class RestClientBuilderImpl implements RestClientBuilder {

    private static final String RESTEASY_PROPERTY_PREFIX = "resteasy.";

    private static final String DEFAULT_MAPPER_PROP = "microprofile.rest.client.disable.default.mapper";

    private static final DefaultMediaTypeFilter DEFAULT_MEDIA_TYPE_FILTER = new DefaultMediaTypeFilter();
    public static final MethodInjectionFilter METHOD_INJECTION_FILTER = new MethodInjectionFilter();
    public static final ClientHeadersRequestFilter HEADERS_REQUEST_FILTER = new ClientHeadersRequestFilter();

    static boolean SSL_ENABLED = true;
    static ResteasyProviderFactory PROVIDER_FACTORY;

    public static void setSslEnabled(boolean enabled) {
        SSL_ENABLED = enabled;
    }

    public static void setProviderFactory(ResteasyProviderFactory providerFactory) {
        PROVIDER_FACTORY = providerFactory;
    }

    public RestClientBuilderImpl() {
        builderDelegate = new MpClientBuilderImpl();

        if (PROVIDER_FACTORY != null) {
            ResteasyProviderFactory localProviderFactory = new LocalResteasyProviderFactory(PROVIDER_FACTORY);
            ResteasyProviderFactory f = ResteasyProviderFactory.peekInstance();
            if (f != null) {
                localProviderFactory.initializeClientProviders(f);
            }
            builderDelegate.providerFactory(localProviderFactory);
        }

        configurationWrapper = new ConfigurationWrapper(builderDelegate.getConfiguration());

        try {
            // configuration MP may not be available.
            config = ConfigProvider.getConfig();
        } catch (Throwable e) {

        }
    }

    public Configuration getConfigurationWrapper() {
        return configurationWrapper;
    }

    @Override
    public RestClientBuilder baseUrl(URL url) {
        try {
            baseURI = url.toURI();
            return this;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public RestClientBuilder baseUri(URI uri) {
        baseURI = uri;
        return this;
    }

    @Override
    public RestClientBuilder connectTimeout(long l, TimeUnit timeUnit) {
        connectTimeout = l;
        connectTimeoutUnit = timeUnit;
        return this;
    }

    @Override
    public RestClientBuilder readTimeout(long time, TimeUnit timeUnit) {
        readTimeout = time;
        readTimeoutUnit = timeUnit;
        return this;
    }


    @Override
    public RestClientBuilder sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    @Override
    public RestClientBuilder trustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    @Override
    public RestClientBuilder keyStore(KeyStore keyStore, String keystorePassword) {
        this.keyStore = keyStore;
        this.keystorePassword = keystorePassword;
        return this;
    }

    @Override
    public RestClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    @Override
    public RestClientBuilder executorService(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ExecutorService must not be null");
        }
        executorService = executor;
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T build(Class<T> aClass) throws IllegalStateException, RestClientDefinitionException {

        RestClientListeners.get().forEach(listener -> listener.onNewClient(aClass, this));

        // Interface validity
        verifyInterface(aClass);

        if (baseURI == null) {
            throw new IllegalStateException("Neither baseUri nor baseUrl was specified");
        }

        // Provider annotations
        RegisterProvider[] providers = aClass.getAnnotationsByType(RegisterProvider.class);

        for (RegisterProvider provider : providers) {
            register(provider.value(), provider.priority());
        }

        // Default exception mapper
        if (!isMapperDisabled()) {
            register(DefaultResponseExceptionMapper.class);
        }

        builderDelegate.register(new ExceptionMapping(localProviderInstances), 1);

        ClassLoader classLoader = aClass.getClassLoader();

        List<String> noProxyHosts = Arrays.asList(
                System.getProperty("http.nonProxyHosts", "localhost|127.*|[::1]").split("|"));
        String envProxyHost = System.getProperty("http.proxyHost");

        T actualClient;
        ResteasyClient client;

        ResteasyClientBuilder resteasyClientBuilder;
        if (envProxyHost != null && !noProxyHosts.contains(baseURI.getHost())) {
            // Use proxy, if defined in the env variables
            resteasyClientBuilder = builderDelegate.defaultProxy(
                    envProxyHost,
                    Integer.parseInt(System.getProperty("http.proxyPort", "80")));
        } else {
            // Search for proxy settings passed in the client builder, if passed and use them if found
            String userProxyHost = Optional.ofNullable(getConfiguration().getProperty(PROPERTY_PROXY_HOST))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElse(null);

            Integer userProxyPort = Optional.ofNullable(getConfiguration().getProperty(PROPERTY_PROXY_PORT))
                    .filter(Integer.class::isInstance)
                    .map(Integer.class::cast)
                    .orElse(null);

            String userProxyScheme = Optional.ofNullable(getConfiguration().getProperty(PROPERTY_PROXY_SCHEME))
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElse(null);

            if (userProxyHost != null && userProxyPort != null) {
                resteasyClientBuilder = builderDelegate.defaultProxy(userProxyHost, userProxyPort, userProxyScheme);
            } else {
                //no proxy
                resteasyClientBuilder = builderDelegate;
            }
        }

        if (this.executorService != null) {
           ExecutorService executor = AsyncInvocationInterceptorHandler.wrapExecutorService(this.executorService);
           resteasyClientBuilder.executorService(executor);
        } else {
           ExecutorService executor = AsyncInvocationInterceptorHandler.wrapExecutorService(Executors.newCachedThreadPool());
           resteasyClientBuilder.executorService(executor, true);
        }
        resteasyClientBuilder.register(DEFAULT_MEDIA_TYPE_FILTER);
        resteasyClientBuilder.register(METHOD_INJECTION_FILTER);
        resteasyClientBuilder.register(HEADERS_REQUEST_FILTER);
        resteasyClientBuilder.sslContext(sslContext);
        resteasyClientBuilder.trustStore(trustStore);
        resteasyClientBuilder.keyStore(keyStore, keystorePassword);

        resteasyClientBuilder.hostnameVerifier(hostnameVerifier);
        resteasyClientBuilder.setIsTrustSelfSignedCertificates(false);

        if (readTimeout != null) {
            resteasyClientBuilder.readTimeout(readTimeout, readTimeoutUnit);
        }
        if (connectTimeout != null) {
            resteasyClientBuilder.connectTimeout(connectTimeout, connectTimeoutUnit);
        }

        if (!SSL_ENABLED) {
            resteasyClientBuilder.httpEngine(new URLConnectionClientEngineBuilder().resteasyClientBuilder(resteasyClientBuilder).build());
            resteasyClientBuilder.sslContext(null);
            resteasyClientBuilder.trustStore(null);
            resteasyClientBuilder.keyStore(null, "");
        }

        client = resteasyClientBuilder
                .build();
        client.register(AsyncInterceptorRxInvokerProvider.class);

        actualClient = client.target(baseURI)
                .proxyBuilder(aClass)
                .classloader(classLoader)
                .defaultConsumes(MediaType.APPLICATION_JSON)
                .defaultProduces(MediaType.APPLICATION_JSON).build();

        Class<?>[] interfaces = new Class<?>[3];
        interfaces[0] = aClass;
        interfaces[1] = RestClientProxy.class;
        interfaces[2] = Closeable.class;

        T proxy = (T) Proxy.newProxyInstance(classLoader, interfaces, new ProxyInvocationHandler(aClass, actualClient, getLocalProviderInstances(), client, asyncInterceptorFactories));
        ClientHeaderProviders.registerForClass(aClass, proxy);
        return proxy;
    }

    private boolean isMapperDisabled() {
        boolean disabled = false;
        Optional<Boolean> defaultMapperProp = config == null ? Optional.empty() : config.getOptionalValue(DEFAULT_MAPPER_PROP, Boolean.class);

        // disabled through config api
        if (defaultMapperProp.isPresent() && defaultMapperProp.get().equals(Boolean.TRUE)) {
            disabled = true;
        } else if (!defaultMapperProp.isPresent()) {

            // disabled through jaxrs property
            try {
                Object property = builderDelegate.getConfiguration().getProperty(DEFAULT_MAPPER_PROP);
                if (property != null) {
                    disabled = (Boolean) property;
                }
            } catch (Throwable e) {
                // ignore cast exception
            }
        }
        return disabled;
    }

    private String getReflectName(AnnotatedElement element) {
        if (element instanceof Parameter) {
            return ((Parameter)element).getName();
        } else if (element instanceof Field) {
            return ((Field)element).getName();
        } else if (element instanceof Method) {
            Method m = (Method)element;
            if (!m.getName().startsWith("get")) return null;
            return Character.toLowerCase(m.getName().charAt(3)) + m.getName().substring(4);
        }
        return null;
    }

    private String getPathParamName(AnnotatedElement element) {
        if (element.isAnnotationPresent(PathParam.class)) {
            PathParam pp = element.getAnnotation(PathParam.class);
            return pp.value();
        } else if (element.isAnnotationPresent(org.jboss.resteasy.annotations.jaxrs.PathParam.class)) {
            org.jboss.resteasy.annotations.jaxrs.PathParam pp = element.getAnnotation(org.jboss.resteasy.annotations.jaxrs.PathParam.class);
            if (pp.value().length() > 0) return pp.value();
            return getReflectName(element);
        }
        return null;
    }

    private void verifyBeanPathParam(Class<?> beanType, Map<String, Object> paramMap) {
        for (Field field : beanType.getDeclaredFields()) {
            String name = getPathParamName(field);
            if (name != null) {
                paramMap.put(name, "foobar");
            }
        }

        for (Method m : beanType.getDeclaredMethods()) {
            String name = getPathParamName(m);
            if (name != null) {
                paramMap.put(name, "foobar");
            }

        }
    }

    private <T> void verifyInterface(Class<T> typeDef) {

        Method[] methods = typeDef.getMethods();

        // multiple verbs
        for (Method method : methods) {
            boolean hasHttpMethod = false;
            for (Annotation annotation : method.getAnnotations()) {
                boolean isHttpMethod = (annotation.annotationType().getAnnotation(HttpMethod.class) != null);
                if (!hasHttpMethod && isHttpMethod) {
                    hasHttpMethod = true;
                } else if (hasHttpMethod && isHttpMethod) {
                    throw new RestClientDefinitionException("Ambiguous @Httpmethod defintion on type " + typeDef);
                }
            }
        }

        // invalid parameter
        Path classPathAnno = typeDef.getAnnotation(Path.class);

        ResteasyUriBuilder template = null;
        for (Method method : methods) {
            Path methodPathAnno = method.getAnnotation(Path.class);
            if (methodPathAnno != null) {
                template = classPathAnno == null ? (ResteasyUriBuilder) UriBuilder.fromUri(methodPathAnno.value())
                        : (ResteasyUriBuilder) UriBuilder.fromUri(classPathAnno.value() + "/" + methodPathAnno.value());
            } else if (classPathAnno != null) {
                template = (ResteasyUriBuilder) UriBuilder.fromUri(classPathAnno.value());
            }

            if (template == null) {
                continue;
            }

            // it's not executed, so this can be anything - but a hostname needs to present
            template.host("localhost");

            Set<String> allVariables = new HashSet<>(template.getPathParamNamesInDeclarationOrder());
            Map<String, Object> paramMap = new HashMap<>();
            for (Parameter p : method.getParameters()) {
                PathParam pathParam = p.getAnnotation(PathParam.class);
                if (pathParam != null) {
                    paramMap.put(pathParam.value(), "foobar");
                } else if (p.isAnnotationPresent(org.jboss.resteasy.annotations.jaxrs.PathParam.class)){
                    org.jboss.resteasy.annotations.jaxrs.PathParam rePathParam = p.getAnnotation(org.jboss.resteasy.annotations.jaxrs.PathParam.class);
                    String name = rePathParam.value() == null || rePathParam.value().length() == 0 ? p.getName() : rePathParam.value();
                    paramMap.put(name, "foobar");
                } else if (p.isAnnotationPresent(BeanParam.class)) {
                    verifyBeanPathParam(p.getType(), paramMap);
                }
            }

            if (allVariables.size() != paramMap.size()) {
                throw new RestClientDefinitionException("Parameters and variables don't match on " + typeDef + "::" + method.getName());
            }

            try {
                template.resolveTemplates(paramMap, false).build();
            } catch (IllegalArgumentException ex) {
                throw new RestClientDefinitionException("Parameter names don't match variable names on " + typeDef + "::" + method.getName(), ex);
            }

        }
    }

    @Override
    public Configuration getConfiguration() {
        return getConfigurationWrapper();
    }

    @Override
    public RestClientBuilder property(String name, Object value) {
        if (name.startsWith(RESTEASY_PROPERTY_PREFIX)) {
            // Makes it possible to configure some of the ResteasyClientBuilder delegate properties
            String builderMethodName = name.substring(RESTEASY_PROPERTY_PREFIX.length());
            Method builderMethod = Arrays.stream(ResteasyClientBuilder.class.getMethods())
                    .filter(m -> builderMethodName.equals(m.getName()) && m.getParameterTypes().length >= 1)
                    .findFirst()
                    .orElse(null);
            if (builderMethod == null) {
                throw new IllegalArgumentException("ResteasyClientBuilder setter method not found: " + builderMethodName);
            }
            Object[] arguments;
            if (builderMethod.getParameterTypes().length > 1) {
                if (value instanceof List) {
                    arguments = ((List<?>) value).toArray();
                } else {
                    throw new IllegalArgumentException("Value must be an instance of List<> for ResteasyClientBuilder setter method: " + builderMethodName);
                }
            } else {
                arguments = new Object[] { value };
            }
            try {
                builderMethod.invoke(builderDelegate, arguments);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException("Unable to invoke ResteasyClientBuilder method: " + builderMethodName, e);
            }
        }
        builderDelegate.property(name, value);
        return this;
    }

    private static Object newInstanceOf(Class<?> clazz) {
        if (PROVIDER_FACTORY != null) {
            return PROVIDER_FACTORY.injectedInstance(clazz);
        } else {
            try {
                return clazz.newInstance();
            } catch (Throwable t) {
                throw new RuntimeException("Failed to register " + clazz, t);
            }
        }
    }

    @Override
    public RestClientBuilder register(Class<?> aClass) {
        register(newInstanceOf(aClass));
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, int i) {

        register(newInstanceOf(aClass), i);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, Class<?>[] classes) {
        register(newInstanceOf(aClass), classes);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, Map<Class<?>, Integer> map) {
        register(newInstanceOf(aClass), map);
        return this;
    }

    @Override
    public RestClientBuilder register(Object o) {
        if (o instanceof ResponseExceptionMapper) {
            ResponseExceptionMapper mapper = (ResponseExceptionMapper) o;
            register(mapper, mapper.getPriority());
        } else if (o instanceof ParamConverterProvider) {
            register(o, Priorities.USER);
        } else if (o instanceof AsyncInvocationInterceptorFactory) {
            asyncInterceptorFactories.add((AsyncInvocationInterceptorFactory) o);
        } else {
            builderDelegate.register(o);
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, int i) {
        if (o instanceof ResponseExceptionMapper) {

            // local
            ResponseExceptionMapper mapper = (ResponseExceptionMapper) o;
            HashMap<Class<?>, Integer> contracts = new HashMap<>();
            contracts.put(ResponseExceptionMapper.class, i);
            registerLocalProviderInstance(mapper, contracts);

            // delegate
            builderDelegate.register(mapper, i);

        } else if (o instanceof ParamConverterProvider) {

            // local
            ParamConverterProvider converter = (ParamConverterProvider) o;
            HashMap<Class<?>, Integer> contracts = new HashMap<>();
            contracts.put(ParamConverterProvider.class, i);
            registerLocalProviderInstance(converter, contracts);

            // delegate
            builderDelegate.register(converter, i);

        } else if (o instanceof AsyncInvocationInterceptorFactory) {
            asyncInterceptorFactories.add((AsyncInvocationInterceptorFactory) o);
        } else {
            builderDelegate.register(o, i);
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, Class<?>[] classes) {

        // local
        for (Class<?> aClass : classes) {
            if (aClass.isAssignableFrom(ResponseExceptionMapper.class)) {
                register(o);
            }
        }

        // other
        builderDelegate.register(o, classes);
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, Map<Class<?>, Integer> map) {

        if (o instanceof ResponseExceptionMapper) {

            // local
            ResponseExceptionMapper mapper = (ResponseExceptionMapper) o;
            HashMap<Class<?>, Integer> contracts = new HashMap<>();
            contracts.put(ResponseExceptionMapper.class, map.get(ResponseExceptionMapper.class));
            registerLocalProviderInstance(mapper, contracts);

            // other
            builderDelegate.register(o, map);

        } else {
            builderDelegate.register(o, map);
        }

        return this;
    }

    public Set<Object> getLocalProviderInstances() {
        return localProviderInstances;
    }

    public void registerLocalProviderInstance(Object provider, Map<Class<?>, Integer> contracts) {
        for (Object registered : getLocalProviderInstances()) {
            if (registered == provider) {
                // System.out.println("Provider already registered " + provider.getClass().getName());
                return;
            }
        }

        localProviderInstances.add(provider);
        configurationWrapper.registerLocalContract(provider.getClass(), contracts);
    }

    ResteasyClientBuilder getBuilderDelegate() {
        return builderDelegate;
    }

    private final ResteasyClientBuilder builderDelegate;

    private final ConfigurationWrapper configurationWrapper;

    private Config config;

    private ExecutorService executorService;

    private URI baseURI;

    private Long connectTimeout;
    private TimeUnit connectTimeoutUnit;

    private Long readTimeout;
    private TimeUnit readTimeoutUnit;

    private SSLContext sslContext;
    private KeyStore trustStore;
    private KeyStore keyStore;
    private String keystorePassword;
    private HostnameVerifier hostnameVerifier;


    private Set<Object> localProviderInstances = new HashSet<>();

    private final List<AsyncInvocationInterceptorFactory> asyncInterceptorFactories = new ArrayList<>();
}
