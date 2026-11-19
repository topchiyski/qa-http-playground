package io.qaplayground.course.solutions;

import io.qaplayground.course.support.CourseTest;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Solution for lesson 14. Compare it with yours after your own version is green.
 */
class S14_CustomFiltersSolution extends CourseTest {

    /**
     * Turns any 5xx into an immediate failure that names the method and the URL.
     */
    static final class FailOnServerError implements Filter {
        @Override
        public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response,
                               FilterContext context) {
            Response result = context.next(request, response);
            if (result.statusCode() >= 500) {
                throw new AssertionError("Server error " + result.statusCode() + " for "
                        + request.getMethod() + " " + request.getURI());
            }
            return result;
        }
    }

    /**
     * The filter stops on a 500 and stays quiet on a 404, which is a client error, not a server one.
     */
    @Test
    void failFastOnServerErrors() {
        assertThatThrownBy(() -> given().filter(new FailOnServerError()).get("/status/500"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Server error 500 for GET");
        assertThatCode(() -> given().filter(new FailOnServerError()).get("/status/404"))
                .doesNotThrowAnyException();
    }
}
