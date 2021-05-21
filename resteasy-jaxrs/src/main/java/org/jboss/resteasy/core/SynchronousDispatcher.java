package org.jboss.resteasy.core;

import org.jboss.resteasy.core.interception.PreMatchContainerRequestContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Providers;

import org.jboss.resteasy.plugins.server.servlet.Cleanable;
import org.jboss.resteasy.plugins.server.servlet.Cleanables;
import org.jboss.resteasy.resteasy_jaxrs.i18n.LogMessages;
import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;
import org.jboss.resteasy.specimpl.BuiltResponse;
import org.jboss.resteasy.specimpl.RequestImpl;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpRequestPreprocessor;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.InternalDispatcher;
import org.jboss.resteasy.spi.InternalServerErrorException;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyAsynchronousContext;
import org.jboss.resteasy.spi.ResteasyConfiguration;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.UnhandledException;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@SuppressWarnings("unchecked")
public class SynchronousDispatcher implements Dispatcher
{
   protected ResteasyProviderFactory providerFactory;
   protected Registry registry;
   protected List<HttpRequestPreprocessor> requestPreprocessors = new ArrayList<HttpRequestPreprocessor>();
   protected Map<Class, Object> defaultContextObjects = new HashMap<Class, Object>();
   protected Set<String> unwrappedExceptions = new HashSet<String>();
   protected boolean bufferExceptionEntityRead = false;
   protected boolean bufferExceptionEntity = true;

   public SynchronousDispatcher(final ResteasyProviderFactory providerFactory)
   {
      this.providerFactory = providerFactory;
      this.registry = new ResourceMethodRegistry(providerFactory);
      defaultContextObjects.put(Providers.class, providerFactory);
      defaultContextObjects.put(Registry.class, registry);
      defaultContextObjects.put(Dispatcher.class, this);
      defaultContextObjects.put(InternalDispatcher.class, InternalDispatcher.getInstance());
   }

   public SynchronousDispatcher(final ResteasyProviderFactory providerFactory, final ResourceMethodRegistry registry)
   {
      this(providerFactory);
      this.registry = registry;
      defaultContextObjects.put(Registry.class, registry);
   }

   public ResteasyProviderFactory getProviderFactory()
   {
      return providerFactory;
   }

   public Registry getRegistry()
   {
      return registry;
   }

   public Map<Class, Object> getDefaultContextObjects()
   {
      return defaultContextObjects;
   }

   public Set<String> getUnwrappedExceptions()
   {
      return unwrappedExceptions;
   }

   public Response preprocess(HttpRequest request)
   {
      Response aborted = null;
      try
      {
         for (HttpRequestPreprocessor preprocessor : this.requestPreprocessors)
         {
            preprocessor.preProcess(request);
         }
         ContainerRequestFilter[] requestFilters = providerFactory.getContainerRequestFilterRegistry().preMatch();
         // FIXME: support async
         PreMatchContainerRequestContext requestContext = new PreMatchContainerRequestContext(request, requestFilters, null);
         aborted = requestContext.filter();
      }
      catch (Exception e)
      {
         //logger.error("Failed in preprocess, mapping exception", e);
         aborted = new ExceptionHandler(providerFactory, unwrappedExceptions).handleException(request, e);
      }
      return aborted;
   }

   /**
    * Call pre-process ContainerRequestFilters.
    *
    * @param request http request
    * @param response http response
    * @param continuation runnable
    */
   protected void preprocess(HttpRequest request, HttpResponse response, Runnable continuation)
   {
      Response aborted = null;
      PreMatchContainerRequestContext requestContext = null;
      try
      {
         for (HttpRequestPreprocessor preprocessor : this.requestPreprocessors)
         {
            preprocessor.preProcess(request);
         }
         ContainerRequestFilter[] requestFilters = providerFactory.getContainerRequestFilterRegistry().preMatch();
         requestContext = new PreMatchContainerRequestContext(request, requestFilters,
            () -> {
               continuation.run();
               return null;
            });
         aborted = requestContext.filter();
      }
      catch (Exception e)
      {
         //logger.error("Failed in preprocess, mapping exception", e);
         // we only want to catch exceptions happening in the filters, not in the continuation
         if(requestContext == null || !requestContext.startedContinuation())
         {
            writeException(request, response, e, t -> {});
            return;
         }
         else
         {
            rethrow(e);
         }
      }
      if (aborted != null)
      {
         writeResponse(request, response, aborted);
         return;
      }
   }

   public static <T extends Throwable> void rethrow(Throwable t) throws T
   {
      throw (T)t;
   }

   @Deprecated
   public void writeException(HttpRequest request, HttpResponse response, Throwable e)
   {
      writeException(request, response, e, t -> {});
   }

   public void writeException(HttpRequest request, HttpResponse response, Throwable e, Consumer<Throwable> onComplete)
   {
      if (!bufferExceptionEntityRead)
      {
         bufferExceptionEntityRead = true;
         ResteasyConfiguration context = ResteasyProviderFactory.getContextData(ResteasyConfiguration.class);
         if (context != null)
         {
            String s = context.getParameter("resteasy.buffer.exception.entity");
            if (s != null)
            {
               bufferExceptionEntity = Boolean.parseBoolean(s);
            }
         }
      }
      if (response.isCommitted())
      {
         LogMessages.LOGGER.debug(Messages.MESSAGES.responseIsCommitted());
         onComplete.accept(null);
         return;
      }
      // FIXME: losing X-Request-ID because ThreadLocal that keeps it in the application will not be there anymore when
      // the ExceptionHandler below is called...
      MultivaluedMap<String, Object> originalResponseHeaders = response.getOutputHeaders();
      // cleans possibly partially written response
      response.reset();
      // FIXME: ... but maybe constructing the headers anew is actually the ExceptionHandler's job, since this is
      // duplicating the Content-Type in the Map (no impact in the received Response in the browser though).
      response.getOutputHeaders().putAll(originalResponseHeaders);
      Response handledResponse = new ExceptionHandler(providerFactory, unwrappedExceptions).handleException(request, e);
      if (handledResponse == null) throw new UnhandledException(e);
      if (!bufferExceptionEntity)
      {
         response.getOutputHeaders().add("resteasy.buffer.exception.entity", "false");
      }
      try
      {
         ServerResponseWriter.writeNomapResponse(((BuiltResponse) handledResponse), request, response, providerFactory, onComplete);
      }
      catch (Exception e1)
      {
         throw new UnhandledException(e1);
      }
   }


   public void invoke(HttpRequest request, HttpResponse response)
   {
      try
      {
         pushContextObjects(request, response);
         preprocess(request, response, () -> {
            ResourceInvoker invoker = null;
            try
            {
               try
               {
                  invoker = getInvoker(request);
               }
               catch (Exception exception)
               {
                  //logger.error("getInvoker() failed mapping exception", exception);
                  writeException(request, response, exception, t -> {});
                  return;
               }
               invoke(request, response, invoker);
            }
            finally
            {
               // we're probably clearing it twice but still required
               clearContextData();
            }
         });
      }
      finally
      {
         clearContextData();
      }
   }

   /**
    * Propagate NotFoundException.  This is used for Filters.
    *
    * @param request http request
    * @param response http response
    */
   public void invokePropagateNotFound(HttpRequest request, HttpResponse response) throws NotFoundException
   {
      try
      {
         pushContextObjects(request, response);
         preprocess(request, response, () -> {
            ResourceInvoker invoker = null;
            try
            {
               try
               {
                  invoker = getInvoker(request);
               }
               catch (Exception failure)
               {
                  if (failure instanceof NotFoundException)
                  {
                     throw ((NotFoundException) failure);
                  }
                  else
                  {
                     //logger.error("getInvoker() failed mapping exception", failure);
                     writeException(request, response, failure, t->{});
                     return;
                  }
               }
               invoke(request, response, invoker);
            }
            finally
            {
               // we're probably clearing it twice but still required
               clearContextData();
            }
         });
      }
      finally
      {
         clearContextData();
      }

   }

   public ResourceInvoker getInvoker(HttpRequest request)
         throws Failure
   {
      LogMessages.LOGGER.pathInfo(request.getUri().getPath());
      if (!request.isInitial())
      {
         throw new InternalServerErrorException(Messages.MESSAGES.isNotInitialRequest(request.getUri().getPath()));
      }
      ResourceInvoker invoker = registry.getResourceInvoker(request);
      if (invoker == null)
      {
         throw new NotFoundException(Messages.MESSAGES.unableToFindJaxRsResource(request.getUri().getPath()));
      }
      return invoker;
   }

   public void pushContextObjects(final HttpRequest request, final HttpResponse response)
   {
      Map contextDataMap = ResteasyProviderFactory.getContextDataMap();
      contextDataMap.put(HttpRequest.class, request);
      contextDataMap.put(HttpResponse.class, response);
      contextDataMap.put(HttpHeaders.class, request.getHttpHeaders());
      contextDataMap.put(UriInfo.class, request.getUri());
      contextDataMap.put(Request.class, new RequestImpl(request, response));
      contextDataMap.put(ResteasyAsynchronousContext.class, request.getAsyncContext());
      ResourceContext resourceContext = new ResourceContext()
      {
         @Override
         public <T> T getResource(Class<T> resourceClass)
         {
            return providerFactory.injectedInstance(resourceClass, request, response);
         }

         @Override
         public <T> T initResource(T resource)
         {
            providerFactory.injectProperties(resource, request, response);
            return resource;
         }
      };
      contextDataMap.put(ResourceContext.class, resourceContext);

      contextDataMap.putAll(defaultContextObjects);
      contextDataMap.put(Cleanables.class, new Cleanables());
      contextDataMap.put(PostResourceMethodInvokers.class, new PostResourceMethodInvokers());
   }

   public Response internalInvocation(HttpRequest request, HttpResponse response, Object entity)
   {
      // be extra careful in the clean up process. Only pop if there was an
      // equivalent push.
      ResteasyProviderFactory.addContextDataLevel();
      boolean pushedBody = false;
      try
      {
         MessageBodyParameterInjector.pushBody(entity);
         pushedBody = true;
         ResourceInvoker invoker = getInvoker(request);
         if (invoker != null)
         {
            pushContextObjects(request, response);
            return execute(request, response, invoker);
         }

         // this should never happen, since getInvoker should throw an exception
         // if invoker is null
         return null;
      }
      finally
      {
         ResteasyProviderFactory.removeContextDataLevel();
         if (pushedBody)
         {
            MessageBodyParameterInjector.popBody();
         }
      }
   }

   public void clearContextData()
   {
      Map<Class<?>, Object> map = ResteasyProviderFactory.getContextDataMap(false);
      Cleanables cleanables = map != null ? (Cleanables) map.get(Cleanables.class) : null;
      if (cleanables != null)
      {
         for (Iterator<Cleanable> it = cleanables.getCleanables().iterator(); it.hasNext(); )
         {
            try
            {
               it.next().clean();
            }
            catch(Exception e)
            {
               // Empty
            }
         }
         ResteasyProviderFactory.clearContextData();
      }
      // just in case there were internalDispatches that need to be cleaned up
      MessageBodyParameterInjector.clearBodies();
   }

   /**
    * Return a response wither from an invoke or exception handling.
    *
    * @param request http request
    * @param response http response
    * @param invoker resource invoker
    * @return response
    */
   public Response execute(HttpRequest request, HttpResponse response, ResourceInvoker invoker)
   {
      Response jaxrsResponse = null;
      try
      {
         jaxrsResponse = invoker.invoke(request, response);
         if (request.getAsyncContext().isSuspended())
         {
            /**
             * Callback by the initial calling thread.  This callback will probably do nothing in an asynchronous environment
             * but will be used to simulate AsynchronousResponse in vanilla Servlet containers that do not support
             * asychronous HTTP.
             *
             */
            request.getAsyncContext().getAsyncResponse().initialRequestThreadFinished();
            jaxrsResponse = null; // we're handing response asynchronously
         }
      }
      catch (Exception e)
      {
         //logger.error("invoke() failed mapping exception", e);
         jaxrsResponse = new ExceptionHandler(providerFactory, unwrappedExceptions).handleException(request, e);
         if (jaxrsResponse == null) throw new UnhandledException(e);
      }
      return jaxrsResponse;
   }

   /**
    * Invoke and write response.
    *
    * @param request http request
    * @param response http response
    * @param invoker resource invoker
    */
   public void invoke(HttpRequest request, HttpResponse response, ResourceInvoker invoker)
   {
      Response jaxrsResponse = null;
      try
      {
         jaxrsResponse = invoker.invoke(request, response);
         if (request.getAsyncContext().isSuspended())
         {
            /**
             * Callback by the initial calling thread.  This callback will probably do nothing in an asynchronous environment
             * but will be used to simulate AsynchronousResponse in vanilla Servlet containers that do not support
             * asychronous HTTP.
             *
             */
            request.getAsyncContext().getAsyncResponse().initialRequestThreadFinished();
            jaxrsResponse = null; // we're handing response asynchronously
         }
      }
      catch (Exception e)
      {
         //logger.error("invoke() failed mapping exception", e);
         invoker.getMethodStatisticsLogger().incFailureCnt();
         writeException(request, response, e, t->{});
         return;
      }

      if (jaxrsResponse != null) writeResponse(request, response, jaxrsResponse);
   }

   @Deprecated
   public void asynchronousDelivery(HttpRequest request, HttpResponse response, Response jaxrsResponse) throws IOException
   {
      asynchronousDelivery(request, response, jaxrsResponse, t -> {});
   }

   public void asynchronousDelivery(HttpRequest request, HttpResponse response, Response jaxrsResponse, Consumer<Throwable> onComplete) throws IOException
   {
      if (jaxrsResponse == null) return;
      try
      {
         pushContextObjects(request, response);
         ServerResponseWriter.writeNomapResponse((BuiltResponse) jaxrsResponse, request, response, providerFactory, onComplete);
      }
      finally
      {
         ResteasyProviderFactory.removeContextDataLevel();
      }
   }

   public void unhandledAsynchronousException(HttpResponse response, Throwable ex) {
      LogMessages.LOGGER.unhandledAsynchronousException(ex);
      // unhandled exceptions need to be processed as they can't be thrown back to the servlet container
      if (!response.isCommitted()) {
         try
         {
            response.reset();
            response.sendError(500);
         }
         catch (IOException e)
         {

         }
      }
   }

   @Deprecated
   public void asynchronousExceptionDelivery(HttpRequest request, HttpResponse response, Throwable exception)
   {
      asynchronousExceptionDelivery(request, response, exception, t -> {});
   }

   public void asynchronousExceptionDelivery(HttpRequest request, HttpResponse response, Throwable exception, Consumer<Throwable> onComplete)
   {
      try
      {
         pushContextObjects(request, response);
         writeException(request, response, exception, t -> {
            if(t != null)
               unhandledAsynchronousException(response, t);
            onComplete.accept(null);
            ResteasyProviderFactory.removeContextDataLevel();
         });
      }
      catch (Throwable ex)
      {
         unhandledAsynchronousException(response, ex);
         onComplete.accept(ex);
      }
   }


   protected void writeResponse(HttpRequest request, HttpResponse response, Response jaxrsResponse)
   {
      try
      {
         ServerResponseWriter.writeNomapResponse((BuiltResponse) jaxrsResponse, request, response, providerFactory,
            t -> {
               if(t != null)
                  writeException(request, response, t, t2 -> {});
            });
      }
      catch (Exception e)
      {
         //logger.error("writeResponse() failed mapping exception", e);
         writeException(request, response, e, t -> {});
      }
   }

   public void addHttpPreprocessor(HttpRequestPreprocessor httpPreprocessor)
   {
      requestPreprocessors.add(httpPreprocessor);
   }

}
