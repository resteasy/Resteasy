package org.jboss.resteasy.client.jaxrs;

import org.apache.http.HttpHost;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpAsyncClient4Engine;
import org.jboss.resteasy.client.jaxrs.i18n.LogMessages;
import org.jboss.resteasy.client.jaxrs.i18n.Messages;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.LocalResteasyProviderFactory;
import org.jboss.resteasy.microprofile.config.ResteasyConfigProvider;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
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
public class ResteasyClientBuilder extends ClientBuilder
{
   public enum HostnameVerificationPolicy
   {
      /**
       * Hostname verification is not done on the server's certificate
       */
      ANY,
      /**
       * Allows wildcards in subdomain names i.e. *.foo.com
       */
      WILDCARD,
      /**
       * CN must match hostname connecting to
       */
      STRICT
   }

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

   /**
    * Client properties to enable proxy. Proxy host property name (string).
    */
   public static final String PROPERTY_PROXY_HOST = "org.jboss.resteasy.jaxrs.client.proxy.host";
   /**
    * Proxy port property name (integer).
    */
   public static final String PROPERTY_PROXY_PORT = "org.jboss.resteasy.jaxrs.client.proxy.port";
   /**
    * Proxy scheme property name (string).
    */
   public static final String PROPERTY_PROXY_SCHEME = "org.jboss.resteasy.jaxrs.client.proxy.scheme";

   /**
    * Changing the providerFactory will wipe clean any registered components or properties.
    *
    * @param providerFactory provider factory
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder providerFactory(ResteasyProviderFactory providerFactory)
   {
      this.providerFactory = providerFactory;
      return this;
   }

   /**
    * Executor to use to run AsyncInvoker invocations.
    *
    * @param asyncExecutor executor service
    * @return an updated client builder instance
    * @deprecated use {@link ResteasyClientBuilder#executorService(ExecutorService)} instead
    */
   @Deprecated
   public ResteasyClientBuilder asyncExecutor(ExecutorService asyncExecutor)
   {
      return asyncExecutor(asyncExecutor, false);
   }

   /**
    * Executor to use to run AsyncInvoker invocations.
    *
    * @param asyncExecutor executor service
    * @param cleanupExecutor true if the Client should close the executor when it is closed
    * @return an updated client builder instance
    */
   @Deprecated
   public ResteasyClientBuilder asyncExecutor(ExecutorService asyncExecutor, boolean cleanupExecutor)
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
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder connectionTTL(long ttl, TimeUnit unit)
   {
      this.connectionTTL = ttl;
      this.connectionTTLUnit = unit;
      return this;
   }

   @Override
   public ResteasyClientBuilder readTimeout(long timeout, TimeUnit unit)
   {
      this.socketTimeout = timeout;
      this.socketTimeoutUnits = unit;
      return this;
   }

   /**
    * The timeout for waiting for data. A timeout value of zero is interpreted as an infinite timeout
    *
    * @param timeout the maximum time to wait
    * @param unit the time unit of the timeout argument
    * @return an updated client builder instance
    */
   @Deprecated
   public ResteasyClientBuilder socketTimeout(long timeout, TimeUnit unit)
   {
      return readTimeout(timeout, unit);
   }

   @Override
   public ResteasyClientBuilder connectTimeout(long timeout, TimeUnit unit)
   {
      this.establishConnectionTimeout = timeout;
      this.establishConnectionTimeoutUnits = unit;
      return this;
   }

   /**
    * When trying to make an initial socket connection, what is the timeout?
    *
    * @param timeout the maximum time to wait
    * @param unit the time unit of the timeout argument
    * @return an updated client builder instance
    */
   @Deprecated
   public ResteasyClientBuilder establishConnectionTimeout(long timeout, TimeUnit unit)
   {
      return connectTimeout(timeout, unit);
   }


   /**
    * If connection pooling enabled, how many connections to pool per url?
    *
    * @param maxPooledPerRoute max pool size per url
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder maxPooledPerRoute(int maxPooledPerRoute)
   {
      this.maxPooledPerRoute = maxPooledPerRoute;
      return this;
   }

   /**
    * If connection pooling is enabled, how long will we wait to get a connection?
    * @param timeout the timeout
    * @param unit the units the timeout is in
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder connectionCheckoutTimeout(long timeout, TimeUnit unit)
   {
      this.connectionCheckoutTimeoutMs = (int) TimeUnit.MILLISECONDS.convert(timeout, unit);
      return this;
   }

   /**
    * Number of connections allowed to pool.
    *
    * @param connectionPoolSize connection pool size
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder connectionPoolSize(int connectionPoolSize)
   {
      this.connectionPoolSize = connectionPoolSize;
      return this;
   }

   /**
    * Response stream is wrapped in a BufferedInputStream.  Default is 8192.  Value of 0 will not wrap it.
    * Value of -1 will use a SelfExpandingBufferedInputStream.
    *
    * @param size response buffer size
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder responseBufferSize(int size)
   {
      this.responseBufferSize = size;
      return this;
   }


   /**
    * Disable trust management and hostname verification.  <i>NOTE</i> this is a security
    * hole, so only set this option if you cannot or do not want to verify the identity of the
    * host you are communicating with.
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder disableTrustManager()
   {
      this.disableTrustManager = true;
      return this;
   }

   /**
    * When the user is not using a TrustManager (see disableTrustManager) and
    * does not define an SSLContext object but they want all defined trustStores
    * to use the TrustSelfSignedCertificates trust strategy set this value to true.
    *
    * @param b A value of true assigns trust strategy TrustSelfSignedCertificates
    *          to the trustStores.  A value of false assigns a null to the trust
    *          strategy. The default value is true in order to maintain backward
    *          compatibility.
    */

   public void setIsTrustSelfSignedCertificates(boolean b){
      trustSelfSignedCertificates = b;
   }

   public boolean isTrustSelfSignedCertificates(){
      return trustSelfSignedCertificates;
   }


   /**
    * SSL policy used to verify hostnames
    *
    * @param policy SSL policy
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder hostnameVerification(HostnameVerificationPolicy policy)
   {
      this.policy = policy;
      return this;
   }

   /**
    * Negates all ssl and connection specific configuration
    *
    * @param httpEngine http engine
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder httpEngine(ClientHttpEngine httpEngine)
   {
      this.httpEngine = httpEngine;
      return this;
   }

   public ResteasyClientBuilder useAsyncHttpEngine()
   {
      this.httpEngine = new ApacheHttpAsyncClient4Engine(HttpAsyncClients.createSystem(), true);
      return this;
   }

   @Override
   public ResteasyClientBuilder sslContext(SSLContext sslContext)
   {
      this.sslContext = sslContext;
      return this;
   }

   @Override
   public ResteasyClientBuilder trustStore(KeyStore truststore)
   {
      this.truststore = truststore;
      return this;
   }

   @Override
   public ResteasyClientBuilder keyStore(KeyStore keyStore, String password)
   {
      this.clientKeyStore = keyStore;
      this.clientPrivateKeyPassword = password;
      return this;
   }

   @Override
   public ResteasyClientBuilder keyStore(KeyStore keyStore, char[] password)
   {
      this.clientKeyStore = keyStore;
      this.clientPrivateKeyPassword = new String(password);
      return this;
   }

   @Override
   public ResteasyClientBuilder property(String name, Object value)
   {
      getProviderFactory().property(name, value);
      return this;
   }

   /**
    * Adds a TLS/SSL SNI Host Name for authentication.
    *
    * @param sniHostNames host names
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder sniHostNames(String... sniHostNames) {
      this.sniHostNames.addAll(Arrays.asList(sniHostNames));
      return this;
   }

   /**
    * Specify a default proxy.  Default port and schema will be used.
    *
    * @param hostname host name
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder defaultProxy(String hostname)
   {
      return defaultProxy(hostname, -1, null);
   }

   /**
    * Specify a default proxy host and port.  Default schema will be used.
    *
    * @param hostname host name
    * @param port port
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder defaultProxy(String hostname, int port)
   {
      return defaultProxy(hostname, port, null);
   }

   /**
    * Specify default proxy.
    *
    * @param hostname host name
    * @param port port
    * @param scheme scheme
    * @return an updated client builder instance
    */
   public ResteasyClientBuilder defaultProxy(String hostname, int port, final String scheme)
   {
      this.defaultProxy = hostname != null ? new HttpHost(hostname, port, scheme) : null;
      return this;
   }

   protected ResteasyProviderFactory getProviderFactory()
   {
      if (providerFactory == null)
      {
         // create a new one
         providerFactory = new LocalResteasyProviderFactory(new ResteasyProviderFactory());

         if (!AccessController.doPrivileged((PrivilegedAction<Boolean>) () ->
         {
            final String propertyValue = ResteasyConfigProvider.getConfig().getOptionalValue("resteasy.client.providers.annotations.disabled", String.class).orElse(null);
            return (propertyValue == null) ? Boolean.FALSE : Boolean.valueOf(propertyValue.isEmpty() ? "true" : propertyValue);
         }))
         {
            if (ResteasyProviderFactory.peekInstance() != null)
            {
               providerFactory.initializeClientProviders(ResteasyProviderFactory.getInstance());
            }
         }

         RegisterBuiltin.register(providerFactory);
      }
      return providerFactory;
   }

   @Deprecated
   public ResteasyClient buildOld()
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
         executor = Executors.newFixedThreadPool(10);
      }

      boolean resetProxy = false;
      if (this.defaultProxy == null) {
         resetProxy = true;
         // check for proxy config parameters
         setProxyIfNeeded(config);
      }
      ClientHttpEngine engine = httpEngine != null ? httpEngine : new ClientHttpEngineBuilder4().resteasyClientBuilder(this).build();
      if (resetProxy) {
         this.defaultProxy = null;
      }
      return createResteasyClient(engine, executor, cleanupExecutor, scheduledExecutorService, config);

   }

   @Override
   public ResteasyClient build()
   {
      if (HTTPClientVersionCheck.isUseOldHTTPClient() || !HTTPClientVersionCheck.isNewHTTPClientAvailable()) {
         return buildOld();
      }
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
      return new ResteasyClient(engine, executor, cleanupExecutor, scheduledExecutorService, config);
   }

   protected void prepareSocketForSni(SSLSocket socket)
   {
      if(!sniHostNames.isEmpty()) {
         List<SNIServerName> sniNames = new ArrayList<>(sniHostNames.size());
         for(String sniHostName : sniHostNames) {
            sniNames.add(new SNIHostName(sniHostName));
         }

         SSLParameters sslParameters = socket.getSSLParameters();
         sslParameters.setServerNames(sniNames);
         socket.setSSLParameters(sslParameters);
      }
   }

   @Override
   public ResteasyClientBuilder hostnameVerifier(HostnameVerifier verifier)
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
   public ResteasyClientBuilder register(Class<?> componentClass)
   {
      getProviderFactory().register(componentClass);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Class<?> componentClass, int priority)
   {
      getProviderFactory().register(componentClass, priority);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Class<?> componentClass, Class<?>... contracts)
   {
      getProviderFactory().register(componentClass, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Class<?> componentClass, Map<Class<?>, Integer> contracts)
   {
      getProviderFactory().register(componentClass, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Object component)
   {
      getProviderFactory().register(component);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Object component, int priority)
   {
      getProviderFactory().register(component, priority);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Object component, Class<?>... contracts)
   {
      getProviderFactory().register(component, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilder register(Object component, Map<Class<?>, Integer> contracts)
   {
      getProviderFactory().register(component, contracts);
      return this;
   }

   @Override
   public ResteasyClientBuilder withConfig(Configuration config)
   {
      providerFactory = new LocalResteasyProviderFactory(new ResteasyProviderFactory());
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
   public ClientBuilder executorService(ExecutorService executorService)
   {
      return asyncExecutor(executorService, false);
   }

   @Override
   public ClientBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService)
   {
      this.scheduledExecutorService = scheduledExecutorService;
      return this;
   }
}
