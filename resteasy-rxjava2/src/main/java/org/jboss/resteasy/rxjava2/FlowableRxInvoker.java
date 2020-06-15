package org.jboss.resteasy.rxjava2;

import javax.ws.rs.client.RxInvoker;

import org.jboss.resteasy.client.jaxrs.RxInvokerExt;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

public interface FlowableRxInvoker extends RxInvoker<Flowable<?>>, RxInvokerExt
{
   BackpressureStrategy getBackpressureStrategy();

   void setBackpressureStrategy(BackpressureStrategy backpressureStrategy);
}
