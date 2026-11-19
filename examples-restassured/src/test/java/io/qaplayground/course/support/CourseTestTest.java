package io.qaplayground.course.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the plumbing every lesson relies on.
 *
 * <p>If these fail, every lesson fails with them, so they say clearly which part broke.
 */
class CourseTestTest {

    /**
     * The address is a plain HTTP URL that REST Assured can use.
     */
    @Test
    void baseUriIsAnHttpUrl() {
        assertThat(CourseTest.baseUri()).startsWith("http://");
    }

    /**
     * Asking twice gives the same address, so a second playground is never started by accident.
     */
    @Test
    void baseUriIsStable() {
        assertThat(CourseTest.baseUri()).isEqualTo(CourseTest.baseUri());
    }

    /**
     * With {@code -Dplayground.baseUri}, the tests use that server and start none of their own.
     */
    @Test
    void usesConfiguredServerWhenGiven() {
        String configured = System.getProperty("playground.baseUri");
        assumeTrue(configured != null && !configured.isBlank(), "only when -Dplayground.baseUri is set");
        assertThat(CourseTest.baseUri()).isEqualTo(configured.replaceAll("/+$", ""));
        assertThat(CourseTest.startedInProcess()).isFalse();
    }
}
