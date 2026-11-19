package io.qaplayground.api.cookies;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.StateTransition;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.Cookie;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tutorial: cookies. Sending them, reading {@code Set-Cookie}, cookie attributes, and keeping a cookie jar across
 * requests with REST Assured's {@link CookieFilter}.
 */
class CookiesTest extends PlaygroundTest {

    /**
     * A request that doesn't follow redirects, so the {@code Set-Cookie} on the 302 stays visible.
     */
    private static RequestSpecification noFollow() {
        return given().config(RestAssuredConfig.config()
                .redirect(RedirectConfig.redirectConfig().followRedirects(false)));
    }

    /**
     * The cookies you send come back as a JSON object.
     */
    @Test
    void echoesCookiesSent() {
        given()
            .cookie("flavour", "oatmeal")
            .cookie("size", "large")
        .when()
            .get("/cookies")
        .then()
            .statusCode(200)
            .body("cookies.flavour", equalTo("oatmeal"))
            .body("cookies.size", equalTo("large"));
    }

    /**
     * No cookies: an empty object, not a missing field.
     */
    @Test
    void noCookiesGivesEmptyObject() {
        given().when().get("/cookies").then().body("cookies", anEmptyMap());
    }

    /**
     * Quoted cookie values arrive without their quotes.
     */
    @Test
    void quotedValueIsUnquoted() {
        given()
            .header("Cookie", "token=\"abc\"")
        .when()
            .get("/cookies")
        .then()
            .body("cookies.token", equalTo("abc"));
    }

    /**
     * /cookies/set answers 302 with Set-Cookie; inspect it before following the redirect.
     */
    @Test
    void setCookieDefaults() {
        noFollow()
            .queryParam("flavour", "oatmeal")
            .get("/cookies/set")
        .then()
            .statusCode(302)
            .header("Location", "/cookies")
            .header("Set-Cookie", "flavour=oatmeal; Path=/; HttpOnly");
    }

    /**
     * {@code attr[...]} parameters control the cookie attributes.
     */
    @Test
    void setCookieAttributes() {
        Cookie cookie = noFollow()
            .urlEncodingEnabled(false)
            .get("/cookies/set?k=v&attr[SameSite]=Strict&attr[Secure]=true&attr[HttpOnly]=false&attr[Path]=/foo")
        .then()
            .statusCode(302)
            .extract().detailedCookie("k");

        org.hamcrest.MatcherAssert.assertThat(cookie.getValue(), equalTo("v"));
        org.hamcrest.MatcherAssert.assertThat(cookie.getPath(), equalTo("/foo"));
        org.hamcrest.MatcherAssert.assertThat(cookie.isSecured(), equalTo(true));
        org.hamcrest.MatcherAssert.assertThat(cookie.isHttpOnly(), equalTo(false));
        org.hamcrest.MatcherAssert.assertThat(cookie.getSameSite(), equalTo("Strict"));
    }

    /**
     * Over HTTPS (here: claimed via X-Forwarded-Proto) cookies are Secure by default.
     */
    @Test
    void secureByDefaultOverHttps() {
        noFollow()
            .header("X-Forwarded-Proto", "https")
            .queryParam("k", "v")
            .get("/cookies/set")
        .then()
            .header("Set-Cookie", "k=v; Path=/; HttpOnly; Secure");
    }

    /**
     * Deleting = overwriting with an expired cookie.
     */
    @StateTransition
    @Test
    void deleteExpiresTheCookie() {
        noFollow()
            .queryParam("flavour", "")
            .get("/cookies/delete")
        .then()
            .statusCode(302)
            .header("Set-Cookie", containsString("flavour=; Path=/; Expires="))
            .header("Set-Cookie", containsString("; Max-Age=0; HttpOnly"));
    }

    /**
     * A cookie jar: {@link CookieFilter} keeps cookies from responses and sends them with later requests, which a
     * browser does for you.
     *
     * <p>Gotcha: REST Assured follows redirects <em>inside</em> one request, and the jar only sees the final response.
     * A
     * cookie set on the 302 would be lost, so we stop at the 302.
     */
    @StateTransition
    @Test
    void cookieJarRoundTrip() {
        CookieFilter jar = new CookieFilter();
        noFollow().filter(jar).queryParam("session", "s3cr3t").get("/cookies/set")
            .then().statusCode(302);

        given().filter(jar).get("/cookies")
            .then().body("cookies.session", equalTo("s3cr3t"));

        noFollow().filter(jar).queryParam("session", "").get("/cookies/delete")
            .then().statusCode(302);

        given().filter(jar).get("/cookies")
            .then().body("cookies.session", nullValue());
    }

    /**
     * The same flow with automatic redirects: the cookie never makes it into the jar.
     */
    @Test
    void cookieSetDuringFollowedRedirectIsLost() {
        CookieFilter jar = new CookieFilter();
        given().filter(jar).queryParam("lost", "1").get("/cookies/set")
            .then().statusCode(200).body("cookies.lost", nullValue());
    }

    /**
     * A cookie with an invalid name is ignored, as Go ignores it.
     */
    @ErrorGuessing
    @Test
    void invalidCookieNamesAreIgnored() {
        given()
            .header("Cookie", "good=1; bad name=2")
        .when()
            .get("/cookies")
        .then()
            .body("cookies.good", equalTo("1"))
            .body("cookies", not(org.hamcrest.Matchers.hasKey("bad name")));
    }
}
