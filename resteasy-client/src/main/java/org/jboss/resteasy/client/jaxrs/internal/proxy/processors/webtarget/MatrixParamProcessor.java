package org.jboss.resteasy.client.jaxrs.internal.proxy.processors.webtarget;

import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.client.WebTarget;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class MatrixParamProcessor extends AbstractWebTargetCollectionProcessor
{
   public MatrixParamProcessor(final String paramName, final Type type, final Annotation[] annotations, final ClientConfiguration config)
   {
      super(paramName, type, annotations, config);
   }

   @Override
   protected WebTarget apply(WebTarget target, Object... objects)
   {
      return target.matrixParam(paramName, objects);
   }

}
