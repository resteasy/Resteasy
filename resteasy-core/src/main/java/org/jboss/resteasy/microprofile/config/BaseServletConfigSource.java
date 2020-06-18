package org.jboss.resteasy.microprofile.config;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * I know this implementation is weird, but we do this for two reasons
 * 1. So that resteasy doesn't require servlet
 * 2. Because Graal VM will barf with an unhandled reference if we reference servlet classes directly
 *
 */
public class BaseServletConfigSource {
    protected ConfigSource source;
    protected final boolean available;
    protected final int defaultOrdinal;
    private final String name;

    public BaseServletConfigSource(final boolean available, final Class<?> sourceClass, final int defaultOrdinal) {
        this.available = available;
        this.defaultOrdinal = defaultOrdinal;
        if (available) {
            try {
                source = (ConfigSource)sourceClass.newInstance();
                name = source.getName();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            name = toString();
        }
    }

    public Map<String, String> getProperties() {
       if (!available) {
          return Collections.<String, String>emptyMap();
       }
       return source.getProperties();
    }

    public Set<String> getPropertyNames() {
        return getProperties().keySet();
    }

    public int getOrdinal() {
       if (!available) {
          return this.defaultOrdinal;
       }
       return getOrdinal(source, defaultOrdinal);
   }

    public String getValue(String propertyName) {
       if (!available) {
          return null;
       }
       return source.getValue(propertyName);
    }

    public String getName() {
       return name;
    }

    public static int getOrdinal(ConfigSource configSource, int defaultValue) {
       String configOrdinal = configSource.getValue(ConfigSource.CONFIG_ORDINAL);
       if(configOrdinal != null) {
           try {
               return Integer.parseInt(configOrdinal);
           }
           catch (NumberFormatException ignored) {

           }
       }
       return defaultValue;
   }

}
