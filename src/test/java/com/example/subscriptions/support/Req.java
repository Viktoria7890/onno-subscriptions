package com.example.subscriptions.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Трассировка: какое требование из ТЗ проверяет тест. Идентификаторы и формулировки — в
 * {@code TRACEABILITY.md}; {@link com.example.subscriptions.TraceabilityTest} проверяет, что
 * каждое требование покрыто хотя бы одним тестом.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Req.List.class)
public @interface Req {

    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        Req[] value();
    }
}
