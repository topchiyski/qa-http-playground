package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that covers one item of a checklist, here the HTTP conformance checklist (CTFL v4.0, 4.4.3).
 *
 * <p>A checklist turns experience into something every route gets, every time, without anyone having to remember
 * it.
 *
 * <p>Adds the JUnit tag {@code tech:checklist}. The tag selects tests ({@code -Dgroups=tech:checklist}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:checklist")
public @interface ChecklistBased {
}
