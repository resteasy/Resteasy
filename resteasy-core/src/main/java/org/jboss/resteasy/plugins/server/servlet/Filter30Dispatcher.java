package org.jboss.resteasy.plugins.server.servlet;

import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;

import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.NewCookie;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@WebFilter(asyncSupported = true, value="")
public class Filter30Dispatcher extends FilterDispatcher
{
   ScheduledExecutorService asyncCancelScheduler = Executors.newScheduledThreadPool(0); // this is to get around TCK tests that call setTimeout in a separate thread which is illegal.
   @Override
   public HttpRequest createResteasyHttpRequest(String httpMethod, HttpServletRequest httpServletRequest, ResteasyHttpHeaders httpHeaders, ResteasyUriInfo uriInfo, HttpResponse httpResponse, HttpServletResponse httpServletResponse)
   {
      Servlet3AsyncHttpRequest request = new Servlet3AsyncHttpRequest(httpServletRequest, httpServletResponse, servletContext, httpResponse, httpHeaders, uriInfo, httpMethod, (SynchronousDispatcher) getDispatcher());
      request.asyncScheduler = asyncCancelScheduler;
      return request;
   }

   @Override
   public HttpResponse createResteasyHttpResponse(HttpServletResponse response, HttpServletRequest request)
   {
      return new HttpServletResponseWrapper(response, request, getDispatcher().getProviderFactory()) {
         @Override
         public void addNewCookie(NewCookie cookie)
         {
            outputHeaders.add(jakarta.ws.rs.core.HttpHeaders.SET_COOKIE, cookie);
         }
      };
   }




}
