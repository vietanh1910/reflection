package com.example.demo_reflection.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface MyEntity {
    String tableName();
}
