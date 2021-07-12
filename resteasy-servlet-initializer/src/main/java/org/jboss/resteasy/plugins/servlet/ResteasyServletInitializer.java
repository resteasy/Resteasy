package org.jboss.resteasy.plugins.servlet;

import org.jboss.resteasy.core.AsynchronousDispatcher;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyContextParameters;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.HandlesTypes;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.Provider;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@HandlesTypes({Application.class, Path.class, Provider.class})
public class ResteasyServletInitializer implements ServletContainerInitializer
{
   private static final String RESTEASY_MAPPING_PREFIX = "resteasy.servlet.mapping.prefix";
   private static final String APPLICATION = "javax.ws.rs.Application";
   static final Set<String> ignoredPackages = new HashSet<String>();
   private static final String DEFAULT_APP_CLAZZ = DefaultApplicationClazz.class.getName();

   static
   {
      ignoredPackages.add(AsynchronousDispatcher.class.getPackage().getName());
   }

   @Override
   public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException
   {
      if (classes == null || classes.size() == 0)
         return;
      Set<Class<?>> appClasses = new HashSet<Class<?>>();
      Set<Class<?>> providers = new HashSet<Class<?>>();
      Set<Class<?>> resources = new HashSet<Class<?>>();

      for (Class<?> clazz : classes)
      {
         if (clazz.isInterface() || ignoredPackages.contains(clazz.getPackage().getName()))
            continue;
         if (clazz.isAnnotationPresent(Path.class))
            resources.add(clazz);
         else if (clazz.isAnnotationPresent(Provider.class))
            providers.add(clazz);
         else if (Application.class.isAssignableFrom(clazz))
            appClasses.add(clazz);
      }
      if (appClasses.size() == 0 && resources.size() == 0) return;

      if (appClasses.size() > 1) {
         for (Class<?> app : appClasses) {
            // App has provided an Application impl.  The local default one
            // isn't needed.  Remove it.
            if (DEFAULT_APP_CLAZZ.equals(app.getName())) {
               appClasses.remove(app);
               break;
            }
         }
      }

      for (Class<?> app : appClasses)
      {
         register(app, providers, resources, servletContext);
      }
   }

   private Set<ServletRegistration> getServletsForApplication(Class<?> applicationClass, ServletContext servletContext)
   {
      Set<ServletRegistration> set = new HashSet<>();
      ServletRegistration reg = servletContext.getServletRegistration(applicationClass.getName());
      if (reg != null && reg.getMappings().size() == 1)
         set.add(reg);

      for (ServletRegistration sr : servletContext.getServletRegistrations().values())
      {
         String appClassName = sr.getInitParameter(APPLICATION);
         if (applicationClass.getName().equals(appClassName) && sr.getMappings().size() == 1)
            set.add(sr);
      }
      return set;
   }

   protected void register(Class<?> applicationClass, Set<Class<?>> providers, Set<Class<?>> resources, ServletContext servletContext)
   {
      Set<ServletRegistration> servletsForApp = getServletsForApplication(applicationClass, servletContext);
      // ignore @ApplicationPath if application is already mapped in web.xml
      if (!servletsForApp.isEmpty())
      {
         for (ServletRegistration servletReg : servletsForApp)
         {
            String servletClassName = servletReg.getClassName();
            if (servletClassName == null)
               servletContext.addServlet(servletReg.getName(), HttpServlet30Dispatcher.class);
            String prefix = servletReg.getMappings().iterator().next();
            if (prefix.endsWith("*"))
               prefix = prefix.substring(0, prefix.length() - 1);
            if (prefix.length() > 1 && prefix.endsWith("/"))
               prefix = prefix.substring(0, prefix.length() - 1);
            registerResourcesAndProviders(servletReg, providers, resources, applicationClass, prefix);
         }
         return;
      }
      ApplicationPath path = applicationClass.getAnnotation(ApplicationPath.class);
      if (path == null)
      {
         // Application subclass has no @ApplicationPath and no declared mappings to use
         // TODO: add debug message indicating that an Application was detected but could
         // not be mapped
         return;
      }
      ServletRegistration.Dynamic reg;
      String mapping = path.value();
      String prefix;

      if (!mapping.startsWith("/"))
         mapping = "/" + mapping;
      prefix = mapping;
      if (!prefix.equals("/") && prefix.endsWith("/"))
         prefix = prefix.substring(0, prefix.length() - 1);
      if (!mapping.endsWith("/*"))
      {
         if (mapping.endsWith("/"))
            mapping += "*";
         else
            mapping += "/*";
      }

      reg = servletContext.addServlet(applicationClass.getName(), HttpServlet30Dispatcher.class);
      reg.setLoadOnStartup(1);
      reg.setAsyncSupported(true);
      reg.addMapping(mapping);

      registerResourcesAndProviders(reg, providers, resources, applicationClass, prefix);
   }

   private void registerResourcesAndProviders(ServletRegistration reg, Set<Class<?>> providers, Set<Class<?>> resources,
         Class<?> appClass, String prefix)
   {
      reg.setInitParameter(APPLICATION, appClass.getName());
      // resteasy.servlet.mapping.prefix
      reg.setInitParameter(RESTEASY_MAPPING_PREFIX, prefix);
      if (resources.size() > 0)
      {
         StringBuilder builder = new StringBuilder();
         boolean first = true;
         for (Class<?> resource : resources)
         {
            if (first)
               first = false;
            else
               builder.append(",");
            builder.append(resource.getName());
         }
         reg.setInitParameter(ResteasyContextParameters.RESTEASY_SCANNED_RESOURCES, builder.toString());
      }
      if (providers.size() > 0)
      {
         StringBuilder builder = new StringBuilder();
         boolean first = true;
         for (Class<?> provider : providers)
         {
            if (first)
               first = false;
            else
               builder.append(",");
            builder.append(provider.getName());
         }
         reg.setInitParameter(ResteasyContextParameters.RESTEASY_SCANNED_PROVIDERS, builder.toString());
      }
   }
}
