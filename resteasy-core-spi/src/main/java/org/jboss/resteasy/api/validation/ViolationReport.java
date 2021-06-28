package org.jboss.resteasy.api.validation;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.jboss.resteasy.spi.ResteasyConfiguration;
import org.jboss.resteasy.spi.validation.ConstraintTypeUtil;

/**
*
* @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
* @version $Revision: 1.1 $
*
* Copyright July 27, 2013
*/
@XmlRootElement(name="violationReport")
@XmlAccessorType(XmlAccessType.FIELD)
public class ViolationReport
{
   private String exception;

   private List<ResteasyConstraintViolation> propertyViolations = new ArrayList<>();
   private List<ResteasyConstraintViolation> classViolations = new ArrayList<>();
   private List<ResteasyConstraintViolation> parameterViolations = new ArrayList<>();
   private List<ResteasyConstraintViolation> returnValueViolations = new ArrayList<>();

   public ViolationReport(final ResteasyViolationException exception)
   {
      Exception e = exception.getException();
      if (e != null)
      {
         this.exception = e.toString();
      }
      this.propertyViolations =  exception.getPropertyViolations();
      this.classViolations =  exception.getClassViolations();
      this.parameterViolations =  exception.getParameterViolations();
      this.returnValueViolations =  exception.getReturnValueViolations();
   }

   public ViolationReport(final String s)
   {
      this(new ResteasyViolationException(s)
      {
         private static final long serialVersionUID = 1L;

         @Override
         public ConstraintTypeUtil getConstraintTypeUtil()
         {
            return null;
         }

         @Override
         protected ResteasyConfiguration getResteasyConfiguration()
         {
            return null;
         }
      });
   }

   public ViolationReport()
   {
   }

   public String getException()
   {
      return exception;
   }

   public List<ResteasyConstraintViolation> getPropertyViolations()
   {
      return propertyViolations;
   }

   public List<ResteasyConstraintViolation> getClassViolations()
   {
      return classViolations;
   }

   public List<ResteasyConstraintViolation> getParameterViolations()
   {
      return parameterViolations;
   }

   public List<ResteasyConstraintViolation> getReturnValueViolations()
   {
      return returnValueViolations;
   }

   public void setException(String exception)
   {
      this.exception = exception;
   }

   public void setPropertyViolations(ArrayList<ResteasyConstraintViolation> propertyViolations)
   {
      this.propertyViolations = propertyViolations;
   }

   public void setClassViolations(ArrayList<ResteasyConstraintViolation> classViolations)
   {
      this.classViolations = classViolations;
   }

   public void setParameterViolations(ArrayList<ResteasyConstraintViolation> parameterViolations)
   {
      this.parameterViolations = parameterViolations;
   }

   public void setReturnValueViolations(ArrayList<ResteasyConstraintViolation> returnValueViolations)
   {
      this.returnValueViolations = returnValueViolations;
   }
}
