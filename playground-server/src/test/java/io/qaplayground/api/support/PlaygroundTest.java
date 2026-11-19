package io.qaplayground.api.support;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for every example.
 *
 * <p>It points REST Assured at the playground, and turns on "log the request and response if a test fails". That is
 * the single most useful REST Assured setting while you are learning.
 */
public abstract class PlaygroundTest {

    /**
     * A reusable starting point: {@code given().spec(spec)...}.
     */
    protected static RequestSpecification spec;

    private static boolean filtersInstalled;

    /**
     * Sets the base URI, the failure logging and the traceability filter, once per test class.
     */
    @BeforeAll
    static void pointRestAssuredAtThePlayground() {
        RestAssured.baseURI = PlaygroundTarget.baseUri();
        RestAssured.port = -1; // the port is already part of baseURI
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        if (!filtersInstalled) {
            // Global filters apply to every request, including plain given()/when() calls.
            RestAssured.filters(new TraceFilter());
            filtersInstalled = true;
        }
        spec = new RequestSpecBuilder().setBaseUri(RestAssured.baseURI).build();
    }
}
