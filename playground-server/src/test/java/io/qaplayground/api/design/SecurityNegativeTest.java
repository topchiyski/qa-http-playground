package io.qaplayground.api.design;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Negative security tests for the risks in docs/testing/RISK-REGISTER.md: R1 open redirect, R2 reflected XSS and R4
 * header injection.
 *
 * <p>The playground is permissive on purpose: it echoes and redirects by design. These tests pin down where that
 * permissiveness stops.
 */
@SecurityTest
class SecurityNegativeTest extends PlaygroundTest {

    /**
     * A request that doesn't follow redirects, so the {@code Location} itself can be checked.
     */
    private static RequestSpecification noFollow() {
        return given().config(RestAssuredConfig.config().redirect(RedirectConfig.redirectConfig().followRedirects(false)));
    }

    /**
     * R1: backslashes and spaces in a target are escaped in {@code Location}, so a browser never reads it as another
     * host.
     */
    @ParameterizedTest(name = "redirect-to {0} -> Location {1}")
    @CsvSource(delimiter = '|', value = {
            "/\\evil.com|/%5Cevil.com",
            "/\\/evil.com|/%5C/evil.com",
            "/a b|/a%20b"})
    void locationCannotBeTurnedIntoAnotherHost(String target, String location) {
        noFollow().queryParam("url", target).get("/redirect-to").then().statusCode(302).header("Location", location);
    }

    @ParameterizedTest(name = "R2: /response-headers with Content-Type {0} escapes markup")
    @CsvSource({"text/html", "image/svg+xml", "text/html; charset=utf-8", "TEXT/HTML"})
    void reflectedMarkupIsEscaped(String contentType) {
        Response r = given().queryParam("Content-Type", contentType).queryParam("x", "<script>alert(1)</script>")
                .get("/response-headers");
        assertThat(r.asString()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("R2: /base64 decoding to HTML is escaped unless the type is safe")
    void base64MarkupIsEscaped() {
        String b64 = java.util.Base64.getUrlEncoder().encodeToString("<img src=x onerror=alert(1)>".getBytes());
        Response html = given().queryParam("content-type", "text/html").get("/base64/" + b64);
        assertThat(html.asString()).doesNotContain("<img").contains("&lt;img");
        Response plain = given().queryParam("content-type", "text/plain").get("/base64/" + b64);
        assertThat(plain.asString()).contains("<img");
    }

    /**
     * R4: a line break in input (CRLF or a bare LF) never becomes a new response header.
     */
    @ErrorGuessing
    @ParameterizedTest(name = "R4: header injection via {0}")
    @CsvSource(delimiter = '|', value = {
            "/response-headers?X-Test=a%0D%0ASet-Cookie:%20pwned=1",
            "/response-headers?X-Test=a%0ASet-Cookie:%20pwned=1",
            "/cookies/set?a=b%0D%0ASet-Cookie:%20pwned=1"})
    void noHeaderInjection(String path) {
        Response r = noFollow().urlEncodingEnabled(false).get(path);
        assertThat(r.statusCode()).isLessThan(500);
        assertThat(r.headers().getValues("Set-Cookie")).noneMatch(v -> v.startsWith("pwned"));
        assertThat(r.headers().hasHeaderWithName("pwned")).isFalse();
    }

    @Test
    @DisplayName("Echo endpoints never expose server environment variables outside the prefix")
    void environmentIsFiltered() {
        given().get("/env").then().statusCode(200)
                .body("env.PATH", org.hamcrest.Matchers.nullValue())
                .body("env.JAVA_HOME", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @DisplayName("Security headers on HTML pages: the landing page sends a Content-Security-Policy")
    void landingPageHasCsp() {
        assertThat(given().get("/").header("Content-Security-Policy")).isNotBlank();
    }
}
