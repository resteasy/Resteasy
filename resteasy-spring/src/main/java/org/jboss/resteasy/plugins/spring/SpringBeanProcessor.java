package org.jboss.resteasy.plugins.spring;

import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.plugins.spring.i18n.Messages;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.PropertyInjector;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.GetRestful;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
//import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * The processor will register any bean annotated with @Path or @Provider into
 * the Resteasy framework.
 * </p>
 * <p>
 * It also sets up Registry and ResteasyProviderFactory to be autowirable via @Autowire
 * in Controllers/service layers.
 * </p>
 * <p>
 * There's quite a bit of spring integration functionality under the covers:
 * </p>
 * <ol>
 * <li>@Providers, such as RESTEasy interceptors and String converters have to
 * be registered in RESTEasy before resources and registers. That gets a bit
 * tricky, so depends-on functionality is used as well</li>
 * </ol>
 *
 * @author <a href="mailto:sduskis@gmail.com">Solomon Duskis</a>
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class SpringBeanProcessor implements BeanFactoryPostProcessor, SmartApplicationListener
{
   protected Registry registry;
   protected ResteasyProviderFactory providerFactory;
   protected Dispatcher dispatcher;
   protected ResteasyDeployment deployment;

   protected Set<String> resourceFactoryNames;
   protected Map<String, SpringResourceFactory> resourceFactories = new HashMap<String, SpringResourceFactory>();

   protected Set<String> providerNames = new HashSet<String>();
   private Set<String> registrations = new HashSet<String>();

   private int order;

   protected class ResteasyBeanPostProcessor implements BeanPostProcessor
   {
      private ConfigurableListableBeanFactory beanFactory;

      protected ResteasyBeanPostProcessor(final ConfigurableListableBeanFactory beanFactory)
      {
         this.beanFactory = beanFactory;
      }

      public Object postProcessBeforeInitialization(Object bean, String beanName)
              throws BeansException
      {
         return bean;
      }

      /**
       * This method is invoked after postProcessBeanFactory.
       * <p>
       * this method is invoked when ever a new bean is created. This will
       * perform the following:
       * <ol>
       * <li>RESTEasy injection of singleton @Providers, as well as @Provider
       * registration</li>
       * <li>either singleton or request/prototype RESTeasy injection... but not
       * registration. The RESTEasy registration happens in the
       * onApplicationEvent() below, which happens at the end of the Spring
       * life-cycle</li>
       * <li>merges the {@link ResteasyDeployment} bean with the user deployment</li>
       * </ol>
       * @param bean bean
       * @param beanName bean name
       * @see SpringBeanProcessor#postProcessBeanFactory(ConfigurableListableBeanFactory)
       */
      public Object postProcessAfterInitialization(Object bean, String beanName)
              throws BeansException
      {
         if (providerNames.contains(beanName))
         {
            PropertyInjector injector = getInjector(AopUtils.getTargetClass(bean));
            injector.inject(bean, false);
            providerFactory.registerProviderInstance(bean);
         }

         else if(registrations.contains(beanName) && bean instanceof ResteasyRegistration)
         {
            ResteasyRegistration registration = (ResteasyRegistration)bean;
            String registeredBeanName = registration.getBeanName();
            BeanDefinition beanDef = beanFactory.getBeanDefinition(registeredBeanName);
            Class<?> beanClass = getBeanClass(registeredBeanName, beanDef, beanFactory);
            SpringResourceFactory resourceFactory = new SpringResourceFactory(registeredBeanName, beanFactory, beanClass);
            resourceFactory.setContext(registration.getContext());
            resourceFactories.put(registeredBeanName, resourceFactory);
         }

         else if (bean instanceof ResteasyDeployment && deployment != null) {
            ResteasyDeployment beanDeployment = (ResteasyDeployment) bean;
            deployment.merge(beanDeployment);
            deployment.start();
         }

         else
         {
            SpringResourceFactory resourceFactory = resourceFactories.get(beanName);
            if (resourceFactory != null)
            {
               inject(beanName, bean, getInjector(resourceFactory.getScannableClass()));
            }
         }

         return bean;
      }

      public PropertyInjector getInjector(Class<?> clazz)
      {
         return providerFactory.getInjectorFactory().createPropertyInjector(clazz, providerFactory);
      }

      public void inject(String beanName, Object bean, PropertyInjector propertyInjector)
      {
         if (propertyInjector == null)
         {
            return;
         }
         HttpRequest request = ResteasyContext.getContextData(HttpRequest.class);
         if (request == null || isSingleton(beanName))
         {
            propertyInjector.inject(bean, false);
         }
         else
         {
            HttpResponse response = ResteasyContext.getContextData(HttpResponse.class);
            propertyInjector.inject(request, response, bean, false);
         }
      }

      private boolean isSingleton(String beanName)
      {
         boolean isSingleton = false;
         try
         {
            BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
            isSingleton = beanDef.isSingleton();
         }
         catch (org.springframework.beans.factory.NoSuchBeanDefinitionException nsbde)
         {
            // cannot distinguish between singleton & prototype
         }
         return isSingleton;
      }
   }

   public SpringBeanProcessor(final ResteasyDeployment deployment)
   {
      this(deployment.getDispatcher(), deployment.getRegistry(), deployment.getProviderFactory());
      this.deployment = deployment;
   }

   public SpringBeanProcessor(final Dispatcher dispatcher)
   {
      this(dispatcher, dispatcher.getRegistry(), dispatcher.getProviderFactory());
   }

   public SpringBeanProcessor(final Dispatcher dispatcher, final Registry registry,
                              final ResteasyProviderFactory providerFactory)
   {
      this.setRegistry(registry);
      this.setProviderFactory(providerFactory);
      this.setDispatcher(dispatcher);
   }

   public SpringBeanProcessor()
   {
   }

   @Autowired
   public Registry getRegistry()
   {
      return registry;
   }

   public void setRegistry(Registry registry)
   {
      this.registry = registry;
   }

   public ResteasyProviderFactory getProviderFactory()
   {
      return providerFactory;
   }

   public void setProviderFactory(ResteasyProviderFactory providerFactory)
   {
      this.providerFactory = providerFactory;
   }

   public Dispatcher getDispatcher()
   {
      return dispatcher;
   }

   public void setDispatcher(Dispatcher dispatcher)
   {
      this.dispatcher = dispatcher;
   }

   /**
    * <p>
    * This method keeps track of @Provider and resources for future use. It also
    * registers the RESTEasy Registry, ProviderFactry, and Dispatcher for @Autowire
    * injection.
    * </p>
    * <p>
    * Beyond tracking, this will ensure that non-MessageBody(Reader|Writer) @Providers
    * are created by Spring before any resources by having the resources
    * "depends-on" the @Providers.
    * </p>
    *
    * @param beanFactory bean factory
    */
   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
         throws BeansException
   {
      beanFactory.registerResolvableDependency(Registry.class, getRegistry());
      beanFactory.registerResolvableDependency(ResteasyProviderFactory.class, getProviderFactory());
      if (dispatcher != null)
      {
         beanFactory.registerResolvableDependency(Dispatcher.class, getDispatcher());
      }
      beanFactory.addBeanPostProcessor(new ResteasyBeanPostProcessor(beanFactory));

      List<String> dependsOnBeans = new ArrayList<String>();
      for (String name : beanFactory.getBeanDefinitionNames())
      {
         BeanDefinition beanDef = beanFactory.getBeanDefinition(name);
         if ( (beanDef.getBeanClassName() != null || beanDef.getFactoryBeanName() != null)
                 && !beanDef.isAbstract())
            processBean(beanFactory, dependsOnBeans, name, beanDef);
      }

      dependsOnBeans.addAll(registrations);
      String[] dependsOnArray = dependsOnBeans.toArray(new String[dependsOnBeans.size()]);

      if (dependsOnArray.length > 0)
      {
         for (SpringResourceFactory resourceFactory : resourceFactories.values())
         {
            BeanDefinition beanDef = beanFactory.getBeanDefinition(resourceFactory.getBeanName());
            beanDef.setDependsOn(concat(beanDef.getDependsOn(), dependsOnArray));
         }
      }
   }

   /**
    * Process a single @Provider or a single resource.
    * @param beanFactory bean factory
    * @param dependsOnProviders dependent providers list
    * @param name bean name
    * @param beanDef bean definition
    * @return bean class
    */
   protected Class<?> processBean(final ConfigurableListableBeanFactory beanFactory,
                                  List<String> dependsOnProviders, String name, BeanDefinition beanDef)
   {
      Class<?> beanClass = getBeanClass(name, beanDef, beanFactory);
      if (beanClass.isAnnotationPresent(Provider.class))
      {
         if (!isSingleton(beanDef))
         {
            throw new RuntimeException(Messages.MESSAGES.providerIsNotSingleton(name));
         }

         providerNames.add(name);

         if (!MessageBodyWriter.class.isAssignableFrom(beanClass)
                 && !MessageBodyReader.class.isAssignableFrom(beanClass))
         {
            dependsOnProviders.add(name);
         }
      }
      if (GetRestful.isRootResource(beanClass))
      {
         resourceFactories.put(name, new SpringResourceFactory(name, beanFactory, beanClass));
      }
      if(beanClass == ResteasyRegistration.class)
      {
         registrations.add(name);
      }
      return beanClass;
   }

   public String getPropertyValue(
      MutablePropertyValues registrationPropertyValues, String propertyName)
   {
      if(registrationPropertyValues == null)
      {
         return null;
      }
      PropertyValue propertyValue = registrationPropertyValues.getPropertyValue(propertyName);
      if(propertyValue == null)
      {
         return null;
      }
      Object value = propertyValue.getValue();
      if(value == null)
      {
         return null;
      }
      if(value.getClass() == String.class)
      {
         return (String) value;
      }
      if(value instanceof BeanReference)
      {
         return ((BeanReference)value).getBeanName();
      }
      throw new IllegalStateException(Messages.MESSAGES.resteasyRegistrationReferences());
   }

   /**
    * merge two arrays.
    *
    * @param dependsOn first array
    * @param dependsOnProviders second array
    * @return merged array
    */
   private static String[] concat(String[] dependsOn, String[] dependsOnProviders)
   {
      if (dependsOn == null || dependsOn.length == 0)
      {
         return dependsOnProviders;
      }

      String[] result = new String[dependsOn.length + dependsOnProviders.length];

      System.arraycopy(dependsOn, 0, result, 0, dependsOn.length);
      System.arraycopy(dependsOnProviders, 0, result, dependsOn.length, dependsOnProviders.length);

      return result;
   }

   /**
    * Get the bean class, and take @Configuration @Beans into consideration.
    *
    * @param beanDef {@link BeanDefinition}
    * @param beanFactory bean factory
    * @return bean class
    */
   private static Class<?> getBeanClass(String name, BeanDefinition beanDef,
                              ConfigurableListableBeanFactory beanFactory)
   {
      if (beanDef instanceof RootBeanDefinition)
      {
         RootBeanDefinition rootBeanDef = (RootBeanDefinition) beanDef;
         try
         {
            if (rootBeanDef.getBeanClass() != null)
            {
               return rootBeanDef.getBeanClass();
            }
         }
         catch (IllegalStateException e)
         {
            // do nothing. This gets thrown for factory beans
         }
      }

      // final String factoryBeanName = beanDef.getFactoryBeanName();
      final String factoryMethodName = beanDef.getFactoryMethodName();

      if (beanDef.getBeanClassName() != null && factoryMethodName == null)
      {
         return getBeanClass(beanDef.getBeanClassName());
      }

      if (factoryMethodName != null)
      {
         String factoryClassName = null;

         if (beanDef instanceof AnnotatedBeanDefinition)
         {
            factoryClassName = ((AnnotatedBeanDefinition) beanDef).getMetadata().getClassName();
         }
         else
         {
            // Checks if beanDefinition has a factorybean defined. If so, lookup the classname of that bean
            // definition and use that as the factory class name.
            if (beanDef.getFactoryBeanName() != null)
            {
               factoryClassName = beanFactory.getBeanDefinition(beanDef.getFactoryBeanName()).getBeanClassName();
            }
            else
            {
               factoryClassName = beanDef.getBeanClassName();
            }
         }

         final Class<?> beanClass = getBeanClass(factoryClassName);
         final Method[] methods = ReflectionUtils.getAllDeclaredMethods(beanClass);
         for (Method method : methods) {
            if (method.getName().equals(factoryMethodName)) {
               return method.getReturnType();
            }
         }

         /*
            https://github.com/resteasy/Resteasy/issues/585

            If we haven't found the correct factoryMethod using the previous method,
            fallback to the default FactoryBean getObject method.

            Case in which this tends to happen:

            1. A bean (Bean A) exists which provides factoryMethods for retrieving 1 or more other beans (Bean B, Bean C, ...)
            example: <bean id="processEngine" class="org.activiti.spring.ProcessEngineFactoryBean"/>
            2. Bean B is retrieved by telling Spring that the Factory-Bean is Bean A and that there is a method X to retrieve Bean B.
            example: <bean id="repositoryService" factory-bean="processEngine" factory-method="getRepositoryService"/>
            3. When resteasy has to inject Bean B it tries to lookup method X on Bean A instead of Bean B using the above code.

            As a fix for this, we retrieve the return type for Bean A from the FactoryBean, which later on can be used to retrieve the other beans.

         */
         if (FactoryBean.class.isAssignableFrom(beanClass)) {
            String defaultFactoryMethod = "getObject";
            Class<?> returnType = null;
            for (Method method : methods) {
               if (method.getName().equals(defaultFactoryMethod)) {
                  returnType = method.getReturnType();
                  if (returnType != Object.class) {
                     break;
                  }
               }
            }
            if (returnType != null) {
               return returnType;
            }
         }
      }

      throw new IllegalStateException(Messages.MESSAGES.couldNotFindTypeForBean(name));
   }

   private static boolean isSingleton(BeanDefinition beanDef)
   {
      try
      {
         return beanDef.isSingleton();
      }
      catch (NoSuchBeanDefinitionException nsbde)
      {
         // cannot distinguish between singleton & prototype
         return false;
      }
   }

   private static Class<?> getBeanClass(final String beanClassName)
   {
      try
      {
         return ClassUtils.forName(beanClassName, Thread.currentThread().getContextClassLoader());
      }
      catch (final ClassNotFoundException e)
      {
         throw new IllegalStateException(Messages.MESSAGES.couldNotConvertBeanToClass(beanClassName), e);
      }
   }

   /**
    * Register all of the resources into RESTEasy only when Spring finishes it's
    * life-cycle and the spring singleton bean creation is completed.
    * @param event application event
    */
   @Override
   public void onApplicationEvent(ApplicationEvent event)
   {
      for (SpringResourceFactory resourceFactory : resourceFactories.values())
      {
         getRegistry().removeRegistrations(resourceFactory.getScannableClass());
      }

//  The following code would re-process the bean factory, in case the configuration changed.
//  However, it needs work.
//      if (event.getSource() instanceof XmlWebApplicationContext)
//      {
//         ConfigurableListableBeanFactory beanFactory = ((XmlWebApplicationContext) event.getSource()).getBeanFactory();
//         postProcessBeanFactory(beanFactory);
//      }
      for (SpringResourceFactory resourceFactory : resourceFactories.values())
      {
         getRegistry().addResourceFactory(resourceFactory, resourceFactory.getContext());
      }
   }

   @Override
   public int getOrder()
   {
      return this.order;
   }

   public void setOrder(int order)
   {
      this.order = order;
   }

   @Override
   public boolean supportsEventType(Class<? extends ApplicationEvent> eventType)
   {
      return eventType == ContextRefreshedEvent.class;
   }

   @Override
   public boolean supportsSourceType(Class<?> sourceType)
   {
      return ApplicationContext.class.isAssignableFrom(sourceType);
   }
}
