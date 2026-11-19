package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that checks an invariant over many generated inputs (beyond ISTQB).
 *
 * <p>One rule, checked against hundreds of inputs you didn't choose, finds the cases you would never think to
 * write.
 *
 * <p>Adds the JUnit tag {@code tech:property}. The tag selects tests ({@code -Dgroups=tech:property}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:property")
public @interface PropertyBased {
}
