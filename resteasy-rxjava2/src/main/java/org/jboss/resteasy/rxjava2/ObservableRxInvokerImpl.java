package org.jboss.resteasy.rxjava2;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocationBuilder;
import org.jboss.resteasy.plugins.providers.sse.InboundSseEventImpl;
import org.jboss.resteasy.plugins.providers.sse.client.SseEventSourceImpl;
import org.jboss.resteasy.plugins.providers.sse.client.SseEventSourceImpl.SourceBuilder;
import org.jboss.resteasy.rxjava2.i18n.Messages;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.SyncInvoker;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class ObservableRxInvokerImpl implements ObservableRxInvoker
{
   private static Object monitor = new Object();
   private ClientInvocationBuilder syncInvoker;
   private ScheduledExecutorService executorService;

   public ObservableRxInvokerImpl(final SyncInvoker syncInvoker, final ExecutorService executorService)
   {
      if (!(syncInvoker instanceof ClientInvocationBuilder))
      {
         throw new ProcessingException(Messages.MESSAGES.expectedClientInvocationBuilder(syncInvoker.getClass().getName()));
      }
      this.syncInvoker = (ClientInvocationBuilder) syncInvoker;
      if (executorService instanceof ScheduledExecutorService)
      {
         this.executorService = (ScheduledExecutorService) executorService;
      }
   }

   @Override
   public Observable<?> get()
   {
      return eventSourceToObservable(getEventSource(), String.class, "GET", null, getAccept());
   }

   @Override
   public <R> Observable<?> get(Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "GET", null, getAccept());
   }

   @Override
   public <R> Observable<?> get(GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "GET", null, getAccept());
   }

   @Override
   public Observable<?> put(Entity<?> entity)
   {
      return eventSourceToObservable(getEventSource(), String.class, "PUT", entity, getAccept());
   }

   @Override
   public <R> Observable<?> put(Entity<?> entity, Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "PUT", entity, getAccept());
   }

   @Override
   public <R> Observable<?> put(Entity<?> entity, GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "PUT", entity, getAccept());
   }

   @Override
   public Observable<?> post(Entity<?> entity)
   {
      return eventSourceToObservable(getEventSource(), String.class, "POST", entity, getAccept());
   }

   @Override
   public <R> Observable<?> post(Entity<?> entity, Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "POST", entity, getAccept());
   }

   @Override
   public <R> Observable<?> post(Entity<?> entity, GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "POST", entity, getAccept());
   }

   @Override
   public Observable<?> delete()
   {
      return eventSourceToObservable(getEventSource(), String.class, "DELETE", null, getAccept());
   }

   @Override
   public <R> Observable<?> delete(Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "DELETE", null, getAccept());
   }

   @Override
   public <R> Observable<?> delete(GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "DELETE", null, getAccept());
   }

   @Override
   public Observable<?> head()
   {
      return eventSourceToObservable(getEventSource(), String.class, "HEAD", null, getAccept());
   }

   @Override
   public Observable<?> options()
   {
      return eventSourceToObservable(getEventSource(), String.class, "OPTIONS", null, getAccept());
   }

   @Override
   public <R> Observable<?> options(Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "OPTIONS", null, getAccept());
   }

   @Override
   public <R> Observable<?> options(GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "OPTIONS", null, getAccept());
   }

   @Override
   public Observable<?> trace()
   {
      return eventSourceToObservable(getEventSource(), String.class, "TRACE", null, getAccept());
   }

   @Override
   public <R> Observable<?> trace(Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "TRACE", null, getAccept());
   }

   @Override
   public <R> Observable<?> trace(GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, "TRACE", null, getAccept());
   }

   @Override
   public Observable<?> method(String name)
   {
      return eventSourceToObservable(getEventSource(), String.class, name, null, getAccept());
   }

   @Override
   public <R> Observable<?> method(String name, Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, name, null, getAccept());
   }

   @Override
   public <R> Observable<?> method(String name, GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, name, null, getAccept());
   }

   @Override
   public Observable<?> method(String name, Entity<?> entity)
   {
      return eventSourceToObservable(getEventSource(), String.class, name, entity, getAccept());
   }

   @Override
   public <R> Observable<?> method(String name, Entity<?> entity, Class<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, name, entity, getAccept());
   }

   @Override
   public <R> Observable<?> method(String name, Entity<?> entity, GenericType<R> responseType)
   {
      return eventSourceToObservable(getEventSource(), responseType, name, entity, getAccept());
   }

   @Override
   public CompletionStage<Response> getResponse(Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, "GET", null, getAccept());
   }

   @Override
   public CompletionStage<Response> getResponse(GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, "GET", null, getAccept());
   }

   @Override
   public CompletionStage<Response> putResponse(Entity<?> entity, Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, "PUT", entity, getAccept());
   }

   @Override
   public CompletionStage<Response> putResponse(Entity<?> entity, GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, "PUT", entity, getAccept());
   }

   @Override
   public CompletionStage<Response> postResponse(Entity<?> entity, Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, "POST", entity, getAccept());
   }

   @Override
   public CompletionStage<Response> postResponse(Entity<?> entity, GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, "POST", entity, getAccept());
   }

   @Override
   public CompletionStage<Response> deleteResponse(Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, "DELETE", null, getAccept());
   }

   @Override
   public CompletionStage<Response> deleteResponse(GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, "DELETE", null, getAccept());
   }

   @Override
   public CompletionStage<Response> headResponse() {
      return eventSourceToResponse(getEventSource(true), String.class, "HEAD", null, getAccept());
   }

   @Override
   public CompletionStage<Response> optionsResponse(Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, "OPTIONS", null, getAccept());
   }

   @Override
   public CompletionStage<Response> optionsResponse(GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, "OPTIONS", null, getAccept());
   }

   @Override
   public CompletionStage<Response> traceResponse(Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, "TRACE", null, getAccept());
   }

   @Override
   public CompletionStage<Response> traceResponse(GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, "TRACE", null, getAccept());
   }

   @Override
   public CompletionStage<Response> methodResponse(String name, Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, name, null, getAccept());
   }

   @Override
   public CompletionStage<Response> methodResponse(String name, GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, name, null, getAccept());
   }

   @Override
   public CompletionStage<Response> methodResponse(String name, Entity<?> entity, Class<?> responseType) {
      return eventSourceToResponse(getEventSource(true), responseType, name, entity, getAccept());
   }

   @Override
   public CompletionStage<Response> methodResponse(String name, Entity<?> entity, GenericType<?> genericType) {
      return eventSourceToResponse(getEventSource(true), genericType, name, entity, getAccept());
   }

   ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

   private <T> CompletableFuture<Response> eventSourceToResponse(SseEventSourceImpl sseEventSource, Class<T> clazz, String verb, Entity<?> entity, MediaType[] mediaTypes)
   {
      Observable<T> flowable = eventSourceToObservable(sseEventSource, false, clazz, verb, entity, mediaTypes);
      return CompletableFuture.supplyAsync(
            () -> {
               Response originalResponse = sseEventSource.getResponse();
               return Response.fromResponse(originalResponse).entity(flowable).build();
            });
   }

   private <T> CompletableFuture<Response> eventSourceToResponse(SseEventSourceImpl sseEventSource, GenericType<T> type, String verb, Entity<?> entity, MediaType[] mediaTypes)
   {
      Observable<T> flowable = eventSourceToObservable(sseEventSource, false, type, verb, entity, mediaTypes);
      return CompletableFuture.supplyAsync(
               () -> {
                  Response originalResponse = sseEventSource.getResponse();
                  return Response.fromResponse(originalResponse).entity(flowable).build();
               });
   }

   private <T> Observable<T> eventSourceToObservable(SseEventSourceImpl sseEventSource, Class<T> clazz, String verb, Entity<?> entity, MediaType[] mediaTypes)
   {
      return eventSourceToObservable(sseEventSource, true, clazz, verb, entity, mediaTypes);
   }

   private <T> Observable<T> eventSourceToObservable(SseEventSourceImpl sseEventSource, boolean open, Class<T> clazz, String verb, Entity<?> entity, MediaType[] mediaTypes) {
      Observable<T> observable = Observable.create(
            new ObservableOnSubscribe<T>() {

               @Override
               public void subscribe(ObservableEmitter<T> emitter) throws Exception {
                  sseEventSource.register(
                     (InboundSseEvent e) -> {
                        T t = e.readData(clazz, ((InboundSseEventImpl) e).getMediaType());
                        emitter.onNext(t);
                     },
                     (Throwable t) -> emitter.onError(t),
                     () -> emitter.onComplete());
                  synchronized (monitor)
                  {
                     if (!sseEventSource.isOpen())
                     {
                        try
                        {
                           sseEventSource.open(null, verb, entity, mediaTypes);
                        }
                        catch (IllegalStateException e)
                        {
                           // Ignore
                        }
                     }
                  }
               }
            });
      synchronized (monitor)
      {
         if (!open)
         {
            if (!sseEventSource.isConnected())
            {
               sseEventSource.connect(verb, entity, mediaTypes);
            }
         }
      }
      return observable;
   }

   private <T> Observable<T> eventSourceToObservable(SseEventSourceImpl sseEventSource, GenericType<T> type, String verb, Entity<?> entity, MediaType[] mediaTypes)
   {
      return eventSourceToObservable(sseEventSource, true, type, verb, entity, mediaTypes);
   }

   private <T> Observable<T> eventSourceToObservable(SseEventSourceImpl sseEventSource, boolean open, GenericType<T> type, String verb, Entity<?> entity, MediaType[] mediaTypes)
   {
      Observable<T> observable = Observable.create(
            new ObservableOnSubscribe<T>() {

               @Override
               public void subscribe(ObservableEmitter<T> emitter) throws Exception {
                  sseEventSource.register(
                     (InboundSseEvent e) -> {T t = e.readData(type, ((InboundSseEventImpl) e).getMediaType()); emitter.onNext(t);},
                     (Throwable t) -> emitter.onError(t),
                     () -> emitter.onComplete());
                  synchronized (monitor)
                  {
                     if (!sseEventSource.isOpen())
                     {
                        try
                        {
                           sseEventSource.open(null, verb, entity, mediaTypes);
                        }
                        catch (IllegalStateException e)
                        {
                           // Ignore
                        }
                     }
                  }
               }
            });
      synchronized (monitor)
      {
         if (!open)
         {
            if (!sseEventSource.isConnected())
            {
               sseEventSource.connect(verb, entity, mediaTypes);
            }
         }
      }
      return observable;
   }

   private SseEventSourceImpl getEventSource()
   {
      return getEventSource(false);
   }

   private SseEventSourceImpl getEventSource(boolean getResponse)
   {
      SourceBuilder builder = (SourceBuilder) SseEventSource.target(syncInvoker.getTarget());
      if (executorService != null)
      {
         builder.executor(executorService);
      }
      SseEventSourceImpl sseEventSource = (SseEventSourceImpl) builder.build();
      sseEventSource.setAlwaysReconnect(false);
      if (getResponse)
      {
         sseEventSource.register(new CompletableFuture<Response>());
      }
      return sseEventSource;
   }

   private MediaType[] getAccept()
   {
      if (syncInvoker instanceof ClientInvocationBuilder)
      {
         ClientInvocationBuilder builder = (ClientInvocationBuilder) syncInvoker;
         List<MediaType> accept = builder.getHeaders().getAcceptableMediaTypes();
         return accept.toArray(new MediaType[accept.size()]);
      }
      else
      {
         return null;
      }
   }
}
