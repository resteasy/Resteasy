package org.jboss.resteasy.plugins.providers.jsonb;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AlternativeDeserializeAs {
    Class from();
    Class to();
}
