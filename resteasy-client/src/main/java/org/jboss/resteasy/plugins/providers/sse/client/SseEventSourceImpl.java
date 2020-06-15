package org.jboss.resteasy.plugins.providers.sse.client;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.plugins.providers.sse.SseConstants;
import org.jboss.resteasy.plugins.providers.sse.SseEventInputImpl;
import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;

public class SseEventSourceImpl implements SseEventSource
{
   public static final long RECONNECT_DEFAULT = 500;

   private final WebTarget target;

   private final long reconnectDelay;

   private final SseEventSourceScheduler sseEventSourceScheduler;

   private enum State {
      PENDING, CONNECTED, OPEN, CLOSED
   }

   private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

   private final List<Consumer<InboundSseEvent>> onEventConsumers = new CopyOnWriteArrayList<>();

   private final List<Consumer<Throwable>> onErrorConsumers = new CopyOnWriteArrayList<>();

   private final List<Runnable> onCompleteConsumers = new CopyOnWriteArrayList<>();

   private boolean alwaysReconnect;

   private EventHandler handler;

   private volatile ClientResponse response;

   private CompletableFuture<Response> responseFuture;

   public static class SourceBuilder extends Builder
   {
      private WebTarget target = null;

      private long reconnect = RECONNECT_DEFAULT;

      private String name = null;

      private ScheduledExecutorService executor;

      public SourceBuilder()
      {
         //NOOP
      }

      public Builder named(String name)
      {
         this.name = name;
         return this;
      }

      public SseEventSource build()
      {
         return new SseEventSourceImpl(target, name, reconnect, false, executor);
      }

      @Override
      public Builder target(WebTarget target)
      {
         if (target == null)
         {
            throw new NullPointerException();
         }
         this.target = target;
         return this;
      }

      @Override
      public Builder reconnectingEvery(long delay, TimeUnit unit)
      {
         reconnect = unit.toMillis(delay);
         return this;
      }

      public Builder executor(ScheduledExecutorService executor)
      {
         this.executor = executor;
         return this;
      }
   }

   public SseEventSourceImpl(final WebTarget target)
   {
      this(target, true);
   }

   public SseEventSourceImpl(final WebTarget target, final boolean open)
   {
      this(target, null, RECONNECT_DEFAULT, open, null);
   }

   private SseEventSourceImpl(final WebTarget target, final String name, final long reconnectDelay, final boolean open, final ScheduledExecutorService executor)
   {
      if (target == null)
      {
         throw new IllegalArgumentException(Messages.MESSAGES.webTargetIsNotSetForEventSource());
      }
      this.target = target;
      this.reconnectDelay = reconnectDelay;
      //tck requries this
      this.alwaysReconnect = true;

      if (executor == null)
      {
         ScheduledExecutorService scheduledExecutor = null;
         if (target instanceof ResteasyWebTarget)
         {
            scheduledExecutor = ((ResteasyWebTarget) target).getResteasyClient().getScheduledExecutor();
         }
         if (name != null) {
            this.sseEventSourceScheduler = new SseEventSourceScheduler(scheduledExecutor, name);
         } else {
            this.sseEventSourceScheduler = new SseEventSourceScheduler(scheduledExecutor, String.format("sse-event-source(%s)", target.getUri()));
         }
      }
      else
      {
         if (name != null) {
            this.sseEventSourceScheduler = new SseEventSourceScheduler(executor, name);
         } else {
            this.sseEventSourceScheduler = new SseEventSourceScheduler(executor, String.format("sse-event-source(%s)", target.getUri()));
         }
      }

      if (open)
      {
         open();
      }
   }

   @Override
   public void open()
   {
      open(null);
   }

   public void open(String lastEventId)
   {
      open(lastEventId, "GET", null, MediaType.SERVER_SENT_EVENTS_TYPE);
   }

   public void open(String lastEventId, String verb, Entity<?> entity, MediaType... mediaTypes)
   {
      if (state.compareAndSet(State.CONNECTED, State.OPEN))
      { // if connect() was called
         handler.startReading();
         handler.awaitConnected();
         return;
      }
      if (!state.compareAndSet(State.PENDING, State.OPEN))
      {
         throw new IllegalStateException(Messages.MESSAGES.eventSourceIsNotReadyForOpen());
      }
      handler = new EventHandler(reconnectDelay, lastEventId, verb, entity, mediaTypes);
      sseEventSourceScheduler.schedule(handler, 0, TimeUnit.SECONDS);
      handler.awaitConnected();
   }

   @Override
   public boolean isOpen()
   {
      return state.get() == State.OPEN;
   }

   /*
    Added for implementations of
    connect() causes the EventHandler to make the initial invocation so that a
    Response can be returned if responseFuture != null. EventHandler will then start
    to read events once open() is called.
    */
   public void connect(String verb, Entity<?> entity, MediaType... mediaTypes)
   {
      if (!state.compareAndSet(State.PENDING, State.CONNECTED))
      {
         throw new IllegalStateException(Messages.MESSAGES.eventSourceIsNotReadyForOpen());
      }
      handler = new EventHandler(new CountDownLatch(1), reconnectDelay, null, verb, entity, mediaTypes);
      sseEventSourceScheduler.schedule(handler, 0, TimeUnit.SECONDS);
   }

   public boolean isConnected()
   {
      return state.get() == State.CONNECTED;
   }

   @Override
   public void register(Consumer<InboundSseEvent> onEvent)
   {
      if (onEvent == null)
      {
         throw new IllegalArgumentException();
      }
      onEventConsumers.add(onEvent);
   }

   @Override
   public void register(Consumer<InboundSseEvent> onEvent, Consumer<Throwable> onError)
   {
      if (onEvent == null)
      {
         throw new IllegalArgumentException();
      }
      if (onError == null)
      {
         throw new IllegalArgumentException();
      }
      onEventConsumers.add(onEvent);
      onErrorConsumers.add(onError);
   }

   @Override
   public void register(Consumer<InboundSseEvent> onEvent, Consumer<Throwable> onError, Runnable onComplete)
   {
      if (onEvent == null)
      {
         throw new IllegalArgumentException();
      }
      if (onError == null)
      {
         throw new IllegalArgumentException();
      }
      if (onComplete == null)
      {
         throw new IllegalArgumentException();
      }
      onEventConsumers.add(onEvent);
      onErrorConsumers.add(onError);
      onCompleteConsumers.add(onComplete);
   }

   /*
    * Sets responseFuture so that the initial Response generated in EventHandler
    * can be retrieved.
    */
   public void register(CompletableFuture<Response> responseFuture)
   {
      this.responseFuture = responseFuture;
   }

   public Response getResponse()
   {
      try {
         return responseFuture.get();
      } catch (InterruptedException | ExecutionException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public boolean close(final long timeout, final TimeUnit unit)
   {
      internalClose();
      try
      {
         return sseEventSourceScheduler.awaitTermination(timeout, unit);
      }
      catch (InterruptedException e)
      {
         onErrorConsumers.forEach(consumer -> {
            consumer.accept(e);
         });
         Thread.currentThread().interrupt();
         return false;
      }
   }

   private void internalClose()
   {
      if (state.getAndSet(State.CLOSED) == State.CLOSED)
      {
         return;
      }
      if (response != null)
      {
         try
         {
            response.releaseConnection(false);
         }
         catch (IOException e)
         {
            onErrorConsumers.forEach(consumer -> {
               consumer.accept(e);
            });
         }
      }
      sseEventSourceScheduler.shutdownNow();
      onCompleteConsumers.forEach(Runnable::run);
   }

   public void setAlwaysReconnect(boolean always)
   {
      this.alwaysReconnect = always;
   }

   private class EventHandler implements Runnable
   {

      private final CountDownLatch connectedLatch;

      private String lastEventId;

      private long reconnectDelay;

      private String verb;
      private Entity<?> entity;
      private MediaType[] mediaTypes;
      private CountDownLatch readLatch;

      EventHandler(final long reconnectDelay, final String lastEventId, final String verb, final Entity<?> entity, final MediaType... mediaTypes)
      {
         this.connectedLatch = new CountDownLatch(1);
         this.reconnectDelay = reconnectDelay;
         this.lastEventId = lastEventId;
         this.verb = verb;
         this.entity = entity;
         this.mediaTypes = mediaTypes;
      }

      EventHandler(final CountDownLatch readLatch, final long reconnectDelay, final String lastEventId, final String verb, final Entity<?> entity, final MediaType... mediaTypes)
      {
         this(reconnectDelay, lastEventId, verb, entity, mediaTypes);
         this.readLatch = readLatch;
      }

      private EventHandler(final EventHandler anotherHandler)
      {
         this.connectedLatch = anotherHandler.connectedLatch;
         this.reconnectDelay = anotherHandler.reconnectDelay;
         this.lastEventId = anotherHandler.lastEventId;
         this.verb = anotherHandler.verb;
         this.entity = anotherHandler.entity;
         this.mediaTypes = anotherHandler.mediaTypes;
      }

      @Override
      public void run()
      {
         if (state.get() != State.OPEN && state.get() != State.CONNECTED)
         {
            return;
         }

         SseEventInputImpl eventInput = null;
         try
         {
            final Invocation.Builder requestBuilder = buildRequest(mediaTypes);
            Invocation request = null;
            if (entity == null)
            {
               request = requestBuilder.build(verb);
            }
            else
            {
               request = requestBuilder.build(verb, entity);
            }
            response = (ClientResponse) request.invoke();

            if (responseFuture != null)
            {
               responseFuture.complete(response);
            }

            if (Family.SUCCESSFUL.equals(response.getStatusInfo().getFamily()))
            {
               onConnection();
               eventInput = response.readEntity(SseEventInputImpl.class);
               //if 200<= response code <300 and response contentType is null, fail the connection.
               if (eventInput == null && !alwaysReconnect)
               {
                  waitForSubscriber();
                  internalClose();
                  return;
               }
            }
            else
            {
               //Let's buffer the entity in case the response contains an entity the user would like to retrieve from the exception.
               //This will also ensure that the connection is correctly closed.
               response.bufferEntity();
               //Throw an instance of WebApplicationException depending on the response.
               waitForSubscriber();
               ClientInvocation.handleErrorStatus(response);
            }
         }
         catch (ServiceUnavailableException ex)
         {
            waitForSubscriber();
            if (ex.hasRetryAfter())
            {
               onConnection();
               Date requestTime = new Date();
               long localReconnectDelay = ex.getRetryTime(requestTime).getTime() - requestTime.getTime();
               onErrorConsumers.forEach(consumer -> {
                  consumer.accept(ex);
               });
               reconnect(localReconnectDelay);
            }
            else
            {
               onUnrecoverableError(ex);
            }
            return;
         }
         catch (Throwable e)
         {
            waitForSubscriber();
            onUnrecoverableError(e);
            return;
         }

         waitForSubscriber();

         while (!Thread.currentThread().isInterrupted() && state.get() == State.OPEN)
         {
            if (eventInput == null || eventInput.isClosed())
            {
               reconnect(reconnectDelay);
               break;
            }
            try
            {
               eventInput.setProviders((ClientConfiguration) target.getConfiguration());
               InboundSseEvent event = eventInput.read();
               if (event != null)
               {
                  onEvent(event);
               }
               //event sink closed
               else if (!alwaysReconnect)
               {
                  internalClose();
                  break;
               }
            }
            catch (IOException e)
            {
               reconnect(reconnectDelay);
               break;
            }
         }
      }

      public void awaitConnected()
      {
         try
         {
            connectedLatch.await();
         }
         catch (InterruptedException ex)
         {
            Thread.currentThread().interrupt();
         }

      }

      /*
       * Noop if readLatch is null, i.e., if connect() is not called before open().
       */
      private void waitForSubscriber()
      {
         if (readLatch == null)
         {
            return;
         }
         while (true)
         {
            try
            {
               readLatch.await();
               return;
            }
            catch (InterruptedException e) {
               // Keep waiting.
            }
         }
      }

      private void onConnection()
      {
         connectedLatch.countDown();
      }

      private void onUnrecoverableError(Throwable throwable)
      {
         connectedLatch.countDown();
         onErrorConsumers.forEach(consumer -> {
            consumer.accept(throwable);
         });
         internalClose();
      }

      private void onEvent(final InboundSseEvent event)
      {
         if (event.getId() != null)
         {
            lastEventId = event.getId();
         }
         if (event.isReconnectDelaySet())
         {
            reconnectDelay = event.getReconnectDelay();
         }
         onEventConsumers.forEach(consumer -> {
            consumer.accept(event);
         });
      }

      private void startReading()
      {
         readLatch.countDown();
      }

      private Invocation.Builder buildRequest(MediaType... mediaTypes)
      {
         final Invocation.Builder request = (mediaTypes != null && mediaTypes.length > 0) ? target.request(mediaTypes) : target.request();
         if (lastEventId != null && !lastEventId.isEmpty())
         {
            request.header(SseConstants.LAST_EVENT_ID_HEADER, lastEventId);
         }
         return request;
      }

      private void reconnect(final long delay)
      {
         if (state.get() != State.OPEN)
         {
            return;
         }

         EventHandler processor = new EventHandler(this);
         sseEventSourceScheduler.schedule(processor, delay, TimeUnit.MILLISECONDS);
      }
   }


}
