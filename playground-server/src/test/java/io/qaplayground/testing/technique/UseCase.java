package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that follows the main or an alternative flow of a user journey (CTAL-TA).
 *
 * <p>Users follow paths, not single requests. These tests follow the same paths.
 *
 * <p>Adds the JUnit tag {@code tech:use-case}. The tag selects tests ({@code -Dgroups=tech:use-case}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:use-case")
public @interface UseCase {
}
