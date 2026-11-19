package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that covers one row of a pairwise-covering array of parameter values (CTAL-TA).
 *
 * <p>Most defects need only two parameters to interact. Pairwise covers every pair with a small fraction of all
 * combinations.
 *
 * <p>Adds the JUnit tag {@code tech:pairwise}. The tag selects tests ({@code -Dgroups=tech:pairwise}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:pairwise")
public @interface Pairwise {
}
