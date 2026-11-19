package io.qaplayground.api.methods;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import io.qaplayground.api.support.PlaygroundTest;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.ErrorGuessing;
import io.qaplayground.testing.technique.SecurityTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tutorial: sending bodies with different methods and content types, and checking how the server read them
 * ({@code data}, {@code form}, {@code files}, {@code json}).
 */
class HttpMethodsTest extends PlaygroundTest {

    /**
     * Parameterised test: one method body, many inputs. Each endpoint accepts exactly one method.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /post", "PUT, /put", "PATCH, /patch", "DELETE, /delete"})
    void eachEndpointEchoesItsMethod(String method, String path) {
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": 1}")
        .when()
            .request(method, path)
        .then()
            .statusCode(200)
            .body("method", equalTo(method))
            .body("json.id", equalTo(1))
            .body("data", equalTo("{\"id\": 1}"));
    }

    /**
     * Each method endpoint refuses the others with 405 and an {@code Allow} header.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "GET {0} is 405")
    @CsvSource({"/post, POST", "/put, PUT", "/patch, PATCH", "/delete, DELETE"})
    void wrongMethodIs405(String path, String allow) {
        when()
            .get(path)
        .then()
            .statusCode(405)
            .header("Allow", allow);
    }

    /**
     * {@code formParam} sends {@code application/x-www-form-urlencoded}; repeated fields become lists.
     */
    @EquivalencePartitioning
    @Test
    void formFieldsAreParsed() {
        given()
            .formParam("custname", "Ada")
            .formParam("topping", "bacon", "onion")
        .when()
            .post("/post")
        .then()
            .body("form.custname[0]", equalTo("Ada"))
            .body("form.topping", equalTo(List.of("bacon", "onion")))
            .body("json", nullValue());
    }

    /**
     * Multipart: plain fields land in {@code form}, uploaded files in {@code files}.
     */
    @EquivalencePartitioning
    @Test
    void multipartFieldsAndFiles() {
        given()
            .multiPart("note", "hi")
            .multiPart("upload", "hello.txt", "hello world".getBytes(StandardCharsets.UTF_8), "text/plain")
        .when()
            .post("/post")
        .then()
            .statusCode(200)
            .body("form.note[0]", equalTo("hi"))
            .body("files.upload[0]", equalTo("hello world"));
    }

    /**
     * Unknown content types are returned as a base64 {@code data:} URL.
     */
    @EquivalencePartitioning
    @Test
    void binaryBodyBecomesDataUrl() {
        byte[] payload = "encodeMe-png".getBytes(StandardCharsets.UTF_8);
        given()
            .contentType("image/png")
            .body(payload)
        .when()
            .post("/post")
        .then()
            .body("data", equalTo("data:image/png;base64," + Base64.getUrlEncoder().encodeToString(payload)));
    }

    /**
     * Forgot the {@code Content-Type}? The server can't know it is a form, so nothing is parsed. A classic mistake,
     * which
     * this test documents.
     */
    @EquivalencePartitioning
    @Test
    void formBodyWithoutContentTypeIsNotParsed() {
        given()
            .noContentType()
            .body("foo=foo".getBytes(StandardCharsets.UTF_8))
        .when()
            .post("/post")
        .then()
            .body("form", anEmptyMap())
            .body("data", startsWith("data:application/octet-stream;base64,"));
    }

    /**
     * No body at all: {@code data}, {@code form} and {@code files} are empty, and {@code json} is null.
     */
    @ErrorGuessing
    @Test
    void emptyBody() {
        when()
            .post("/post")
        .then()
            .statusCode(200)
            .body("data", emptyString())
            .body("json", nullValue())
            .body("form", anEmptyMap())
            .body("files", anEmptyMap());
    }

    /**
     * Broken JSON with a JSON {@code Content-Type} is a 400 in go-httpbin's error shape.
     */
    @ErrorGuessing
    @Test
    void invalidJsonIs400() {
        given()
            .contentType(ContentType.JSON)
            .body("foo")
        .when()
            .post("/post")
        .then()
            .statusCode(400)
            .body("status_code", equalTo(400))
            .body("error", equalTo("Bad Request"))
            .body("detail", startsWith("error parsing request body: "));
    }

    /**
     * A form body with a bad {@code %} escape is a 400.
     */
    @ErrorGuessing
    @Test
    void invalidFormEncodingIs400() {
        given()
            .contentType("application/x-www-form-urlencoded")
            .body("%ZZ")
        .when()
            .post("/post")
        .then()
            .statusCode(400)
            .body("detail", equalTo("error parsing request body: invalid URL escape \"%ZZ\""));
    }

    /**
     * A multipart body without a boundary can't be split into parts, so it is a 400.
     */
    @ErrorGuessing
    @Test
    void multipartWithoutBoundaryIs400() {
        given()
            .header("Content-Type", "multipart/form-data; etc")
            .body("%ZZ".getBytes(StandardCharsets.UTF_8))
        .when()
            .post("/post")
        .then()
            .statusCode(400);
    }

    /**
     * Bodies above playground.max-body-size (1 MiB by default) are rejected.
     */
    @SecurityTest
    @Test
    void bodyTooLargeIs400() {
        given()
            .contentType("application/octet-stream")
            .body(new byte[1024 * 1024 + 1])
        .when()
            .post("/post")
        .then()
            .statusCode(400)
            .body("detail", equalTo("error parsing request body: http: request body too large"));
    }

    /**
     * HEAD-only: headers, no body; GET is refused.
     */
    @Test
    void headEndpoint() {
        when().head("/head").then().statusCode(200).body(emptyString());
        when().get("/head").then().statusCode(405).header("Allow", "HEAD");
    }

    /**
     * {@code /upload} throws the body away and only reports its size.
     */
    @ParameterizedTest(name = "{0} /upload")
    @CsvSource({"POST", "PUT", "PATCH"})
    void uploadReportsBytesReceived(String method) {
        given()
            .contentType("application/octet-stream")
            .body(new byte[2048])
        .when()
            .request(method, "/upload")
        .then()
            .statusCode(200)
            .body("bytes_received", equalTo(2048))
            .body("method", equalTo(method));
    }

    /**
     * {@code /upload} accepts only POST, PUT and PATCH. GET gets 405, with an {@code Allow} header that says so.
     */
    @Test
    void uploadRejectsGet() {
        when().get("/upload").then().statusCode(405).header("Allow", "PATCH, POST, PUT");
    }

    /**
     * The HTML form page is served, and it posts to {@code /post}.
     */
    @Test
    void htmlFormPostsToPost() {
        when()
            .get("/forms/post")
        .then()
            .statusCode(200)
            .contentType(ContentType.HTML)
            .body(containsString("action=\"/post\""));
    }
}
