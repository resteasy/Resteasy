package org.jboss.resteasy.client.jaxrs;

import java.util.concurrent.CompletionStage;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

/**
 * Extension of javax.ws.rs.client.RxInvoker with methods that return CompletionStage<Response>
 */
public interface RxInvokerExt
{
   /**
    * Invoke HTTP GET method for the current request.
    *
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> getResponse(Class<?> responseType);

   /**
    * Invoke HTTP GET method for the current request.
    *
    * @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> getResponse(GenericType<?> genericType);

   /**
    * Invoke HTTP PUT method for the current request.
    *
    * @param entity       request entity, including its full {@link javax.ws.rs.core.Variant} information.
    *                     Any variant-related HTTP headers previously set (namely {@code Content-Type},
    *                     {@code Content-Language} and {@code Content-Encoding}) will be overwritten using
    *                     the entity variant information.
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> putResponse(Entity<?> entity, Class<?> responseType);

   /**
    * Invoke HTTP PUT method for the current request.
    *
    * @param entity       request entity, including its full {@link javax.ws.rs.core.Variant} information.
    *                     Any variant-related HTTP headers previously set (namely {@code Content-Type},
    *                     {@code Content-Language} and {@code Content-Encoding}) will be overwritten using
    *                     the entity variant information.
    * @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> putResponse(Entity<?> entity, GenericType<?> genericType);

   /**
    * Invoke HTTP POST method for the current request.
    *
    * @param entity       request entity, including its full {@link javax.ws.rs.core.Variant} information.
    *                     Any variant-related HTTP headers previously set (namely {@code Content-Type},
    *                     {@code Content-Language} and {@code Content-Encoding}) will be overwritten using
    *                     the entity variant information.
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> postResponse(Entity<?> entity, Class<?> responseType);

   /**
    * Invoke HTTP POST method for the current request.
    *
    * @param entity       request entity, including its full {@link javax.ws.rs.core.Variant} information.
    *                     Any variant-related HTTP headers previously set (namely {@code Content-Type},
    *                     {@code Content-Language} and {@code Content-Encoding}) will be overwritten using
    *                     the entity variant information.
    * @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> postResponse(Entity<?> entity, GenericType<?> genericType);

   /**
    * Invoke HTTP DELETE method for the current request.
    *
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> deleteResponse(Class<?> responseType);

   /**
    * Invoke HTTP DELETE method for the current request.
    *
    * @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> deleteResponse(GenericType<?> genericType);

   /**
    * Invoke HTTP HEAD method for the current request.
    *
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    */
   CompletionStage<Response> headResponse();

   /**
    * Invoke HTTP OPTIONS method for the current request.
    *
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> optionsResponse(Class<?> responseType);

   /**
    * Invoke HTTP OPTIONS method for the current request.
    *
    * @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> optionsResponse(GenericType<?> genericType);

   /**
    * Invoke HTTP TRACE method for the current request.
    *
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> traceResponse(Class<?> responseType);

   /**
    * Invoke HTTP TRACE method for the current request.
    *
    * @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type.
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> traceResponse(GenericType<?> genericType);

   /**
    * Invoke an arbitrary method for the current request.
    *
    * @param name         method name.
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type..
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> methodResponse(String name, Class<?> responseType);

   /**
    * Invoke an arbitrary method for the current request.
    *
    * @param name         method name.
    * @param @param genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type..
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> methodResponse(String name, GenericType<?> genericType);

   /**
    * Invoke an arbitrary method for the current request.
    *
    * @param name         method name.
    * @param entity       request entity, including its full {@link javax.ws.rs.core.Variant} information.
    *                     Any variant-related HTTP headers previously set (namely {@code Content-Type},
    *                     {@code Content-Language} and {@code Content-Encoding}) will be overwritten using
    *                     the entity variant information.
    * @param responseType Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type..
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> methodResponse(String name, Entity<?> entity, Class<?> responseType);

   /**
    * Invoke an arbitrary method for the current request.
    *
    * @param name         method name.
    * @param entity       request entity, including its full {@link javax.ws.rs.core.Variant} information.
    *                     Any variant-related HTTP headers previously set (namely {@code Content-Type},
    *                     {@code Content-Language} and {@code Content-Encoding}) will be overwritten using
    *                     the entity variant information.
    * @param genericType  genericType representation of a generic Java type of elements in the Response's rx object
    * @return invocation response wrapped in the completion aware type..
    * @throws javax.ws.rs.client.ResponseProcessingException in case processing of a received HTTP response fails (e.g. in a
    *                                                        filter or during conversion of the response entity data to an
    *                                                        instance of a particular Java type).
    * @throws javax.ws.rs.ProcessingException                in case the request processing or subsequent I/O operation fails.
    * @throws javax.ws.rs.WebApplicationException            in case the response status code of the response returned by the
    *                                                        server is not
    *                                                        {@link javax.ws.rs.core.Response.Status.Family#SUCCESSFUL
    *                                                        successful} and the specified response type is not
    *                                                        {@link javax.ws.rs.core.Response}.
    */
   CompletionStage<Response> methodResponse(String name, Entity<?> entity, GenericType<?> genericType);
}
