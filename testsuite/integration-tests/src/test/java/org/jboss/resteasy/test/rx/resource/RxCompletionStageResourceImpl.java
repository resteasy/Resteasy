package org.jboss.resteasy.test.rx.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.rxjava.SingleProvider;
import org.jboss.resteasy.test.rx.resource.Thing;

import rx.Single;

@Path("")
public class RxCompletionStageResourceImpl {

   private static SingleProvider singleProvider = new SingleProvider();
   
   @SuppressWarnings("unchecked")
   @GET
   @Path("get/string")
   @Produces(MediaType.TEXT_PLAIN)
   public CompletionStage<String> get() throws InterruptedException {
      return (CompletionStage<String>) singleProvider.toCompletionStage(Single.just("x"));
   }

   @SuppressWarnings("unchecked")
   @GET
   @Path("get/thing")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<Thing> getThing() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<Thing>) singleProvider.toCompletionStage(Single.just(new Thing("x")));
   }

   @GET
   @Path("get/thing/list")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<List<Thing>> getThingList() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<List<Thing>>) buildCompletionStageThingList("x", 3);
   }

   @SuppressWarnings("unchecked")
   @PUT
   @Path("put/string")
   @Produces(MediaType.TEXT_PLAIN)
   public CompletionStage<String> put(String s) throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<String>) singleProvider.toCompletionStage(Single.just(s));
   }

   @SuppressWarnings("unchecked")
   @PUT
   @Path("put/thing")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<Thing> putThing(String s) throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<Thing>) singleProvider.toCompletionStage(Single.just(new Thing(s)));
   }

   @PUT
   @Path("put/thing/list")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<List<Thing>> putThingList(String s) throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<List<Thing>>) buildCompletionStageThingList(s, 3);
   }

   @SuppressWarnings("unchecked")
   @POST
   @Path("post/string")
   @Produces(MediaType.TEXT_PLAIN)
   public CompletionStage<String> post(String s) throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<String>) singleProvider.toCompletionStage(Single.just(s));
   }

   @SuppressWarnings("unchecked")
   @POST
   @Path("post/thing")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<Thing> postThing(String s) throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<Thing>) singleProvider.toCompletionStage(Single.just(new Thing(s)));
   }

   @POST
   @Path("post/thing/list")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<List<Thing>> postThingList(String s) throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<List<Thing>>) buildCompletionStageThingList(s, 3);
   }

   @SuppressWarnings("unchecked")
   @DELETE
   @Path("delete/string")
   @Produces(MediaType.TEXT_PLAIN)
   public CompletionStage<String> delete() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<String>) singleProvider.toCompletionStage(Single.just("x"));
   }

   @SuppressWarnings("unchecked")
   @DELETE
   @Path("delete/thing")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<Thing> deleteThing() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<Thing>) singleProvider.toCompletionStage(Single.just(new Thing("x")));
   }

   @DELETE
   @Path("delete/thing/list")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<List<Thing>> deleteThingList() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<List<Thing>>) buildCompletionStageThingList("x", 3);
   }

   @SuppressWarnings("unchecked")
   @HEAD
   @Path("head/string")
   @Produces(MediaType.TEXT_PLAIN)
   public CompletionStage<String> head() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<String>) singleProvider.toCompletionStage(Single.just("x"));
   }

   @SuppressWarnings("unchecked")
   @OPTIONS
   @Path("options/string")
   @Produces(MediaType.TEXT_PLAIN)
   public CompletionStage<String> options() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<String>) singleProvider.toCompletionStage(Single.just("x"));
   }

   @SuppressWarnings("unchecked")
   @OPTIONS
   @Path("options/thing")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<Thing> optionsThing() throws InterruptedException {
      Thread.sleep(500);
      return (CompletionStage<Thing>) singleProvider.toCompletionStage(Single.just(new Thing("x")));
   }

   @OPTIONS
   @Path("options/thing/list")
   @Produces(MediaType.APPLICATION_XML)
   public CompletionStage<List<Thing>> optionsThingList() throws InterruptedException {
      Thread.sleep(500);
      return buildCompletionStageThingList("x", 3);
   }

   @SuppressWarnings("unchecked")
   static CompletionStage<List<Thing>> buildCompletionStageThingList(String s, int listSize) {
      List<Thing> list = new ArrayList<Thing>();
      for (int i = 0; i < listSize; i++) {
         list.add(new Thing(s));
      }
      return (CompletionStage<List<Thing>>) singleProvider.toCompletionStage(Single.just(list));
   }
   
   @GET
   @Path("exception/unhandled")
   public CompletionStage<Thing> exceptionUnhandled() throws Exception {
      throw new Exception("unhandled");
   }

   @GET
   @Path("exception/handled")
   public CompletionStage<Thing> exceptionHandled() throws Exception {
      throw new TestException("handled");
   }
}
