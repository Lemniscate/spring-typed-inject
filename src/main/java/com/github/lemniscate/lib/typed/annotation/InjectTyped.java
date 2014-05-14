package com.github.lemniscate.lib.typed.annotation;

import java.lang.annotation.*;

@Target( {ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectTyped {

    String[] value();

    /**
     * Used to denote that the field names in "value" should be used to lookup the matching types on
     * the object that injection is taking place on;
     */
    boolean reverseLookupIdenticalFields() default true;

    /**
     * The fields on the CALLING object (aka the one being injected) to match type arguments with;
     * only used in scenarios where you don't have concrete types available from the class signature.
     */
    String[] reverseLookupFields() default {};

    boolean required() default true;
}
