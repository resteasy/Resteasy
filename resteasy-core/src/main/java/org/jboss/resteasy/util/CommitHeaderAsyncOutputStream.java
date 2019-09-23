package org.jboss.resteasy.util;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import org.jboss.resteasy.spi.AsyncOutputStream;

public class CommitHeaderAsyncOutputStream extends AsyncOutputStream
{
   protected AsyncOutputStream delegate;
   protected boolean isHeadersCommitted;
   protected CommitHeaderOutputStream.CommitCallback headers;

   public CommitHeaderAsyncOutputStream(final AsyncOutputStream delegate, final CommitHeaderOutputStream.CommitCallback headers)
   {
      this.delegate = delegate;
      this.headers = headers;
   }

   public CommitHeaderAsyncOutputStream()
   {
   }

   public AsyncOutputStream getDelegate()
   {
      return delegate;
   }

   public void setDelegate(AsyncOutputStream delegate)
   {
      this.delegate = delegate;
   }

   public void setHeaders(CommitHeaderOutputStream.CommitCallback headers)
   {
      this.headers = headers;
   }

   public synchronized void commit()
   {
      if (isHeadersCommitted) return;
      isHeadersCommitted = true;
      headers.commit();
   }

   @Override
   public void write(int i) throws IOException
   {
      commit();
      delegate.write(i);
   }

   @Override
   public void write(byte[] bytes) throws IOException
   {
      commit();
      delegate.write(bytes);
   }

   @Override
   public void write(byte[] bytes, int i, int i1) throws IOException
   {
      commit();
      delegate.write(bytes, i, i1);
   }

   @Override
   public void flush() throws IOException
   {
      commit();
      delegate.flush();
   }



   @Override
   public void close() throws IOException
   {
      commit();
      delegate.close();
   }

   @Override
   public CompletionStage<Void> rxFlush()
   {
      commit();
      return delegate.rxFlush();
   }

   @Override
   public CompletionStage<Void> rxWrite(byte[] bytes)
   {
      commit();
      return delegate.rxWrite(bytes);
   }

}
