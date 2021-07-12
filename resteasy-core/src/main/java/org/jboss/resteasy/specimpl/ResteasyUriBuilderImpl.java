package org.jboss.resteasy.specimpl;

import org.jboss.resteasy.resteasy_jaxrs.i18n.Messages;
import org.jboss.resteasy.spi.ResteasyUriBuilder;
import org.jboss.resteasy.util.Encode;
import org.jboss.resteasy.util.PathHelper;

import javax.ws.rs.Path;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class ResteasyUriBuilderImpl extends ResteasyUriBuilder
{

   private static final class URITemplateParametersMap extends HashMap<String, Object> {

      private final Object[] parameterValues;
      private int index;

      private URITemplateParametersMap(final Object...parameterValues) {
         this.parameterValues=parameterValues;
      }

      @Override
      public Object get(Object key) {
         Object object = null;
         if ( !super.containsKey(key) && this.index != this.parameterValues.length) {
            object = this.parameterValues[this.index++];
            super.put((String) key, object);
         }else{
            object = super.get(key);
         }
         return object;
      }

      @Override
      public boolean containsKey(Object key) {
         boolean containsKey = super.containsKey(key);
         if (!containsKey && this.index != this.parameterValues.length) {
            super.put((String) key, this.parameterValues[this.index++]);
            containsKey = true;
         }
         return containsKey;
      }

   }

   private String host;
   private String scheme;
   private int port = -1;

   private String userInfo;
   private String path;
   private String query;
   private String fragment;
   private String ssp;
   private String authority;

   public ResteasyUriBuilderImpl(){}
   /*
    * Constructor allows subclass to clone retaining values.
    */
   public ResteasyUriBuilderImpl(final String host, final String scheme, final int port,
                                 final String userInfo, final String path, final String query,
                                 final String fragment, final String ssp, final String authority) {
      this.host = host;
      this.scheme = scheme;
      this.port = port;
      this.userInfo = userInfo;
      this.path = path;
      this.query = query;
      this.fragment = fragment;
      this.ssp = ssp;
      this.authority = authority;
   }

   @Override
   public UriBuilder clone()
   {
      ResteasyUriBuilderImpl impl = new ResteasyUriBuilderImpl();
      impl.host = host;
      impl.scheme = scheme;
      impl.port = port;
      impl.userInfo = userInfo;
      impl.path = path;
      impl.query = query;
      impl.fragment = fragment;
      impl.ssp = ssp;
      impl.authority = authority;

      return impl;
   }

   private static final Pattern hostPortPattern = Pattern.compile("([^/:]+):(\\d+)");
   private static final Pattern squareHostBrackets = Pattern.compile( "(\\[(([0-9A-Fa-f]{0,4}:){2,7})([0-9A-Fa-f]{0,4})%?.*\\]):(\\d+)" );

   /**
    * You may put path parameters anywhere within the uriTemplate except port.
    *
    * @param uriTemplate uri template
    * @return uri builder
    */
   @Override
   public UriBuilder uriTemplate(CharSequence uriTemplate)
   {
      if (uriTemplate == null) throw new IllegalArgumentException(Messages.MESSAGES.uriTemplateParameterNull());
      Matcher opaque = opaqueUri.matcher(uriTemplate);
      if (opaque.matches())
      {
         this.authority = null;
         this.host = null;
         this.port = -1;
         this.userInfo = null;
         this.query = null;
         this.scheme = opaque.group(1);
         this.ssp = opaque.group(2);
         return this;
      }
      else
      {
         Matcher match = hierarchicalUri.matcher(uriTemplate);
         if (match.matches())
         {
            ssp = null;
            return parseHierarchicalUri(uriTemplate, match);
         }
      }
      throw new IllegalArgumentException(Messages.MESSAGES.illegalUriTemplate(uriTemplate));
   }

   protected UriBuilder parseHierarchicalUri(CharSequence uriTemplate, Matcher match)
   {
      boolean scheme = match.group(2) != null;
      if (scheme) this.scheme = match.group(2);
      String authority = match.group(4);
      if (authority != null)
      {
         this.authority = null;
         String host = match.group(4);
         int at = host.indexOf('@');
         if (at > -1)
         {
            String user = host.substring(0, at);
            host = host.substring(at + 1);
            this. userInfo = user;
         }

         Matcher hostPortMatch = hostPortPattern.matcher(host);
         if (hostPortMatch.matches())
         {
            this.host = hostPortMatch.group(1);
            try
            {
               this.port = Integer.parseInt(hostPortMatch.group(2));
            } catch (NumberFormatException e)
            {
               throw new IllegalArgumentException(Messages.MESSAGES.illegalUriTemplate(uriTemplate), e);
            }
         } else
         {
            if (host.startsWith("["))
            {
               // Must support an IPv6 hostname of format "[::1]" or [0:0:0:0:0:0:0:0]
               // and IPv6 link-local format [fe80::1234%1] [ff08::9abc%interface10]
               Matcher bracketsMatch = squareHostBrackets.matcher(host);
               if (bracketsMatch.matches())
               {
                  host = bracketsMatch.group(1);
                  try
                  {
                     this.port = Integer.parseInt(bracketsMatch.group(5));
                  } catch (NumberFormatException e)
                  {
                     throw new IllegalArgumentException(Messages.MESSAGES.illegalUriTemplate(uriTemplate), e);
                  }
               }
            }
            this.host = host;
         }
      }
      if (match.group(5) != null)
      {
         String group = match.group(5);
         if (!scheme && !"".equals(group) && !group.startsWith("/") && group.indexOf(':') > -1 &&
            group.indexOf('/') > -1 && group.indexOf(':') < group.indexOf('/'))
            throw new IllegalArgumentException(Messages.MESSAGES.illegalUriTemplate(uriTemplate));
         if (!"".equals(group)) replacePath(group);
      }
      if (match.group(7) != null) replaceQuery(match.group(7));
      if (match.group(9) != null) fragment(match.group(9));
      return this;
   }

   @Override
   public UriBuilder uri(String uriTemplate) throws IllegalArgumentException
   {
      return uriTemplate(uriTemplate);
   }

   @Override
   public UriBuilder uriFromCharSequence(CharSequence uriTemplate) throws IllegalArgumentException
   {
      return uriTemplate(uriTemplate);
   }

   @Override
   public UriBuilder uri(URI uri) throws IllegalArgumentException
   {
      if (uri == null) throw new IllegalArgumentException(Messages.MESSAGES.uriNull());

      if (uri.getRawFragment() != null) fragment = uri.getRawFragment();

      if (uri.isOpaque())
      {
         scheme = uri.getScheme();
         ssp = uri.getRawSchemeSpecificPart();
         return this;
      }

      if (uri.getScheme() == null)
      {
         if (ssp != null)
         {
            if (uri.getRawSchemeSpecificPart() != null)
            {
               ssp = uri.getRawSchemeSpecificPart();
               return this;
            }
         }
      }
      else
      {
         scheme = uri.getScheme();
      }

      ssp = null;
      if (uri.getRawAuthority() != null) {
         if (uri.getRawUserInfo() == null && uri.getHost() == null && uri.getPort() == -1) {
            authority = uri.getRawAuthority();
            userInfo = null;
            host = null;
            port = -1;
         } else {
            authority = null;
            if (uri.getRawUserInfo() != null) {
               userInfo = uri.getRawUserInfo();
            }
            if (uri.getHost() != null) {
               host = uri.getHost();
            }
            if (uri.getPort() != -1) {
               port = uri.getPort();
            }
         }
      }

      if (uri.getRawPath() != null && uri.getRawPath().length() > 0) {
         path = uri.getRawPath();
      }
      if (uri.getRawQuery() != null && uri.getRawQuery().length() > 0) {
         query = uri.getRawQuery();
      }

      return this;
   }

   @Override
   public UriBuilder scheme(String scheme) throws IllegalArgumentException
   {
      this.scheme = scheme;
      return this;
   }

   @Override
   public UriBuilder schemeSpecificPart(String ssp) throws IllegalArgumentException
   {
      if (ssp == null) throw new IllegalArgumentException(Messages.MESSAGES.schemeSpecificPartNull());

      StringBuilder sb = new StringBuilder();
      if (scheme != null) sb.append(scheme).append(':');
      if (ssp != null)
         sb.append(ssp);
      if (fragment != null && fragment.length() > 0) sb.append('#').append(fragment);
      URI uri = URI.create(sb.toString());

      if (uri.getRawSchemeSpecificPart() != null && uri.getRawPath() == null)
      {
         this.ssp = uri.getRawSchemeSpecificPart();
      }
      else
      {
         this.ssp = null;
         userInfo = uri.getRawUserInfo();
         host = uri.getHost();
         port = uri.getPort();
         path = uri.getRawPath();
         query = uri.getRawQuery();

      }
      return this;

   }

   @Override
   public UriBuilder userInfo(String ui)
   {
      this.userInfo = ui;
      return this;
   }

   @Override
   public UriBuilder host(String host) throws IllegalArgumentException
   {
      if (host != null && host.equals("")) throw new IllegalArgumentException(Messages.MESSAGES.invalidHost());
      this.host = host;
      return this;
   }

   @Override
   public UriBuilder port(int port) throws IllegalArgumentException
   {
      if (port < -1) throw new IllegalArgumentException(Messages.MESSAGES.invalidPort());
      this.port = port;
      return this;
   }

   protected static String paths(boolean encode, String basePath, String... segments)
   {
      String path = basePath;
      if (path == null) path = "";
      for (String segment : segments)
      {
         if ("".equals(segment)) continue;
         if (path.endsWith("/"))
         {
            if (segment.startsWith("/"))
            {
               segment = segment.substring(1);
               if ("".equals(segment)) continue;
            }
            if (encode) segment = Encode.encodePath(segment);
            path += segment;
         }
         else
         {
            if (encode) segment = Encode.encodePath(segment);
            if ("".equals(path))
            {
               path = segment;
            }
            else if (segment.startsWith("/"))
            {
               path += segment;
            }
            else
            {
               path += "/" + segment;
            }
         }

      }
      return path;
   }

   @Override
   public UriBuilder path(String segment) throws IllegalArgumentException
   {
      if (segment == null) throw new IllegalArgumentException(Messages.MESSAGES.pathNull());
      path = paths(true, path, segment);
      return this;
   }

   @SuppressWarnings("unchecked")
   @Override
   public UriBuilder path(@SuppressWarnings("rawtypes") Class resource) throws IllegalArgumentException
   {
      if (resource == null) throw new IllegalArgumentException(Messages.MESSAGES.pathNull());
      Path ann = (Path) resource.getAnnotation(Path.class);
      if (ann != null)
      {
         String[] segments = new String[]{ann.value()};
         path = paths(true, path, segments);
      }
      else
      {
         throw new IllegalArgumentException(Messages.MESSAGES.classMustBeAnnotatedWithPath());
      }
      return this;
   }

   @Override
   public UriBuilder path(@SuppressWarnings("rawtypes")Class resource, String method) throws IllegalArgumentException
   {
      if (resource == null) throw new IllegalArgumentException(Messages.MESSAGES.resourceNull());
      if (method == null) throw new IllegalArgumentException(Messages.MESSAGES.methodNull());
      Method theMethod = null;
      for (Method m : resource.getMethods())
      {
         if (m.getName().equals(method))
         {
            if (theMethod != null && m.isAnnotationPresent(Path.class))
            {
               throw new IllegalArgumentException(Messages.MESSAGES.twoMethodsSameName(method));
            }
            if (m.isAnnotationPresent(Path.class)) theMethod = m;
         }
      }
      if (theMethod == null) throw new IllegalArgumentException(Messages.MESSAGES.noPublicPathAnnotatedMethod(resource.getName(), method));
      return path(theMethod);
   }

   @Override
   public UriBuilder path(Method method) throws IllegalArgumentException
   {
      if (method == null)
      {
         throw new IllegalArgumentException(Messages.MESSAGES.methodNull());
      }
      Path ann = method.getAnnotation(Path.class);
      if (ann != null)
      {
         path = paths(true, path, ann.value());
      }
      else
      {
         throw new IllegalArgumentException(Messages.MESSAGES.methodNotAnnotatedWithPath());
      }
      return this;
   }

   @Override
   public UriBuilder replaceMatrix(String matrix) throws IllegalArgumentException
   {
      if (matrix == null) matrix = "";
      if (!matrix.startsWith(";")) matrix = ";" + matrix;
      matrix = Encode.encodePath(matrix);
      if (path == null)
      {
         path = matrix;
      }
      else
      {
         int start = path.lastIndexOf('/');
         if (start < 0) start = 0;
         int matrixIndex = path.indexOf(';', start);
         if (matrixIndex > -1) path = path.substring(0, matrixIndex) + matrix;
         else path += matrix;

      }
      return this;
   }

   @Override
   public UriBuilder replaceQuery(String query) throws IllegalArgumentException
   {
      if (query == null || query.length() == 0)
      {
         this.query = null;
         return this;
      }
      this.query = Encode.encodeQueryString(query);
      return this;
   }

   public UriBuilder replaceQueryNoEncoding(String query) {
      this.query = query;
      return this;
   }

   @Override
   public UriBuilder fragment(String fragment) throws IllegalArgumentException
   {
      if (fragment == null)
      {
         this.fragment = null;
         return this;
      }
      this.fragment = Encode.encodeFragment(fragment);
      return this;
   }

   /**
    * Only replace path params in path of URI.  This changes state of URIBuilder.
    *
    * @param name parameter name
    * @param value parameter value
    * @param isEncoded encoded flag
    * @return uri builder
    */
   @Override
   public UriBuilder substitutePathParam(String name, Object value, boolean isEncoded)
   {
      if (path != null)
      {
         StringBuilder builder = new StringBuilder();
         replacePathParameter(name, value.toString(), isEncoded, path, builder, false);
         path = builder.toString();
      }
      return this;
   }

   @Override
   public URI buildFromMap(Map<String, ? extends Object> values) throws IllegalArgumentException, UriBuilderException
   {
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());
      return buildUriFromMap(values, false, true);
   }

   @Override
   public URI buildFromEncodedMap(Map<String, ? extends Object> values) throws IllegalArgumentException, UriBuilderException
   {
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());
      return buildUriFromMap(values, true, false);
   }

   @Override
   public URI buildFromMap(Map<String, ?> values, boolean encodeSlashInPath) throws IllegalArgumentException, UriBuilderException
   {
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());
      return buildUriFromMap(values, false, encodeSlashInPath);
   }

   protected URI buildUriFromMap(Map<String, ? extends Object> paramMap, boolean fromEncodedMap, boolean encodeSlash) throws IllegalArgumentException, UriBuilderException
   {
      String buf = buildString(paramMap, fromEncodedMap, false, encodeSlash);
      try
      {
         return URI.create(buf);
      }
      catch (Exception e)
      {
         throw new RuntimeException(Messages.MESSAGES.failedToCreateUri(buf), e);
      }
   }

   private String buildString(Map<String, ? extends Object> paramMap, boolean fromEncodedMap, boolean isTemplate, boolean encodeSlash)
   {
      return buildCharSequence(paramMap, fromEncodedMap, isTemplate, encodeSlash).toString();
   }

   private CharSequence buildCharSequence(Map<String, ? extends Object> paramMap, boolean fromEncodedMap, boolean isTemplate, boolean encodeSlash)
   {
      StringBuilder builder = new StringBuilder();

      if (scheme != null) replaceParameter(paramMap, fromEncodedMap, isTemplate, scheme, builder, encodeSlash).append(":");
      if (ssp != null)
      {
         builder.append(ssp);
      }
      else if (userInfo != null || host != null || port != -1)
      {
         builder.append("//");
         if (userInfo != null) replaceParameter(paramMap, fromEncodedMap, isTemplate, userInfo, builder, encodeSlash).append("@");
         if (host != null)
         {
            if ("".equals(host)) throw new UriBuilderException(Messages.MESSAGES.emptyHostName());
            replaceParameter(paramMap, fromEncodedMap, isTemplate, host, builder, encodeSlash);
         }
         if (port != -1) builder.append(":").append(Integer.toString(port));
      }
      else if (authority != null)
      {
         builder.append("//");
         replaceParameter(paramMap, fromEncodedMap, isTemplate, authority, builder, encodeSlash);
      }
      if (path != null)
      {
         StringBuilder tmp = new StringBuilder();
         replaceParameter(paramMap, fromEncodedMap, isTemplate, path, tmp, encodeSlash);
         if (userInfo != null || host != null)
         {
            if (tmp.length() > 0 && tmp.charAt(0) !=  '/') builder.append("/");
         }
         builder.append(tmp);
      }
      if (query != null)
      {
         builder.append("?");
         replaceQueryStringParameter(paramMap, fromEncodedMap, isTemplate, query, builder);
      }
      if (fragment != null)
      {
         builder.append("#");
         replaceParameter(paramMap, fromEncodedMap, isTemplate, fragment, builder, encodeSlash);
      }
      return builder;
   }

   protected StringBuilder replacePathParameter(String name, String value, boolean isEncoded, String string, StringBuilder builder, boolean encodeSlash)
   {
      if (string.indexOf('{') == -1)
      {
         return builder.append(string);
      }
      Matcher matcher = createUriParamMatcher(string);
      int start = 0;
      while (matcher.find())
      {
         String param = matcher.group(1);
         if (!param.equals(name)) continue;
         builder.append(string, start, matcher.start());
         if (!isEncoded)
         {
            if (encodeSlash) value = Encode.encodePath(value);
            else value = Encode.encodePathSegment(value);

         }
         else
         {
            value = Encode.encodeNonCodes(value);
         }
         builder.append(value);
         start = matcher.end();
      }
      builder.append(string, start, string.length());
      return builder;
   }

   public static Matcher createUriParamMatcher(String string)
   {
      Matcher matcher = PathHelper.URI_PARAM_PATTERN.matcher(PathHelper.replaceEnclosedCurlyBracesCS(string));
      return matcher;
   }

   protected StringBuilder replaceParameter(Map<String, ? extends Object> paramMap, boolean fromEncodedMap, boolean isTemplate, String string, StringBuilder builder, boolean encodeSlash)
   {
      if (string.indexOf('{') == -1)
      {
         return builder.append(string);
      }
      Matcher matcher = createUriParamMatcher(string);
      int start = 0;
      while (matcher.find())
      {
         builder.append(string, start, matcher.start());
         String param = matcher.group(1);
         boolean containsValueForParam = paramMap.containsKey(param);
         if (!containsValueForParam)
         {
            if (isTemplate)
            {
               builder.append(matcher.group());
               start = matcher.end();
               continue;
            }
            throw new IllegalArgumentException(Messages.MESSAGES.pathParameterNotProvided(param));
         }
         Object value = paramMap.get(param);
         String stringValue = value != null ? value.toString() : null;
         if (stringValue != null)
         {
            if (!fromEncodedMap)
            {
               if (encodeSlash) stringValue = Encode.encodePathSegmentAsIs(stringValue);
               else stringValue = Encode.encodePathAsIs(stringValue);
            }
            else
            {
               if (encodeSlash) stringValue = Encode.encodePathSegmentSaveEncodings(stringValue);
               else stringValue = Encode.encodePathSaveEncodings(stringValue);
            }
            builder.append(stringValue);
            start = matcher.end();
         }
         else
         {
            throw new IllegalArgumentException(Messages.MESSAGES.templateParameterNull(param));
         }
      }
      builder.append(string, start, string.length());
      return builder;
   }

   protected StringBuilder replaceQueryStringParameter(Map<String, ? extends Object> paramMap, boolean fromEncodedMap, boolean isTemplate, String string, StringBuilder builder)
   {
      if (string.indexOf('{') == -1)
      {
         return builder.append(string);
      }
      Matcher matcher = createUriParamMatcher(string);
      int start = 0;
      while (matcher.find())
      {
         builder.append(string, start, matcher.start());
         String param = matcher.group(1);
         boolean containsValueForParam = paramMap.containsKey(param);
         if (!containsValueForParam)
         {
            if (isTemplate)
            {
               builder.append(matcher.group());
               start = matcher.end();
               continue;
            }
            throw new IllegalArgumentException(Messages.MESSAGES.pathParameterNotProvided(param));
         }
         Object value = paramMap.get(param);
         String stringValue = value != null ? value.toString() : null;
         if (stringValue != null)
         {
            if (!fromEncodedMap)
            {
               stringValue = Encode.encodeQueryParamAsIs(stringValue);
            }
            else
            {
               stringValue = Encode.encodeQueryParamSaveEncodings(stringValue);
            }
            builder.append(stringValue);
            start = matcher.end();
         }
         else
         {
            throw new IllegalArgumentException(Messages.MESSAGES.templateParameterNull(param));
         }
      }
      builder.append(string, start, string.length());
      return builder;
   }

   /**
    * Return a unique order list of path params.
    *
    * @return list of path parameters
    */
   @Override
   public List<String> getPathParamNamesInDeclarationOrder()
   {
      List<String> params = new ArrayList<String>();
      HashSet<String> set = new HashSet<String>();
      if (scheme != null) addToPathParamList(params, set, scheme);
      if (userInfo != null) addToPathParamList(params, set, userInfo);
      if (host != null) addToPathParamList(params, set, host);
      if (path != null) addToPathParamList(params, set, path);
      if (query != null) addToPathParamList(params, set, query);
      if (fragment != null) addToPathParamList(params, set, fragment);

      return params;
   }

   private void addToPathParamList(List<String> params, HashSet<String> set, String string)
   {
      Matcher matcher = PathHelper.URI_PARAM_PATTERN.matcher(PathHelper.replaceEnclosedCurlyBracesCS(string));
      while (matcher.find())
      {
         String param = matcher.group(1);
         if (set.contains(param)) continue;
         else
         {
            set.add(param);
            params.add(param);
         }
      }
   }

   @Override
   public URI build(Object... values) throws IllegalArgumentException, UriBuilderException
   {
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());
      return buildFromValues(true, false, values);
   }

   protected URI buildFromValues(boolean encodeSlash, boolean encoded, Object... values)
   {
      String buf = null;
      try
      {
         buf = buildString(new URITemplateParametersMap(values), encoded, false, encodeSlash);
         return new URI(buf);
         //return URI.create(buf);
      }
      catch (IllegalArgumentException iae)
      {
         throw iae;
      }
      catch (Exception e)
      {
         throw new UriBuilderException(Messages.MESSAGES.failedToCreateUri(buf), e);
      }
   }

   @Override
   public UriBuilder matrixParam(String name, Object... values) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParameterNull());
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());
      if (path == null) path = "";
      for (Object val : values)
      {
         if (val == null) throw new IllegalArgumentException(Messages.MESSAGES.nullValue());
         path += ";" + Encode.encodeMatrixParam(name) + "=" + Encode.encodeMatrixParam(val.toString());
      }
      return this;
   }

   private static final Pattern PARAM_REPLACEMENT = Pattern.compile("_resteasy_uri_parameter");

   @Override
   public UriBuilder replaceMatrixParam(String name, Object... values) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParameterNull());
      if (path == null)
      {
         if (values != null && values.length > 0) return matrixParam(name, values);
         return this;
      }

      // remove all path param expressions so we don't accidentally start replacing within a regular expression
      ArrayList<String> pathParams = new ArrayList<String>();
      boolean foundParam = false;

      CharSequence pathWithoutEnclosedCurlyBraces = PathHelper.replaceEnclosedCurlyBracesCS(this.path);
      Matcher matcher = PathHelper.URI_TEMPLATE_PATTERN.matcher(pathWithoutEnclosedCurlyBraces);
      StringBuilder newSegment = new StringBuilder();
      int from = 0;
      while (matcher.find())
      {
         newSegment.append(pathWithoutEnclosedCurlyBraces, from, matcher.start());
         foundParam = true;
         String group = matcher.group();
         pathParams.add(PathHelper.recoverEnclosedCurlyBraces(group));
         newSegment.append("_resteasy_uri_parameter");
         from = matcher.end();
      }
      newSegment.append(pathWithoutEnclosedCurlyBraces, from, pathWithoutEnclosedCurlyBraces.length());
      path = newSegment.toString();

      // Find last path segment
      int start = path.lastIndexOf('/');
      if (start < 0) start = 0;

      int matrixIndex = path.indexOf(';', start);
      if (matrixIndex > -1)
      {

         String matrixParams = path.substring(matrixIndex + 1);
         path = path.substring(0, matrixIndex);
         MultivaluedMapImpl<String, String> map = new MultivaluedMapImpl<String, String>();

         String[] params = matrixParams.split(";");
         for (String param : params)
         {
            int idx = param.indexOf('=');
            if (idx < 0)
            {
               map.add(param, null);
            }
            else
            {
               String theName = param.substring(0, idx);
               String value = "";
               if (idx + 1 < param.length()) value = param.substring(idx + 1);
               map.add(theName, value);
            }
         }
         map.remove(name);
         for (String theName : map.keySet())
         {
            List<String> vals = map.get(theName);
            for (Object val : vals)
            {
               if (val == null) path += ";" + theName;
               else path += ";" + theName + "=" + val.toString();
            }
         }
      }
      if (values != null && values.length > 0) matrixParam(name, values);

      // put back all path param expressions
      if (foundParam)
      {
         matcher = PARAM_REPLACEMENT.matcher(path);
         newSegment = new StringBuilder();
         int i = 0;
         from = 0;
         while (matcher.find())
         {
            newSegment.append(this.path, from, matcher.start());
            newSegment.append(pathParams.get(i++));
            from = matcher.end();
         }
         newSegment.append(this.path, from, this.path.length());
         path = newSegment.toString();
      }
      return this;
   }

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
   @Override
   public UriBuilder clientQueryParam(String name, boolean isValueEncoded, Object... values) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParameterNull());
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());

      String prefix = query != null ? "&" : "";
      StringBuilder sb = query != null ? new StringBuilder(query) : new StringBuilder();

      for (Object value : values)
      {
         if (value == null) throw new IllegalArgumentException(Messages.MESSAGES.passedInValueNull());
         sb.append(prefix)
            .append(Encode.encodeQueryParamAsIs(name))
            .append("=")
            .append(isValueEncoded
                    ? Encode.encodeQueryParamSaveEncodings(value.toString())
                    : Encode.encodeQueryParamAsIs(value.toString()));

         prefix = "&";
      }

      query = sb.toString();
      return this;
   }

   @Override
   public UriBuilder queryParam(String name, Object... values) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParameterNull());
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParameterNull());

      String prefix = query != null ? "&" : "";
      StringBuilder sb = query != null ? new StringBuilder(query) : new StringBuilder();

      for (Object value : values)
      {
         if (value == null) throw new IllegalArgumentException(Messages.MESSAGES.passedInValueNull());
         sb.append(prefix)
            .append(Encode.encodeQueryParam(name))
            .append("=")
            .append(Encode.encodeQueryParam(value.toString()));

         prefix = "&";
      }

      query = sb.toString();
      return this;
   }

   @Override
   public UriBuilder replaceQueryParam(String name, Object... values) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParameterNull());
      if (query == null || query.equals(""))
      {
         if (values != null) return queryParam(name, values);
         return this;
      }

      String[] params = query.split("&");
      query = null;

      String replacedName = Encode.encodeQueryParam(name);


      for (String param : params)
      {
         int pos = param.indexOf('=');
         if (pos >= 0)
         {
            String paramName = param.substring(0, pos);
            if (paramName.equals(replacedName)) continue;
         }
         else
         {
            if (param.equals(replacedName)) continue;
         }
         if (query == null) query = "";
         else query += "&";
         query += param;
      }
      // don't set values if values is null
      if (values == null || values.length == 0) return this;
      return queryParam(name, values);
   }

   @Override
   public String getHost()
   {
      return host;
   }

   @Override
   public String getScheme()
   {
      return scheme;
   }

   @Override
   public int getPort()
   {
      return port;
   }

   @Override
   public String getUserInfo()
   {
      return userInfo;
   }

   @Override
   public String getPath()
   {
      return path;
   }

   @Override
   public String getQuery()
   {
      return query;
   }

   @Override
   public String getFragment()
   {
      return fragment;
   }

   public String getAuthority() {
      return authority;
   }
   public String getSsp() {
      return ssp;
   }

   @Override
   public UriBuilder segment(String... segments) throws IllegalArgumentException
   {
      if (segments == null) throw new IllegalArgumentException(Messages.MESSAGES.segmentsParameterNull());
      for (String segment : segments)
      {
         if (segment == null) throw new IllegalArgumentException(Messages.MESSAGES.segmentNull());
         path(Encode.encodePathSegment(segment));
      }
      return this;
   }

   @Override
   public URI buildFromEncoded(Object... values) throws IllegalArgumentException, UriBuilderException
   {
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParamIsNull());
      return buildFromValues(false, true, values);
   }

   @Override
   public UriBuilder replacePath(String path)
   {
      if (path == null)
      {
         this.path = null;
         return this;
      }
      this.path = Encode.encodePath(path);
      return this;
   }

   @Override
   public URI build(Object[] values, boolean encodeSlashInPath) throws IllegalArgumentException, UriBuilderException
   {
      if (values == null) throw new IllegalArgumentException(Messages.MESSAGES.valuesParamIsNull());
      return buildFromValues(encodeSlashInPath, false, values);
   }

   @Override
   public String toTemplate()
   {
      return buildString(new HashMap<String, Object>(), true, true, true);
   }

   @Override
   public UriBuilder resolveTemplate(String name, Object value) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParamIsNull());
      if (value == null) throw new IllegalArgumentException(Messages.MESSAGES.valueParamIsNull());
      HashMap<String, Object> vals = new HashMap<String, Object>();
      vals.put(name, value);
      return resolveTemplates(vals);
   }

   @Override
   public UriBuilder resolveTemplates(Map<String, Object> templateValues) throws IllegalArgumentException
   {
      if (templateValues == null) throw new IllegalArgumentException(Messages.MESSAGES.templateValuesParamNull());
      if (templateValues.containsKey(null)) throw new IllegalArgumentException(Messages.MESSAGES.mapKeyNull());
      return uriTemplate(buildCharSequence(templateValues, false, true, true));
   }

   @Override
   public UriBuilder resolveTemplate(String name, Object value, boolean encodeSlashInPath) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParamIsNull());
      if (value == null) throw new IllegalArgumentException(Messages.MESSAGES.valueParamIsNull());
      HashMap<String, Object> vals = new HashMap<String, Object>();
      vals.put(name, value);
      return uriTemplate(buildCharSequence(vals, false, true, encodeSlashInPath));
   }

   @Override
   public UriBuilder resolveTemplateFromEncoded(String name, Object value) throws IllegalArgumentException
   {
      if (name == null) throw new IllegalArgumentException(Messages.MESSAGES.nameParamIsNull());
      if (value == null) throw new IllegalArgumentException(Messages.MESSAGES.valueParamIsNull());
      HashMap<String, Object> vals = new HashMap<String, Object>();
      vals.put(name, value);
      return uriTemplate(buildCharSequence(vals, true, true, true));
   }

   @Override
   public UriBuilder resolveTemplates(Map<String, Object> templateValues, boolean encodeSlashInPath) throws IllegalArgumentException
   {
      if (templateValues == null) throw new IllegalArgumentException(Messages.MESSAGES.templateValuesParamNull());
      if (templateValues.containsKey(null)) throw new IllegalArgumentException(Messages.MESSAGES.mapKeyNull());
      return uriTemplate(buildCharSequence(templateValues, false, true, encodeSlashInPath));
   }

   @Override
   public UriBuilder resolveTemplatesFromEncoded(Map<String, Object> templateValues) throws IllegalArgumentException
   {
      if (templateValues == null) throw new IllegalArgumentException(Messages.MESSAGES.templateValuesParamNull());
      if (templateValues.containsKey(null)) throw new IllegalArgumentException(Messages.MESSAGES.mapKeyNull());
      return uriTemplate(buildCharSequence(templateValues, true, true, true));
   }
}
