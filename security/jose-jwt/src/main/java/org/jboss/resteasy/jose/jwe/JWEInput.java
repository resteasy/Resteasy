package org.jboss.resteasy.jose.jwe;

import org.jboss.resteasy.jose.i18n.Messages;
import org.jboss.resteasy.jose.jwe.crypto.DirectDecrypter;
import org.jboss.resteasy.jose.jwe.crypto.RSADecrypter;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Providers;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class JWEInput
{
   String wireString;
   String encodedHeader;
   String encodedKey;
   String encodedIv;
   String encodedContent;
   String encodedAuthTag;

   JWEHeader header;
   byte[] rawContent;
   Providers providers;

   private static ObjectMapper mapper = new ObjectMapper();

   static
   {
      mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
   }

   public JWEInput(final String wire)
   {
      this(wire, null);
   }

   public JWEInput(final String wire, final Providers providers)
   {
      this.providers = providers;
      this.wireString = wire;
      String[] parts = wire.split("\\.");
      if (parts.length != 5) throw new IllegalArgumentException("Parsing error");
      encodedHeader = parts[0];
      encodedKey = parts[1];
      encodedIv = parts[2];
      encodedContent = parts[3];
      encodedAuthTag = parts[4];
      try
      {
         byte[] headerBytes = Base64.getUrlDecoder().decode(encodedHeader);
         header = mapper.readValue(headerBytes, JWEHeader.class);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
   }

   public String getWireString()
   {
      return wireString;
   }

   public JWEHeader getHeader()
   {
      return header;
   }


   public ContentReader decrypt(RSAPrivateKey privateKey)
   {
      Algorithm algorithm = header.getAlgorithm();
      if (algorithm == null) throw new IllegalStateException(Messages.MESSAGES.algorithmWasNull());
      if (algorithm != Algorithm.RSA1_5 && algorithm != Algorithm.RSA_OAEP)
         throw new IllegalStateException(Messages.MESSAGES.notEncryptedWithRSAalgorithm());
      EncryptionMethod enc = header.getEncryptionMethod();
      if (algorithm == null) throw new IllegalStateException(Messages.MESSAGES.encryptionMethodWasNull());
      rawContent = RSADecrypter.decrypt(header, encodedHeader, encodedKey, encodedIv, encodedContent, encodedAuthTag, privateKey);
      return new ContentReader();
   }

   public ContentReader decrypt(String secret)
   {
      EncryptionMethod enc = header.getEncryptionMethod();
      if (enc == null) throw new IllegalStateException(Messages.MESSAGES.encryptionMethodWasNull());
      MessageDigest digest = enc.createSecretDigester();
      byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
      return decrypt(hash);

   }

   public ContentReader decrypt(byte[] secret)
   {
      SecretKey key = new SecretKeySpec(secret, "AES");
      return decrypt(key);

   }

   public ContentReader decrypt(SecretKey key)
   {
      Algorithm algorithm = header.getAlgorithm();
      if (algorithm == null) throw new IllegalStateException(Messages.MESSAGES.algorithmWasNull());
      if (algorithm != Algorithm.dir) throw new IllegalStateException(Messages.MESSAGES.notEncryptedWithDirAlgorithm());
      EncryptionMethod enc = header.getEncryptionMethod();
      if (enc == null) throw new IllegalStateException(Messages.MESSAGES.encryptionMethodWasNull());
      rawContent = DirectDecrypter.decrypt(key, header, encodedHeader, null, encodedIv, encodedContent, encodedAuthTag);
      return new ContentReader();
   }

   public class ContentReader
   {
      public byte[] getRawContent()
      {
         return rawContent;
      }
      /**
       * Defaults to '*' if there is no cty header.
       *
       * @param <T> type
       * @param type type class
       * @return read entity of type T
       */
      public <T> T readContent(Class<T> type)
      {
         MediaType mediaType = MediaType.WILDCARD_TYPE;
         if (header.getContentType() != null)
         {
            mediaType = MediaType.valueOf(header.getContentType());
         }
         return (T) readContent(type, null, null, mediaType);

      }

      public <T> T readContent(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType)
      {
         Providers tmp = providers;
         if (tmp == null) tmp = ResteasyProviderFactory.newInstance();
         MessageBodyReader<T> reader = tmp.getMessageBodyReader(type, genericType, annotations, mediaType);
         if (reader == null) throw new RuntimeException(Messages.MESSAGES.unableToFindReaderForContentType());

         try
         {
            ByteArrayInputStream bais = new ByteArrayInputStream(rawContent);
            return reader.readFrom(type, genericType, annotations, mediaType, new MultivaluedHashMap<String, String>(), bais);
         }
         catch (Exception e)
         {
            throw new RuntimeException(e);
         }
      }
   }


}
