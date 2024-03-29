package com.aj.simple;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PropertyShow {

    String obj() default "";
    String meth() default "";
    String suffix() default "Name";
    boolean enable() default true;
}
