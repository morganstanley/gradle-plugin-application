package com.ms.gradle.application;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for methods that directly delegate the call to the same method of another object (that implements
 * the same interface). Such methods don't have to be covered by tests.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface GeneratedDelegation {}
