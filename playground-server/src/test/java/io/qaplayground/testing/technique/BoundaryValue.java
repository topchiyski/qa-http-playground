package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test derived by boundary value analysis, 3-value (CTFL v4.0, 4.2.2): the boundary of an ordered
 * partition and both of its neighbours.
 *
 * <p>Off-by-one mistakes live at the edges, so the edges are where these tests look.
 *
 * <p>Adds the JUnit tag {@code tech:bva}. The tag selects tests ({@code -Dgroups=tech:bva}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:bva")
public @interface BoundaryValue {
}
