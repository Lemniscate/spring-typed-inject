package com.github.lemniscate.lib.typed.annotation;

import java.lang.annotation.*;

@Target( {ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BeanMarker {
}
