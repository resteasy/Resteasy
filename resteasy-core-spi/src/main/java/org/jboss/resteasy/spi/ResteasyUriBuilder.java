package org.jboss.resteasy.spi;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.RuntimeDelegate;

public abstract class ResteasyUriBuilder extends UriBuilder
{
   public abstract UriBuilder clone();

   public static final Pattern opaqueUri = Pattern.compile("^([^:/?#{]+):([^/].*)");
   public static final Pattern hierarchicalUri = Pattern.compile("^(([^:/?#{]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

   public static boolean compare(String s1, String s2)
   {
      if (s1 == s2) return true;
      if (s1 == null || s2 == null) return false;
      return s1.equals(s2);
   }

   public static URI relativize(URI from, URI to)
   {
      if (!compare(from.getScheme(), to.getScheme())) return to;
      if (!compare(from.getHost(), to.getHost())) return to;
      if (from.getPort() != to.getPort()) return to;
      if (from.getPath() == null && to.getPath() == null) return URI.create("");
      else if (from.getPath() == null) return URI.create(to.getPath());
      else if (to.getPath() == null) return to;


      String fromPath = from.getPath();
      if (fromPath.startsWith("/")) fromPath = fromPath.substring(1);
      String[] fsplit = fromPath.split("/");
      String toPath = to.getPath();
      if (toPath.startsWith("/")) toPath = toPath.substring(1);
      String[] tsplit = toPath.split("/");

      int f = 0;

      for (;f < fsplit.length && f < tsplit.length; f++)
      {
         if (!fsplit[f].equals(tsplit[f])) break;
      }

      UriBuilder builder = UriBuilder.fromPath("");
      for (int i = f; i < fsplit.length; i++) builder.path("..");
      for (int i = f; i < tsplit.length; i++) builder.path(tsplit[i]);
      return builder.build();
   }

   /**
    * You may put path parameters anywhere within the uriTemplate except port.
    *
    * @param uriTemplate uri template
    * @return uri builder
    */
   public static ResteasyUriBuilder fromTemplate(String uriTemplate)
   {
      ResteasyUriBuilder impl = (ResteasyUriBuilder)RuntimeDelegate.getInstance().createUriBuilder();
      impl.uriTemplate(uriTemplate);
      return impl;
   }

   /**
    * You may put path parameters anywhere within the uriTemplate except port.
    *
    * @param uriTemplate uri template
    * @return uri builder
    */
   public abstract UriBuilder uriTemplate(CharSequence uriTemplate);

   public abstract UriBuilder uriFromCharSequence(CharSequence uriTemplate) throws IllegalArgumentException;

   /**
    * Only replace path params in path of URI.  This changes state of URIBuilder.
    *
    * @param name parameter name
    * @param value parameter value
    * @param isEncoded encoded flag
    * @return uri builder
    */
   public abstract UriBuilder substitutePathParam(String name, Object value, boolean isEncoded);


   /**
    * Return a unique order list of path params.
    *
    * @return list of path parameters
    */
   public abstract List<String> getPathParamNamesInDeclarationOrder();

   /**
    * Called by ClientRequest.getUri() to add a query parameter for
    * {@code @QueryParam} parameters. We do not use UriBuilder.queryParam()
    * because
    * <ul>
    * <li> queryParam() supports URI template processing and this method must
    * always encode braces (for parameter substitution is not possible for
    * {@code @QueryParam} parameters).
    * <li> queryParam() supports "contextual URI encoding" (i.e., it does not
    * encode {@code %} characters that are followed by two hex characters).
    * The JavaDoc for {@code @QueryParam.value()} explicitly states that
    * the value is specified in decoded format and that "any percent
    * encoded literals within the value will not be decoded and will
    * instead be treated as literal text". This means that it is an
    * explicit bug to perform contextual URI encoding of this method's
    * name parameter; hence, we must always encode said parameter. This
    * method also foregoes contextual URI encoding on this method's values
    * parameter because it represents arbitrary data passed to a
    * {@code QueryParam} parameter of a client proxy (since the client
    * proxy is nothing more than a transport layer, it should not be
    * "interpreting" such data; instead, it should faithfully transmit
    * this data over the wire).
    * </ul>
    *
    * @param name  the name of the query parameter.
    * @param isValueEncoded is the value already encoded?
    * @param values the value(s) of the query parameter.
    * @return Returns this instance to allow call chaining.
    */
   public abstract UriBuilder clientQueryParam(String name, boolean isValueEncoded, Object... values) throws IllegalArgumentException;

   public abstract String getHost();

   public abstract String getScheme();

   public abstract int getPort();

   public abstract String getUserInfo();

   public abstract String getPath();

   public abstract String getQuery();

   public abstract String getFragment();

}
