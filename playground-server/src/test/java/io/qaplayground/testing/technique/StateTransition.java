package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that covers a valid or an invalid transition of a state model (CTFL v4.0, 4.2.4).
 *
 * <p>Many bugs only appear in a certain order of events. A state model lists the orders worth testing.
 *
 * <p>Adds the JUnit tag {@code tech:state-transition}. The tag selects tests ({@code -Dgroups=tech:state-transition})
 * and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:state-transition")
public @interface StateTransition {
}
