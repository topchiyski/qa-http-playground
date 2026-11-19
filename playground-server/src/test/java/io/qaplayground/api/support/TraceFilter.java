package io.qaplayground.api.support;

import io.qaplayground.testing.trace.Trace;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Records every request's path for the traceability matrix (docs/testing/TRACEABILITY.md).
 *
 * <p>A REST Assured {@link Filter} sees each request before it is sent. Filters are also how you would add logging,
 * auth tokens or request signing to a whole suite, in one place.
 */
public final class TraceFilter implements Filter {

    /**
     * Records the path, then sends the request on unchanged.
     */
    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response,
                           FilterContext context) {
        Trace.path(request.getURI());
        return context.next(request, response);
    }
}
