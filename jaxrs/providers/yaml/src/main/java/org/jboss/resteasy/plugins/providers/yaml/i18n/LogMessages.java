package org.jboss.resteasy.plugins.providers.yaml.i18n;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.Message.Format;
import org.jboss.logging.annotations.MessageLogger;

/**
 * 
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version $Revision: 1.1 $
 *
 * Copyright Aug 25, 2015
 */
@MessageLogger(projectCode = "RESTEASY")
public interface LogMessages extends BasicLogger
{
   LogMessages LOGGER = Logger.getMessageLogger(LogMessages.class, LogMessages.class.getPackage().getName());
}
