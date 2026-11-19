package io.qaplayground.course.advanced;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.qaplayground.course.support.CourseTest;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Lesson 14: a filter sits between your test and the network.
 *
 * <p>It sees every request before it is sent and every response before your assertions run. Behaviour that every
 * test needs (tracing, timing, failing fast) then lives in one place instead of in every test.
 */
class L14_CustomFiltersTest extends CourseTest {

    /**
     * Adds a trace id to every request and records how long each call took.
     */
    static final class TraceFilter implements Filter {

        final List<Long> elapsedMillis = new ArrayList<>();

        @Override
        public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response,
                               FilterContext context) {
            request.header("X-Trace-Id", UUID.randomUUID().toString());
            long start = System.nanoTime();
            Response result = context.next(request, response);   // send it, get the response
            elapsedMillis.add((System.nanoTime() - start) / 1_000_000);
            return result;
        }
    }

    /**
     * The header the filter adds really reaches the server.
     */
    @Test
    void filterAddsHeader() {
        given().filter(new TraceFilter())
                .get("/headers").then()
                .body("headers.X-Trace-Id[0]", notNullValue());
    }

    /**
     * The filter times each call; the slow one takes at least as long as its delay.
     */
    @Test
    void filterMeasuresTime() {
        TraceFilter trace = new TraceFilter();
        given().filter(trace).get("/get");
        given().filter(trace).get("/delay/0.2");

        assertThat(trace.elapsedMillis).hasSize(2);
        assertThat(trace.elapsedMillis.get(1)).isGreaterThanOrEqualTo(200L);
    }
}
