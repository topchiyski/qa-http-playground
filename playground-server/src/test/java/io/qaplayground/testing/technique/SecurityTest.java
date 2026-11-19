package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a negative test against a security risk in docs/testing/RISK-REGISTER.md.
 *
 * <p>This is a test type, not a design technique: it says what the test protects, not how it was derived.
 * Adds the JUnit tag {@code type:security}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("type:security")
public @interface SecurityTest {
}
