package org.jboss.resteasy.client.jaxrs.internal.proxy.processors.webtarget;

import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.client.WebTarget;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class QueryParamProcessor extends AbstractWebTargetCollectionProcessor
{
   private final boolean isEncoded;

   public QueryParamProcessor(final String paramName, final Type type, final Annotation[] annotations, final ClientConfiguration config, final boolean isEncoded)
   {
      super(paramName, type, annotations, config);

      this.isEncoded = isEncoded;
   }

   @Override
   protected WebTarget apply(WebTarget target, Object... objects)
   {
      ResteasyWebTarget t = (ResteasyWebTarget)target;
      return t.queryParamNoTemplate(paramName, isEncoded, objects);
   }
}
