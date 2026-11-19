package io.qaplayground.course.advanced;

import io.qaplayground.course.support.CourseTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Lesson 15: security negative tests.
 *
 * <p>Think like an attacker. What happens with input the developer didn't expect? These checks are cheap, and each
 * one guards against a well-known kind of attack.
 */
class L15_SecurityNegativeTest extends CourseTest {

    /**
     * Documents a known risk: by default, {@code /redirect-to} sends a browser anywhere.
     *
     * <p>Phishing links abuse this. They show a trusted domain and end on an attacker's page. Operators lock it down
     * with {@code playground.allowed-redirect-domains}.
     */
    @Test
    void openRedirectIsPossibleByDefault() {
        // /redirect-to sends you anywhere: an "open redirect" that phishing links abuse
        // (a trusted domain in the link, an attacker's page at the end). The playground can be
        // locked down with playground.allowed-redirect-domains; this test documents the default.
        given().redirects().follow(false)
                .queryParam("url", "https://example.org")
                .get("/redirect-to").then()
                .statusCode(302)
                .header("Location", equalTo("https://example.org"));
    }

    /**
     * A line break in input must not become a new response header (header injection).
     */
    @Test
    void crlfInHeaderIsNotInjected() {
        // %0D%0A is CR LF, the line break between headers. If the server copied it into a header,
        // an attacker could add headers of their own ("header injection").
        Response response = given().urlEncodingEnabled(false)
                .get("/response-headers?X-Test=a%0D%0ASet-Cookie:%20pwned=1");

        assertThat(response.statusCode()).isLessThan(500);
        assertThat(response.headers().getValues("Set-Cookie")).noneMatch(value -> value.contains("pwned"));
    }

    /**
     * One byte over the limit is refused with 400. It is not accepted, and it doesn't crash the server.
     */
    @Test
    void oversizedBodyIsRejected() {
        // The limit is 1 MiB; one byte more must be refused with 400, not accepted or crash the server.
        given().contentType("text/plain").body("x".repeat(1_048_577))
                .post("/post").then()
                .statusCode(400);
    }

    /**
     * No credentials, no entry.
     */
    @Test
    void missingCredentialsIs401() {
        given().get("/basic-auth/u/p").then().statusCode(401);
    }
}
