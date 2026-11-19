package io.qaplayground.course.project;

import io.qaplayground.course.support.CourseTest;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Final project, reference solution: a test suite for the cookie jar ({@code /cookies}, {@code /cookies/set},
 * {@code /cookies/delete}).
 *
 * <p>The model is a state machine. The jar is either empty or holds cookies. Set, overwrite, delete one and delete
 * all move it between those states. Each test names the technique that produced it, so a reviewer can see why it
 * exists.
 */
class CookieJarSuiteSolution extends CourseTest {

    /**
     * A request that uses this jar and doesn't follow redirects, so the {@code Set-Cookie} on the 302 reaches the jar.
     */
    private static RequestSpecification with(CookieFilter jar) {
        return given().filter(jar).redirects().follow(false);
    }

    /**
     * State transition, empty to empty: a new client starts with no cookies.
     */
    @Test
    void newJarIsEmpty() {
        with(new CookieFilter()).get("/cookies").then().statusCode(200).body("cookies", anEmptyMap());
    }

    /**
     * State transition, empty to holds cookies: set one.
     */
    @Test
    void setOneCookie() {
        CookieFilter jar = new CookieFilter();
        with(jar).get("/cookies/set?flavour=oatmeal").then().statusCode(302);

        with(jar).get("/cookies").then().body("cookies.flavour", equalTo("oatmeal"));
    }

    /**
     * State transition, holds cookies to holds cookies: setting the same name again keeps only the newest value.
     */
    @Test
    void settingAgainOverwrites() {
        CookieFilter jar = new CookieFilter();
        with(jar).get("/cookies/set?flavour=oatmeal");
        with(jar).get("/cookies/set?flavour=ginger");

        with(jar).get("/cookies").then().body("cookies.flavour", equalTo("ginger"));
    }

    /**
     * State transition, holds cookies to holds cookies: delete one, and the others stay.
     */
    @Test
    void deleteOneKeepsTheRest() {
        CookieFilter jar = new CookieFilter();
        with(jar).get("/cookies/set?a=1&b=2");
        with(jar).get("/cookies/delete?a");

        with(jar).get("/cookies").then()
                .body("cookies.a", nullValue())
                .body("cookies.b", equalTo("2"));
    }

    /**
     * State transition, holds cookies to empty: delete them all.
     */
    @Test
    void deleteAllEmptiesTheJar() {
        CookieFilter jar = new CookieFilter();
        with(jar).get("/cookies/set?a=1&b=2");
        with(jar).get("/cookies/delete?a&b");

        with(jar).get("/cookies").then().body("cookies", anEmptyMap());
    }

    /**
     * Boundary value: the shortest possible value, an empty string, survives.
     */
    @Test
    void emptyValueIsKept() {
        CookieFilter jar = new CookieFilter();
        with(jar).get("/cookies/set?blank=").then().statusCode(302);

        with(jar).get("/cookies").then().body("cookies.blank", equalTo(""));
    }

    /**
     * Error guessing: deleting a cookie that was never set must not fail.
     */
    @Test
    void deletingUnknownCookieIsHarmless() {
        with(new CookieFilter()).get("/cookies/delete?never-set").then().statusCode(302);
    }

    /**
     * Isolation: two clients, with two jars, never see each other's cookies.
     *
     * <p>Not a design technique of its own, but the first risk of any server that many clients share.
     */
    @Test
    void jarsAreIsolated() {
        CookieFilter alice = new CookieFilter();
        CookieFilter bob = new CookieFilter();
        with(alice).get("/cookies/set?owner=alice");

        with(bob).get("/cookies").then().body("cookies", anEmptyMap());
    }

    /**
     * Equivalence partitioning: one value per kind (letters, digits, a value that needs URL encoding).
     */
    @ParameterizedTest(name = "value \"{0}\" survives the round trip")
    @ValueSource(strings = {"abc", "12345", "a b"})
    void valuesSurviveTheRoundTrip(String value) {
        CookieFilter jar = new CookieFilter();
        with(jar).queryParam("v", value).get("/cookies/set");

        with(jar).get("/cookies").then().body("cookies.v", equalTo(value));
    }
}
