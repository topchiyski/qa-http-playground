package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test whose input comes from a fault attack list (CTFL v4.0, 4.4.1). The test names the fault it
 * guesses at.
 *
 * <p>Some bugs are common enough to look for on purpose. Naming the guessed fault keeps the test honest about why
 * it exists.
 *
 * <p>Adds the JUnit tag {@code tech:error-guessing}. The tag selects tests ({@code -Dgroups=tech:error-guessing}) and
 * feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:error-guessing")
public @interface ErrorGuessing {
}
