package org.jboss.resteasy.plugins.server.servlet;

import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;

import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.NewCookie;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@WebFilter(asyncSupported = true, value="")
public class Filter30Dispatcher extends FilterDispatcher
{
   @Override
   public HttpRequest createResteasyHttpRequest(String httpMethod, HttpServletRequest httpServletRequest, ResteasyHttpHeaders httpHeaders, ResteasyUriInfo uriInfo, HttpResponse httpResponse, HttpServletResponse httpServletResponse)
   {
      Servlet3AsyncHttpRequest request = new Servlet3AsyncHttpRequest(httpServletRequest, httpServletResponse, servletContext, httpResponse, httpHeaders, uriInfo, httpMethod, (SynchronousDispatcher) getDispatcher());
      return request;
   }

   @Override
   public HttpResponse createResteasyHttpResponse(HttpServletResponse response, HttpServletRequest request)
   {
      return new HttpServletResponseWrapper(response, request, getDispatcher().getProviderFactory()) {
         @Override
         public void addNewCookie(NewCookie cookie)
         {
            outputHeaders.add(javax.ws.rs.core.HttpHeaders.SET_COOKIE, cookie);
         }
      };
   }




}
