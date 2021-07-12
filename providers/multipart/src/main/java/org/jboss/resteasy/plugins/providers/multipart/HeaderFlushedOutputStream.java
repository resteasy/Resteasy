package org.jboss.resteasy.plugins.providers.multipart;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.RuntimeDelegate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class HeaderFlushedOutputStream extends OutputStream {
   private final MultivaluedMap<String, Object> headers;
   private final OutputStream stream;
   private boolean headersFlushed = false;

   public HeaderFlushedOutputStream(final MultivaluedMap<String, Object> headers,
                                    final OutputStream delegate) {
      this.headers = headers;
      this.stream = delegate;
   }

   @SuppressWarnings(value = "unchecked")
   protected void flushHeaders() throws IOException {
      if (headersFlushed)
         return;

      headersFlushed = true;
      RuntimeDelegate delegate = RuntimeDelegate.getInstance();

      for (String key : headers.keySet()) {
         for (Object obj : headers.get(key)) {
            String value;
            RuntimeDelegate.HeaderDelegate headerDelegate = delegate
                  .createHeaderDelegate(obj.getClass());
            if (headerDelegate != null) {
               value = headerDelegate.toString(obj);
            } else {
               value = obj.toString();
            }
            stream.write(key.getBytes(StandardCharsets.US_ASCII));
            stream.write(AbstractMultipartWriter.COLON_SPACE_BYTES);
            stream.write(value.getBytes(StandardCharsets.US_ASCII));
            stream.write(AbstractMultipartWriter.LINE_SEPARATOR_BYTES);
         }
      }
      stream.write(AbstractMultipartWriter.LINE_SEPARATOR_BYTES);

   }

   @Override
   public void write(int i) throws IOException {
      flushHeaders();
      stream.write(i);
   }

   @Override
   public void write(byte[] bytes) throws IOException {
      flushHeaders();
      stream.write(bytes);
   }

   @Override
   public void write(byte[] bytes, int i, int i1) throws IOException {
      flushHeaders();
      stream.write(bytes, i, i1);
   }

   @Override
   public void flush() throws IOException {
      stream.flush();
   }

   @Override
   public void close() throws IOException {
      stream.close();
   }
}
