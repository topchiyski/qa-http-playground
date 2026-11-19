package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that checks an acceptance criterion written as Given/When/Then before the automation
 * (CTFL v4.0, 4.5.2-4.5.3).
 *
 * <p>Writing the criterion first keeps the test about what the user needs, not about how the page happens to work.
 *
 * <p>Adds the JUnit tag {@code tech:atdd}. The tag selects tests ({@code -Dgroups=tech:atdd}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:atdd")
public @interface AcceptanceTest {
}
