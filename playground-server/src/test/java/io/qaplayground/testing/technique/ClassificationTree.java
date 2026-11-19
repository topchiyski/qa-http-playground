package io.qaplayground.testing.technique;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test that covers one combination of leaves of a classification tree (CTAL-TA).
 *
 * <p>The tree makes the input space visible, so you can see which combinations are tested and which are not.
 *
 * <p>Adds the JUnit tag {@code tech:classification-tree}. The tag selects tests
 * ({@code -Dgroups=tech:classification-tree}) and feeds
 * docs/testing/TRACEABILITY.md. The derivations are in docs/testing/TEST-DESIGN.md.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Tag("tech:classification-tree")
public @interface ClassificationTree {
}
