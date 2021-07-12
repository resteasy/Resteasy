package org.jboss.resteasy.plugins.spring;

//org.jboss.logging.annotations.Message.Format.MESSAGE_FORMATimport org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author <a href="mailto:sduskis@gmail.com">Solomn Duskis</a>
 * @version $Revision: 1 $
 */

public class ResteasyRegistration
{

   private String context = "";
   private String beanName;

   public ResteasyRegistration()
   {
   }

   public ResteasyRegistration(final String context, final String beanName)
   {
      this.context = context;
      this.beanName = beanName;
   }

   @Autowired
   public String getContext()
   {
      return context;
   }

   public void setContext(String context)
   {
      this.context = context;
   }

   @Autowired
   public String getBeanName()
   {
      return this.beanName;
   }

   public void setBeanName(String beanName)
   {
      this.beanName = beanName;
   }

}
