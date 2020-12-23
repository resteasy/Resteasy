package org.jboss.resteasy.test.client.exception.resource;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("app")
public class ClientWebApplicationExceptionMicroProfileProxyApplication extends Application {

   @Override
   public Set<Class<?>> getClasses() {
      HashSet<Class<?>> classes = new HashSet<Class<?>>();
      classes.add(ClientWebApplicationExceptionMicroProfileProxyResource.class);
      return classes;
   }
}