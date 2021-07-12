package org.jboss.resteasy.client.jaxrs.internal.proxy.processors.webtarget;

import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.proxy.processors.AbstractCollectionProcessor;
import org.jboss.resteasy.client.jaxrs.internal.proxy.processors.WebTargetProcessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.client.WebTarget;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class AbstractWebTargetCollectionProcessor extends AbstractCollectionProcessor<WebTarget> implements WebTargetProcessor
{
   public AbstractWebTargetCollectionProcessor(final String paramName, final Type type, final Annotation[] annotations, final ClientConfiguration config)
   {
      super(paramName, type, annotations, config);
   }

   @Override
   public WebTarget build(WebTarget target, Object param)
   {
      return buildIt(target, param);
   }
}
