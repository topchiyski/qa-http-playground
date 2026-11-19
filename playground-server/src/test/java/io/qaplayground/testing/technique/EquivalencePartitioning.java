package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that covers one representative value per partition of the input or output domain
 * (CTFL v4.0, 4.2.1).
 *
 * <p>Values in one partition should behave alike, so one of each is enough. More tests of the same partition add
 * time, not confidence.
 *
 * <p>Adds the JUnit tag {@code tech:ep}. The tag selects tests ({@code -Dgroups=tech:ep}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:ep")
public @interface EquivalencePartitioning {
}
