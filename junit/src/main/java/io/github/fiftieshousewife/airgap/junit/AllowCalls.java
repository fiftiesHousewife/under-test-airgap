package io.github.fiftieshousewife.airgap.junit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Allows a test, or every test in a class, to reach the stand-ins it relies on, for example
 * {@code @AllowCalls("localhost:8089")} for a WireMock server on another machine name, or
 * {@code @AllowCalls("process:git")}. Entries take the same forms as the agent's {@code allow} setting:
 * {@code host}, {@code host:port}, {@code process:name} or {@code native:name}. Allowed calls are still reported.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtendWith(AllowCallsExtension.class)
public @interface AllowCalls {

    /** @return the stand-ins the test may reach */
    String[] value();
}
