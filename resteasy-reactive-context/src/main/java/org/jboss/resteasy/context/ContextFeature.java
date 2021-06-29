package org.jboss.resteasy.context;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

import io.smallrye.context.impl.SmallRyeThreadContextStorageDeclaration;
import org.eclipse.microprofile.context.spi.ContextManagerProvider;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.Dispatcher;

@Provider
public class ContextFeature implements Feature
{
   /* smallrye context 1.2.0+ requires an instance of this to
   declare that you will need a custom ThreadLocal.
 */
   private final SmallRyeThreadContextStorageDeclaration storageDecl =
           new SmallRyeThreadContextStorageDeclaration();

   @Override
   public boolean configure(FeatureContext context)
   {
      // this is tied to the deployment, which is what we want for the reactive context
      if(context.getConfiguration().getRuntimeType() == RuntimeType.CLIENT)
         return false;
      Dispatcher dispatcher = ResteasyContext.getContextData(Dispatcher.class);
      if(dispatcher == null) {
         // this can happen, but it means we're not able to find a deployment
         return false;
      }
      // Make sure we have context propagation for this class loader
      ContextManagerProvider.instance().getContextManager();
      return true;
   }

}
