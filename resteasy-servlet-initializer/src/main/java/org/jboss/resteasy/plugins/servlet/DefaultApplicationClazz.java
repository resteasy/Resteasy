package org.jboss.resteasy.plugins.servlet;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

/**
 * Provide a placeholder Application impl in case one is not
 * provided by the app.
 */
@ApplicationPath("/")
public class DefaultApplicationClazz extends Application
{ }
