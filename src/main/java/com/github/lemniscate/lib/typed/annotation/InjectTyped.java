package com.github.lemniscate.lib.typed.annotation;

import java.lang.annotation.*;

@Target( {ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectTyped {

    String[] value();
    boolean required() default true;
}
