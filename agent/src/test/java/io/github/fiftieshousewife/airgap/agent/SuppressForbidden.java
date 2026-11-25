package io.github.fiftieshousewife.airgap.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Lets a test use an API the forbidden-APIs check refuses, when that API is what the test is about. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface SuppressForbidden {
    /** @return why the API is needed here */
    String value();
}
