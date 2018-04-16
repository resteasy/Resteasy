package org.jboss.resteasy.plugins.providers.sse.client;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import org.apache.http.HttpHeaders;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.plugins.providers.sse.SseConstants;
import org.jboss.resteasy.plugins.providers.sse.SseEventInputImpl;
import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;

public class SseEventSourceImpl implements SseEventSource
{
   public static final long RECONNECT_DEFAULT = 500;

   private final WebTarget target;

   private static final long CLOSE_WAIT = 30;

   private final long reconnectDelay;

   private final boolean disableKeepAlive;

   private final ScheduledExecutorService executor;

   private enum State {
      PENDING, OPEN, CLOSED
   }

   private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

   private final List<Consumer<InboundSseEvent>> onEventConsumers = new CopyOnWriteArrayList<>();

   private final List<Consumer<Throwable>> onErrorConsumers = new CopyOnWriteArrayList<>();

   private final List<Runnable> onCompleteConsumers = new CopyOnWriteArrayList<>();

   protected static class SourceBuilder extends Builder
   {
      private WebTarget target = null;

      private long reconnect = RECONNECT_DEFAULT;

      private String name = null;

      private boolean disableKeepAlive = false;

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
         return new SseEventSourceImpl(target, name, reconnect, disableKeepAlive, false);
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
   }

   public SseEventSourceImpl(final WebTarget target)
   {
      this(target, true);
   }

   public SseEventSourceImpl(final WebTarget target, final boolean open)
   {
      this(target, null, RECONNECT_DEFAULT, false, open);
   }

   private SseEventSourceImpl(final WebTarget target, String name, long reconnectDelay, final boolean disableKeepAlive,
         final boolean open)
   {
      if (target == null)
      {
         throw new IllegalArgumentException(Messages.MESSAGES.webTargetIsNotSetForEventSource());
      }
      this.target = target;
      this.reconnectDelay = reconnectDelay;
      this.disableKeepAlive = disableKeepAlive;

      if (name == null)
      {
         name = String.format("sse-event-source(%s)", target.getUri());
      }
      ScheduledExecutorService scheduledExecutor = null;
      if (target instanceof ResteasyWebTarget)
      {
         scheduledExecutor = ((ResteasyWebTarget) target).getResteasyClient().getScheduledExecutor();
      }
      this.executor = scheduledExecutor != null ? scheduledExecutor : Executors
            .newSingleThreadScheduledExecutor(new DaemonThreadFactory());
      if (open)
      {
         open();
      }
   }

   private static class DaemonThreadFactory implements ThreadFactory
   {
      private static final AtomicInteger poolNumber = new AtomicInteger(1);

      private final ThreadGroup group;

      private final AtomicInteger threadNumber = new AtomicInteger(1);

      private final String namePrefix;

      DaemonThreadFactory()
      {
         SecurityManager s = System.getSecurityManager();
         group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
         namePrefix = "resteasy-sse-eventsource" + poolNumber.getAndIncrement() + "-thread-";
      }

      public Thread newThread(Runnable r)
      {
         Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
         t.setDaemon(true);
         return t;
      }
   }

   @Override
   public void open()
   {
      open(null);
   }

   public void open(String lastEventId)
   {
      if (!state.compareAndSet(State.PENDING, State.OPEN))
      {
         throw new IllegalStateException(Messages.MESSAGES.eventSourceIsNotReadyForOpen());
      }
      EventHandler handler = new EventHandler(reconnectDelay, lastEventId);
      executor.submit(handler);
      handler.awaitConnected();
   }

   @Override
   public boolean isOpen()
   {
      return state.get() == State.OPEN;
   }

   @Override
   public void close()
   {
      this.close(CLOSE_WAIT, TimeUnit.SECONDS);
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

   @Override
   public boolean close(final long timeout, final TimeUnit unit)
   {
      internalClose();
      try
      {
         if (!executor.awaitTermination(timeout, unit))
         {
            return false;
         }
      }
      catch (InterruptedException e)
      {
         notifyErrorConsumers(e);
         Thread.currentThread().interrupt();
         return false;
      }

      return true;
   }
   
   private void internalClose(){
      if (state.getAndSet(State.CLOSED) != State.CLOSED)
      {
         ResteasyWebTarget resteasyWebTarget = (ResteasyWebTarget) target;
         //close httpEngine to close connection
         resteasyWebTarget.getResteasyClient().httpEngine().close();
         executor.shutdownNow();
         notifyCompleteConsumers();
      }
   }
   
   private void notifyEventConsumers(InboundSseEvent sseEvent)
   {
      onEventConsumers.forEach(consumer -> {
         try
         {
            consumer.accept(sseEvent);
         }
         catch (Throwable e)
         {
            //We don't care about the error but we don't want it to prevent us from iterating on others consumers.
         }
      });
   }

   private void notifyErrorConsumers(Throwable throwable)
   {
      onErrorConsumers.forEach(consumer -> {
         try
         {
            consumer.accept(throwable);
         }
         catch (Throwable e)
         {
            // We don't care about the error but we don't want it to prevent us from iterating on others consumers.
         }
      });
   }

   private void notifyCompleteConsumers()
   {
      onCompleteConsumers.forEach((runnable) -> {
         try
         {
            runnable.run();
         }
         catch (Throwable e)
         {
            //We don't care about the error but we don't want it to prevent us from iterating on others consumers.
         }
      });
   }

   private class EventHandler implements Runnable
   {

      private final CountDownLatch connectedLatch;

      private String lastEventId;

      private long reconnectDelay;

      public EventHandler(final long reconnectDelay, final String lastEventId)
      {
         this.connectedLatch = new CountDownLatch(1);
         this.reconnectDelay = reconnectDelay;
         this.lastEventId = lastEventId;
      }

      private EventHandler(final EventHandler anotherHandler)
      {
         this.connectedLatch = anotherHandler.connectedLatch;
         this.reconnectDelay = anotherHandler.reconnectDelay;
         this.lastEventId = anotherHandler.lastEventId;
      }

      @Override
      public void run()
      {
         if (state.get() != State.OPEN)
         {
            return;
         }
         
         SseEventInputImpl eventInput = null;
         long delay = reconnectDelay;
         Response response = null;
         try
         {
            Date requestTime = new Date();
            try
            {
               response = buildRequest().get();
            }
            catch (ProcessingException | IllegalArgumentException e)
            {
               if (State.CLOSED == state.get())
               {
                  // At this stage the ProcessingException can be either a normal consequence of the 'close(...)' method invocation
                  // and in this case it's not an error at all, or a real error due to IO problem.
                  // So instead of notifying error consumers of something that may not be an error at all, it is acceptable to do
                  // nothing since user already asked to close the SseEventSource anyway.
                  //
                  // IllegalStateException should always be a normal consequence of the 'close(...)' method invocation.
                  return;
               }
               throw e;
            }
            switch (response.getStatus())
            {
               case 200 :
                  MediaType mediaType = response.getMediaType();
                  //We don't want to include charset and other params in the check
                  if (mediaType != null && MediaType.SERVER_SENT_EVENTS_TYPE.getType().equals(mediaType.getType())
                        && MediaType.SERVER_SENT_EVENTS_TYPE.getSubtype().equals(mediaType.getSubtype()))
                  {
                     try
                     {
                        eventInput = response.readEntity(SseEventInputImpl.class);
                     }
                     catch (ProcessingException | IllegalArgumentException e)
                     {
                        if (State.CLOSED == state.get())
                        {
                           // At this stage the ProcessingException or IllegalStateException can be either a normal consequence of
                           // the 'close(...)' method invocation and in this case it's not an error at all, or a real error due to 
                           // IO/response processing problems.
                           // So instead of notifying error consumers of something that may not be an error at all, it is acceptable to do
                           // nothing since user already asked to close the SseEventSource anyway.
                           return;
                        }
                        throw e;
                     }
                  }
                  else
                  {
                     // Throw exception.
                     ClientInvocation.handleErrorStatus(response);
                  }
                  break;
               case 204 :
                  internalClose();
                  break;
               case 503 :
                  ServiceUnavailableException serviceUnavailableException = new ServiceUnavailableException(response);
                  if (serviceUnavailableException.hasRetryAfter())
                  {
                     delay = serviceUnavailableException.getRetryTime(requestTime).getTime() - requestTime.getTime();
                     // No need to notify error consumers since it is not an unrecoverable error.
                     // 503 with retry after is an error whose recovery mechanism is reconnect so it's not unrecoverable.
                  }
                  else
                  {
                     // 503 without retry after is an error without recovery mechanism so we have to notify on error consumers.
                     throw serviceUnavailableException;
                  }
                  break;
               default :
                  // Throw exception.
                  ClientInvocation.handleErrorStatus(response);
                  break;
            }
         }
         catch (Throwable e)
         {
            // Fail connection is an unrecoverable error so we have to notify error consumers.
            notifyErrorConsumers(e);
            internalClose();
         }
         finally
         {
            if (connectedLatch != null)
            {
               connectedLatch.countDown();
            }
         }
         
         while (!Thread.currentThread().isInterrupted() && state.get() == State.OPEN)
         {
            if (eventInput == null || eventInput.isClosed())
            {
               reconnect(response, delay);
               break;
            }
            else
            {
               try
               {
                  InboundSseEvent event = eventInput.read();
                  if (event != null)
                  {
                     onEvent(event);
                     if (event.isReconnectDelaySet())
                     {
                        delay = event.getReconnectDelay();
                     }
                     notifyEventConsumers(event);
                  }
               }
               catch (IOException e)
               {
                  reconnect(response,delay);
                  break;
               }
            }
         }
      }

      public void awaitConnected()
      {
         try
         {
            connectedLatch.await(30, TimeUnit.SECONDS);
         }
         catch (InterruptedException ex)
         {
            Thread.currentThread().interrupt();
         }

      }

      private void onEvent(final InboundSseEvent event)
      {
         if (event == null)
         {
            return;
         }
         if (event.getId() != null)
         {
            lastEventId = event.getId();
         }

      }

      private Invocation.Builder buildRequest()
      {
         final Invocation.Builder request = target.request(MediaType.SERVER_SENT_EVENTS_TYPE);
         if (lastEventId != null && !lastEventId.isEmpty())
         {
            request.header(SseConstants.LAST_EVENT_ID_HEADER, lastEventId);
         }
         if (disableKeepAlive)
         {
            request.header(HttpHeaders.CONNECTION, "close");
         }
         return request;
      }

      private void reconnect(Response previousResponse, final long delay)
      {
         // Let's close the previous response to be sure to release any resource (pooled connections) before trying again.
         // It is useful since only the response headers may have been processed and the response entity ignored.
         // previousRepsonse must/will not be null when this method is called.
         previousResponse.close();
         if (state.get() != State.OPEN)
         {
            return;
         }

         EventHandler processor = new EventHandler(this);
         if (delay > 0)
         {
            executor.schedule(processor, delay, TimeUnit.MILLISECONDS);
         }
         else
         {
            executor.submit(processor);
         }
      }
   }

}
