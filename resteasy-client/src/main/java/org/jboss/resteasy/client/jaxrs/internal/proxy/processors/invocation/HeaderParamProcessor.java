package org.jboss.resteasy.client.jaxrs.internal.proxy.processors.invocation;

import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocationBuilder;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class HeaderParamProcessor extends AbstractInvocationCollectionProcessor
{

   public HeaderParamProcessor(final String paramName)
   {
      super(paramName);
   }

   @Override
   public void process(ClientInvocationBuilder invocation, Object param) {
      // no-op
   }
   @Override
   protected ClientInvocationBuilder apply(ClientInvocationBuilder target, Object object)
   {
      return apply(target, new Object[]{object});
   }

   @Override
   protected ClientInvocationBuilder apply(ClientInvocationBuilder target, Object[] objects)
   {
      for (Object object : objects) {
         target = (ClientInvocationBuilder)target.header(paramName, object);
      }
      return target;
   }

   @Override
   protected ClientInvocation apply(ClientInvocation target, Object object)
   {
      return apply(target, new Object[]{object});
   }

   @Override
   protected ClientInvocation apply(ClientInvocation invocation, Object... objects)
   {
      for (Object object : objects) {
         String value = invocation.getClientConfiguration().toString(object);
         invocation.getHeaders().header(paramName, value);
      }
      return invocation;
   }

}
