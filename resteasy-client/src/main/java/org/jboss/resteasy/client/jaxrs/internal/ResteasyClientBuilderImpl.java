package org.jboss.resteasy.client.jaxrs.internal;

import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpAsyncClient4Engine;
import org.jboss.resteasy.client.jaxrs.engines.ClientHttpEngineBuilder43;
import org.jboss.resteasy.client.jaxrs.i18n.LogMessages;
import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryDelegate;
import org.jboss.resteasy.plugins.interceptors.AcceptEncodingGZIPFilter;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configuration;

import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction for creating Clients.  Allows SSL configuration.  Uses Apache Http Client under
 * the covers.  If used with other ClientHttpEngines though, all configuration options are ignored.
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ResteasyClientBuilderImpl extends ResteasyClientBuilder
{
   protected KeyStore truststore;
   protected KeyStore clientKeyStore;
   protected String clientPrivateKeyPassword;
   protected boolean disableTrustManager;
   protected HostnameVerificationPolicy policy = HostnameVerificationPolicy.WILDCARD;
   protected ResteasyProviderFactory providerFactory;
   protected ExecutorService asyncExecutor;
   protected ScheduledExecutorService scheduledExecutorService;
   protected boolean cleanupExecutor;
   protected SSLContext sslContext;
   protected Map<String, Object> properties = new HashMap<String, Object>();
   protected ClientHttpEngine httpEngine;
   protected int connectionPoolSize = 50;
   protected int maxPooledPerRoute = 0;
   protected long connectionTTL = -1;
   protected TimeUnit connectionTTLUnit = TimeUnit.MILLISECONDS;
   protected long socketTimeout = -1;
   protected TimeUnit socketTimeoutUnits = TimeUnit.MILLISECONDS;
   protected long establishConnectionTimeout = -1;
   protected TimeUnit establishConnectionTimeoutUnits = TimeUnit.MILLISECONDS;
   protected int connectionCheckoutTimeoutMs = -1;
   protected HostnameVerifier verifier = null;
   protected HttpHost defaultProxy;
   protected int responseBufferSize;
   protected List<String> sniHostNames = new ArrayList<>();
   protected boolean trustSelfSignedCertificates = true;
   protected boolean cookieManagementEnabled;

   /**
    * Changing the providerFactory will wipe clean any registered components or properties.
    *
    * @param providerFactory provider factory
    * @return the updated client builder instance
    */
   @Override
   public ResteasyClientBuilderImpl providerFactory(ResteasyProviderFactory providerFactory)
   {
      if (providerFactory instanceof LocalResteasyProviderFactory)
      {
         this.providerFactory = providerFactory;
      }
      else
      {
         this.providerFactory = new ResteasyProviderFactoryDelegate(providerFactory)
         {
            @Override
            public javax.ws.rs.RuntimeType getRuntimeType()
            {
               return RuntimeType.CLIENT;
            }
         };
      }
      return this;
   }

   /**
    * Executor to use to run AsyncInvoker invocations.
    *
    * @param asyncExecutor executor service
    * @return the updated client builder instance
    * @deprecated use {@link ResteasyClientBuilderImpl#executorService(ExecutorService)} instead
    */
   @Deprecated
   public ResteasyClientBuilderImpl asyncExecutor(ExecutorService asyncExecutor)
   {
      return asyncExecutor(asyncExecutor, false);
   }

   /**
    * Executor to use to run AsyncInvoker invocations.
    *
    * @param asyncExecutor executor service
    * @param cleanupExecutor true if the Client should close the executor when it is closed
    * @return the updated client builder instance
    */
   @Deprecated
   public ResteasyClientBuilderImpl asyncExecutor(ExecutorService asyncExecutor, boolean cleanupExecutor)
   {
      this.asyncExecutor = asyncExecutor;
      this.cleanupExecutor = cleanupExecutor;
      return this;
   }

   /**
    * If there is a connection pool, set the time to live in the pool.
    *
    * @param ttl time to live
    * @param unit the time unit of the ttl argument
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl connectionTTL(long ttl, TimeUnit unit)
   {
      this.connectionTTL = ttl;
      this.connectionTTLUnit = unit;
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl readTimeout(long timeout, TimeUnit unit)
   {
      this.socketTimeout = timeout;
      this.socketTimeoutUnits = unit;
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl connectTimeout(long timeout, TimeUnit unit)
   {
      this.establishConnectionTimeout = timeout;
      this.establishConnectionTimeoutUnits = unit;
      return this;
   }

   /**
    * If connection pooling enabled, how many connections to pool per url?
    *
    * @param maxPooledPerRoute max pool size per url
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl maxPooledPerRoute(int maxPooledPerRoute)
   {
      this.maxPooledPerRoute = maxPooledPerRoute;
      return this;
   }

   /**
    * If connection pooling is enabled, how long will we wait to get a connection?
    * @param timeout the timeout
    * @param unit the units the timeout is in
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl connectionCheckoutTimeout(long timeout, TimeUnit unit)
   {
      this.connectionCheckoutTimeoutMs = (int) TimeUnit.MILLISECONDS.convert(timeout, unit);
      return this;
   }

   /**
    * Number of connections allowed to pool.
    *
    * @param connectionPoolSize connection pool size
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl connectionPoolSize(int connectionPoolSize)
   {
      this.connectionPoolSize = connectionPoolSize;
      return this;
   }

   /**
    * Response stream is wrapped in a BufferedInputStream.  Default is 8192.  Value of 0 will not wrap it.
    * Value of -1 will use a SelfExpandingBufferedInputStream.
    *
    * @param size response buffer size
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl responseBufferSize(int size)
   {
      this.responseBufferSize = size;
      return this;
   }


   /**
    * Disable trust management and hostname verification.  <i>NOTE</i> this is a security
    * hole, so only set this option if you cannot or do not want to verify the identity of the
    * host you are communicating with.
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl disableTrustManager()
   {
      this.disableTrustManager = true;
      return this;
   }

   /**
    * SSL policy used to verify hostnames
    *
    * @param policy SSL policy
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl hostnameVerification(HostnameVerificationPolicy policy)
   {
      this.policy = policy;
      return this;
   }

   /**
    * Negates all ssl and connection specific configuration
    *
    * @param httpEngine http engine
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl httpEngine(ClientHttpEngine httpEngine)
   {
      this.httpEngine = httpEngine;
      return this;
   }

   public ResteasyClientBuilderImpl useAsyncHttpEngine()
   {
      this.httpEngine = new ApacheHttpAsyncClient4Engine(HttpAsyncClients.createSystem(), true);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl sslContext(SSLContext sslContext)
   {
      this.sslContext = sslContext;
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl trustStore(KeyStore truststore)
   {
      this.truststore = truststore;
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl keyStore(KeyStore keyStore, String password)
   {
      this.clientKeyStore = keyStore;
      this.clientPrivateKeyPassword = password;
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl keyStore(KeyStore keyStore, char[] password)
   {
      this.clientKeyStore = keyStore;
      this.clientPrivateKeyPassword = new String(password);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl property(String name, Object value)
   {
      getProviderFactory().property(name, value);
      return this;
   }

   /**
    * Adds a TLS/SSL SNI Host Name for authentication.
    *
    * @param sniHostNames host names
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl sniHostNames(String... sniHostNames) {
      this.sniHostNames.addAll(Arrays.asList(sniHostNames));
      return this;
   }

   /**
    * Specify a default proxy.  Default port and schema will be used.
    *
    * @param hostname host name
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl defaultProxy(String hostname)
   {
      return defaultProxy(hostname, -1, null);
   }

   /**
    * Specify a default proxy host and port.  Default schema will be used.
    *
    * @param hostname host name
    * @param port port
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl defaultProxy(String hostname, int port)
   {
      return defaultProxy(hostname, port, null);
   }

   /**
    * Specify default proxy.
    *
    * @param hostname host name
    * @param port port
    * @param scheme scheme
    * @return the updated client builder instance
    */
   public ResteasyClientBuilderImpl defaultProxy(String hostname, int port, final String scheme)
   {
      this.defaultProxy = hostname != null ? new HttpHost(hostname, port, scheme) : null;
      return this;
   }

   public ResteasyProviderFactory getProviderFactory()
   {
      if (providerFactory == null)
      {
         ClassLoader loader = null;
         if (System.getSecurityManager() == null) {
            loader = Thread.currentThread().getContextClassLoader();
         }else {
            try {
               loader = AccessController.doPrivileged(new PrivilegedExceptionAction<ClassLoader>() {
                  @Override
                  public ClassLoader run() throws Exception {
                     return Thread.currentThread().getContextClassLoader();
                  }
               });
            } catch (PrivilegedActionException pae) {
               throw new RuntimeException(pae);
            }
         }
         // create a new one
         providerFactory = new LocalResteasyProviderFactory(RegisterBuiltin.getClientInitializedResteasyProviderFactory(loader));

         ResteasyProviderFactory f = ResteasyProviderFactory.peekInstance();
         if (f != null)
         {
            providerFactory.initializeClientProviders(f);
         }
         // Execution of 'if' above overwrites providerFactory clientRequestFilterRegistry
         // Reregister provider as appropriate.
         if (RegisterBuiltin.isGZipEnabled()) {
            providerFactory.registerProvider(AcceptEncodingGZIPFilter.class, true);
         }
      }
      return providerFactory;
   }

   @Override
   public ResteasyClient build()
   {
      ClientConfiguration config = new ClientConfiguration(getProviderFactory());
      for (Map.Entry<String, Object> entry : properties.entrySet())
      {
         config.property(entry.getKey(), entry.getValue());
      }

      ExecutorService executor = asyncExecutor;

      if (executor == null)
      {
         cleanupExecutor = true;
         executor = Executors.newCachedThreadPool();
      }

      boolean resetProxy = false;
      if (this.defaultProxy == null) {
         resetProxy = true;
         // check for proxy config parameters
         setProxyIfNeeded(config);
      }
      ClientHttpEngine engine = httpEngine != null ? httpEngine : new ClientHttpEngineBuilder43().resteasyClientBuilder(this).build();
      if (resetProxy) {
         this.defaultProxy = null;
      }
      return createResteasyClient(engine, executor, cleanupExecutor, scheduledExecutorService, config);

   }

   /** This method sets http proxy if {@link ResteasyClientBuilder#PROPERTY_PROXY_HOST} is set in the properties.
    *
    * @param clientConfig client config
    */
   private void setProxyIfNeeded(ClientConfiguration clientConfig) {
      try {
         Object proxyHostProp = clientConfig.getProperty(ResteasyClientBuilder.PROPERTY_PROXY_HOST);
         if (proxyHostProp != null) {
            Object proxyPortProp = clientConfig.getProperty(ResteasyClientBuilder.PROPERTY_PROXY_PORT);
            // default if the port is not set or if it is not string or number
            Integer proxyPort = -1;
            if (proxyPortProp != null && proxyPortProp instanceof Number) {
               proxyPort = ((Number) proxyPortProp).intValue();
            } else if (proxyPortProp != null && proxyPortProp instanceof String) {
               proxyPort = Integer.parseInt((String) proxyPortProp);
            }
            Object proxySchemeProp = clientConfig.getProperty(ResteasyClientBuilder.PROPERTY_PROXY_SCHEME);
            defaultProxy((String)proxyHostProp, proxyPort, (String)proxySchemeProp);
         }
      } catch(Exception e) {
         // catch possible exceptions (in this case we do not set proxy at all)
         LogMessages.LOGGER.warn(Messages.MESSAGES.unableToSetHttpProxy(), e);
      }
   }

   protected ResteasyClient createResteasyClient(ClientHttpEngine engine,ExecutorService executor, boolean cleanupExecutor, ScheduledExecutorService scheduledExecutorService, ClientConfiguration config ) {
      return new ResteasyClientImpl(engine, executor, cleanupExecutor, scheduledExecutorService, config);
   }

   @Override
   public ResteasyClientBuilderImpl hostnameVerifier(HostnameVerifier verifier)
   {
      this.verifier = verifier;
      return this;
   }

   @Override
   public Configuration getConfiguration()
   {
      return getProviderFactory().getConfiguration();
   }

   @Override
   public ResteasyClientBuilderImpl register(Class<?> componentClass)
   {
      getProviderFactory().register(componentClass);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Class<?> componentClass, int priority)
   {
      getProviderFactory().register(componentClass, priority);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Class<?> componentClass, Class<?>... contracts)
   {
      getProviderFactory().register(componentClass, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Class<?> componentClass, Map<Class<?>, Integer> contracts)
   {
      getProviderFactory().register(componentClass, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Object component)
   {
      getProviderFactory().register(component);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Object component, int priority)
   {
      getProviderFactory().register(component, priority);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Object component, Class<?>... contracts)
   {
      getProviderFactory().register(component, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl register(Object component, Map<Class<?>, Integer> contracts)
   {
      getProviderFactory().register(component, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilderImpl withConfig(Configuration config)
   {
      providerFactory = new LocalResteasyProviderFactory();
      providerFactory.setProperties(config.getProperties());
      for (Class clazz : config.getClasses())
      {
         Map<Class<?>, Integer> contracts = config.getContracts(clazz);
         try {
            register(clazz, contracts);
         }
         catch (RuntimeException e) {
            throw new RuntimeException(Messages.MESSAGES.failedOnRegisteringClass(clazz.getName()), e);
         }
      }
      for (Object obj : config.getInstances())
      {
         Map<Class<?>, Integer> contracts = config.getContracts(obj.getClass());
         register(obj, contracts);
      }
      return this;
   }

   @Override
   public ResteasyClientBuilder executorService(ExecutorService executorService)
   {
      return asyncExecutor(executorService, false);
   }

   @Override
   public ResteasyClientBuilder executorService(ExecutorService executorService, boolean cleanupExecutor)
   {
      return asyncExecutor(executorService, cleanupExecutor);
   }

   @Override
   public ResteasyClientBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService)
   {
      this.scheduledExecutorService = scheduledExecutorService;
      return this;
   }

   @Override
   public long getConnectionTTL(TimeUnit unit)
   {
      return connectionTTLUnit.equals(unit) ? connectionTTL : unit.convert(connectionTTL, connectionTTLUnit);
   }

   @Override
   public int getMaxPooledPerRoute()
   {
      return maxPooledPerRoute;
   }

   @Override
   public long getConnectionCheckoutTimeout(TimeUnit unit)
   {
      return TimeUnit.MILLISECONDS.equals(unit) ? connectionCheckoutTimeoutMs : unit.convert(connectionCheckoutTimeoutMs, TimeUnit.MILLISECONDS);
   }

   @Override
   public int getConnectionPoolSize()
   {
      return connectionPoolSize;
   }

   @Override
   public int getResponseBufferSize()
   {
      return responseBufferSize;
   }

   @Override
   public boolean isTrustManagerDisabled()
   {
      return disableTrustManager;
   }

   @Override
   public boolean isTrustSelfSignedCertificates(){
      return trustSelfSignedCertificates;
   }

   @Override
   public void setIsTrustSelfSignedCertificates(boolean b){
      trustSelfSignedCertificates = b;
   }

   @Override
   public HostnameVerificationPolicy getHostnameVerification()
   {
      return policy;
   }

   @Override
   public ClientHttpEngine getHttpEngine()
   {
      return httpEngine;
   }

   @Override
   public boolean isUseAsyncHttpEngine()
   {
      return httpEngine != null && (httpEngine instanceof ApacheHttpAsyncClient4Engine);
   }

   @Override
   public List<String> getSniHostNames()
   {
      return sniHostNames;
   }

   @Override
   public String getDefaultProxyHostname()
   {
      return defaultProxy != null ? defaultProxy.getHostName() : null;
   }

   @Override
   public int getDefaultProxyPort()
   {
      return defaultProxy != null ? defaultProxy.getPort() : -1;
   }

   @Override
   public String getDefaultProxyScheme()
   {
      return defaultProxy != null ? defaultProxy.getSchemeName() : null;
   }

   @Override
   public long getReadTimeout(TimeUnit unit)
   {
      return socketTimeoutUnits.equals(unit) ? socketTimeout : unit.convert(socketTimeout, socketTimeoutUnits);
   }

   @Override
   public long getConnectionTimeout(TimeUnit unit)
   {
      return establishConnectionTimeoutUnits.equals(unit) ? establishConnectionTimeout : unit.convert(establishConnectionTimeout, establishConnectionTimeoutUnits);
   }

   @Override
   public SSLContext getSSLContext()
   {
      return sslContext;
   }

   @Override
   public KeyStore getKeyStore()
   {
      return clientKeyStore;
   }

   @Override
   public String getKeyStorePassword()
   {
      return clientPrivateKeyPassword;
   }

   @Override
   public KeyStore getTrustStore()
   {
      return truststore;
   }

   @Override
   public HostnameVerifier getHostnameVerifier()
   {
      return verifier;
   }

   @Override
   public ResteasyClientBuilder enableCookieManagement()
   {
      this.cookieManagementEnabled = true;
      return this;
   }

   @Override
   public boolean isCookieManagementEnabled()
   {
      return cookieManagementEnabled;
   }

}
