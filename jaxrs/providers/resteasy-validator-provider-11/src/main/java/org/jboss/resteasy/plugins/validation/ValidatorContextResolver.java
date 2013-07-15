package org.jboss.resteasy.plugins.validation;

import java.lang.ref.WeakReference;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.validation.BootstrapConfiguration;
import javax.validation.Configuration;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.jboss.resteasy.logging.Logger;
import org.jboss.resteasy.spi.validation.GeneralValidator;

/**
 * 
 * @author Leandro Ferro Luzia
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version $Revision: 1.1 $
 *
 * Copyright May 23, 2013
 */
@Provider
public class ValidatorContextResolver implements ContextResolver<GeneralValidator>
{
   private final static Logger logger = Logger.getLogger(ValidatorContextResolver.class);
   private static volatile WeakReference<ValidatorFactory> validatorFactory = new WeakReference<ValidatorFactory>(null);
   final static Object RD_LOCK = new Object();

   // this used to be initialized in a static block, but I was having trouble class loading the context resolver in some
   // environments.  So instead of failing and logging a warning when the resolver is instantiated at deploy time
   // we log any validation warning when trying to obtain the ValidatorFactory. 
   static ValidatorFactory getValidatorFactory()
   {
      ValidatorFactory tmpValidatorFactory = validatorFactory.get();
      if (tmpValidatorFactory == null)
      {
         synchronized (RD_LOCK)
         {
            tmpValidatorFactory = validatorFactory.get();
            if (tmpValidatorFactory == null)
            {
               try
               {
                  Context context = new InitialContext();
                  validatorFactory = new WeakReference<ValidatorFactory>(ValidatorFactory.class.cast(context.lookup("java:comp/ValidatorFactory")));
                  logger.debug("Using CDI supporting " + validatorFactory);
               }
               catch (NamingException e)
               {
                  logger.info("Unable to find CDI supporting ValidatorFactory. Using default ValidatorFactory");
                  HibernateValidatorConfiguration config = Validation.byProvider(HibernateValidator.class).configure();
                  validatorFactory = new WeakReference<ValidatorFactory>(config.buildValidatorFactory());
               }
            }
         }
      }
      return validatorFactory.get();
   }

   @Override
   public GeneralValidator getContext(Class<?> type) {
      try
      {
         Validator validator = getValidatorFactory().getValidator();
         Configuration<?> config = Validation.byDefaultProvider().configure();
         BootstrapConfiguration bootstrapConfiguration = config.getBootstrapConfiguration();
         boolean isExecutableValidationEnabled = bootstrapConfiguration.isExecutableValidationEnabled();
         Set<ExecutableType> defaultValidatedExecutableTypes = bootstrapConfiguration.getDefaultValidatedExecutableTypes();
         return new GeneralValidatorImpl(validator, isExecutableValidationEnabled, defaultValidatedExecutableTypes);
      }
      catch (Exception e)
      {
         logger.warn("Unable to load Validation support", e);
      }
      return null;
   }
}
