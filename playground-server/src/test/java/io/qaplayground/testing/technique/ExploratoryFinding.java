package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a regression test for a defect found in an exploratory session (CTFL v4.0, 4.4.2). Name the session
 * sheet.
 *
 * <p>Exploration finds the surprises. This test makes sure each surprise is found only once.
 *
 * <p>Adds the JUnit tag {@code tech:exploratory}. The tag selects tests ({@code -Dgroups=tech:exploratory}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:exploratory")
public @interface ExploratoryFinding {
}
