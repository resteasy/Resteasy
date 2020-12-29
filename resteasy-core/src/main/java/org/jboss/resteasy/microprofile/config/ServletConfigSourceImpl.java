package org.jboss.resteasy.microprofile.config;

import org.jboss.resteasy.core.ResteasyContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServletConfigSourceImpl implements ResteasyConfigSource {
   private volatile String name;

   @Override
   public Map<String, String> getProperties() {
      ServletConfig config = ResteasyContext.getContextData(ServletConfig.class);
      if (config == null) {
         return Collections.<String, String>emptyMap();
      }
      Map<String, String> map = new HashMap<String, String>();
      Enumeration<String> keys = config.getInitParameterNames();
      if (keys != null) {
         while (keys.hasMoreElements())
         {
            String key = keys.nextElement();
            map.put(key, config.getInitParameter(key));
         }
      }
      return map;
   }

   @Override
   public Set<String> getPropertyNames() {
      ServletConfig config = ResteasyContext.getContextData(ServletConfig.class);
      if (config == null) {
         return Collections.<String>emptySet();
      }

      return new HashSet<String>(Collections.list(config.getInitParameterNames()));
   }

   @Override
   public String getValue(String propertyName) {
      ServletConfig config = ResteasyContext.getContextData(ServletConfig.class);
      if (config == null) {
         return null;
      }
      return config.getInitParameter(propertyName);
   }

   @Override
   public String getName() {
      String currentName = name;
      if (currentName == null) {
         synchronized(this) {
            currentName = name;
            if (currentName == null) {
               ServletContext servletContext = ResteasyContext.getContextData(ServletContext.class);
               ServletConfig servletConfig = ResteasyContext.getContextData(ServletConfig.class);
               StringBuilder sb = new StringBuilder();
               currentName = sb.append(servletContext != null ? servletContext.getServletContextName() : null).append(":")
                     .append(servletConfig != null ? servletConfig.getServletName() : null)
                     .append(":ServletConfigSource").toString();
               this.name = currentName;
            }
         }
      }
      return currentName;
   }

   @Override
   public int getDefaultOrdinal() {
      return ServletConfigSource.BUILT_IN_DEFAULT_ORDINAL;
   }

}
