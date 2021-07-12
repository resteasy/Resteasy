package org.jboss.resteasy.test.response.resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.annotations.jaxrs.QueryParam;
import org.jboss.resteasy.spi.HttpRequest;

import io.reactivex.Single;

@Path("")
public class CompletionStageResponseResource {

   public static final String HELLO = "Hello CompletionStage world!";
   public static final String EXCEPTION = "CompletionStage exception";

   @GET
   @Path("text")
   @Produces("text/plain")
   public CompletionStage<String> text(@Context HttpRequest req) {
      req.getAsyncContext().getAsyncResponse().register(new AsyncResponseCallback("text"));
      CompletableFuture<String> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
            new Runnable() {
               public void run() {
                  cs.complete(HELLO);
               }
            });
      return cs;
   }

   @GET
   @Path("textSingle")
   @Produces("text/plain")
   public Single<String> textSingle() {
      return Single.create(emitter -> {
         ExecutorService executor = Executors.newSingleThreadExecutor();
         executor.submit(
               new Runnable() {
                  public void run() {
                     emitter.onSuccess(HELLO);
                  }
               });
      });
   }

   @GET
   @Path("testclass")
   public CompletionStage<CompletionStageResponseTestClass> entityTestClass() {
      CompletableFuture<CompletionStageResponseTestClass> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
            new Runnable() {
               public void run() {
                  cs.complete(new CompletionStageResponseTestClass("pdq"));
               }
            });
      return cs;
   }

   @GET
   @Path("response")
   @Produces("text/xxx")
   public CompletionStage<Response> response() {
      CompletableFuture<Response> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
            new Runnable() {
               public void run() {
                  cs.complete(Response.ok(HELLO, "text/plain").build());
               }
            });
      return cs;
   }

   @GET
   @Path("responsetestclass")
   public CompletionStage<Response> responseTestClass() {
      CompletableFuture<Response> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
            new Runnable() {
               public void run() {
                  cs.complete(Response.ok(new CompletionStageResponseTestClass("pdq")).build());
               }
            });
      return cs;
   }

   @GET
   @Path("null")
   @Produces("text/plain")
   public CompletionStage<String> nullEntity() {
      CompletableFuture<String> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
            new Runnable() {
               public void run() {
                  cs.complete(null);
               }
            });
      return cs;
   }

   @GET
   @Path("exception/delay")
   @Produces("text/plain")
   public CompletionStage<String> exceptionDelay(@Context HttpRequest req) {
      req.getAsyncContext().getAsyncResponse().register(new AsyncResponseCallback("exception/delay"));
      CompletableFuture<String> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
            new Runnable() {
               public void run() {
                  try {
                     Thread.sleep(3000L); // make sure that response will be created after end-point method ends
                  } catch (InterruptedException e) {
                     throw new RuntimeException(e);
                  }
                  Response response = Response.status(444).entity(EXCEPTION).build();
                  cs.completeExceptionally(new WebApplicationException(response));
               }
            });
      return cs;
   }

   @GET
   @Path("exception/delay-wrapped")
   @Produces("text/plain")
   public CompletionStage<String> exceptionDelayWrapped(@Context HttpRequest req) {
      req.getAsyncContext().getAsyncResponse().register(new AsyncResponseCallback("exception/delay-wrapped"));
      CompletableFuture<String> cs = CompletableFuture.completedFuture("OK");
      ExecutorService executor = Executors.newSingleThreadExecutor();
      return cs.thenApplyAsync(text -> {
         try {
            Thread.sleep(3000L); // make sure that response will be created after end-point method ends
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
         Response response = Response.status(444).entity(EXCEPTION).build();
         throw new WebApplicationException(response);
      }, executor);
   }

   @GET
   @Path("exception/immediate/runtime")
   @Produces("text/plain")
   public CompletionStage<String> exceptionImmediateRuntime(@Context HttpRequest req) {
      req.getAsyncContext().getAsyncResponse().register(new AsyncResponseCallback("exception/immediate/runtime"));
      throw new RuntimeException(EXCEPTION + ": expect stacktrace");
   }

   @GET
   @Path("exception/immediate/notruntime")
   @Produces("text/plain")
   public CompletionStage<String> exceptionImmediateNotRuntime(@Context HttpRequest req) throws Exception {
      req.getAsyncContext().getAsyncResponse().register(new AsyncResponseCallback("exception/immediate/notruntime"));
      throw new Exception( EXCEPTION + ": expect stacktrace");
   }

   @GET
   @Path("callback-called-no-error")
   public String callbackCalledNoError(@QueryParam String p) {
      AsyncResponseCallback.assertCalled(p, false);
      return "OK";
   }

   @GET
   @Path("callback-called-with-error")
   public String callbackCalledWithError(@QueryParam String p) {
      AsyncResponseCallback.assertCalled(p, true);
      return "OK";
   }

   @GET
   @Path("host")
   public String getHost(@Context UriInfo uri) {
      return uri.getRequestUri().getHost();
   }

   @GET
   @Path("sleep")
   @Produces("text/plain")
   public CompletionStage<String> sleep() {
      CompletableFuture<String> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(() -> {
         try {
            Thread.sleep(3000L);
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
         cs.complete(HELLO);
      });
      return cs;
   }


   @GET
   @Path("cftext")
   @Produces("text/plain")
   public CompletableFuture<String> completableFutureText(@Context HttpRequest req) {
      req.getAsyncContext().getAsyncResponse().register(new AsyncResponseCallback("cftext"));
      CompletableFuture<String> cs = new CompletableFuture<>();
      ExecutorService executor = Executors.newSingleThreadExecutor();
      executor.submit(
         new Runnable() {
            public void run() {
               cs.complete(HELLO);
            }
         });
      return cs;
   }
}
