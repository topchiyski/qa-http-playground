package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that covers one rule, one column, of a decision table of conditions and actions
 * (CTFL v4.0, 4.2.3).
 *
 * <p>When several conditions decide the outcome together, a table is the surest way to know no combination was
 * forgotten.
 *
 * <p>Adds the JUnit tag {@code tech:decision-table}. The tag selects tests ({@code -Dgroups=tech:decision-table}) and
 * feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:decision-table")
public @interface DecisionTable {
}
