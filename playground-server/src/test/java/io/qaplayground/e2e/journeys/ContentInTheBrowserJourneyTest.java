package io.qaplayground.e2e.journeys;

import java.util.List;
import java.util.Map;

import io.qaplayground.e2e.pages.JsonPage;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.e2e.support.Playground;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journeys 7 and 8: content and streaming, as the browser handles them.
 *
 * <p>These features exist only in a browser: decoding, image decoders, {@code EventSource}, WebSocket,
 * {@code Server-Timing}. So they can only be tested end to end. Asynchronous checks use {@code executeAsyncScript}:
 * the browser calls back when the event happens, so there is nothing to sleep on.
 */
@E2eTest
@UseCase
class ContentInTheBrowserJourneyTest {

    /**
     * The HTML page renders, with its heading.
     */
    @Test
    @DisplayName("/html renders as a page")
    void htmlRenders(WebDriver driver) {
        driver.get(Playground.url("/html"));
        assertThat(driver.findElement(By.tagName("h1")).getText()).isEqualTo("Herman Melville - Moby-Dick");
    }

    /**
     * The UTF-8 page renders its characters, not replacement marks.
     */
    @Test
    @DisplayName("/encoding/utf8 shows non-Latin scripts correctly")
    void utf8Renders(WebDriver driver) {
        driver.get(Playground.url("/encoding/utf8"));
        assertThat(driver.findElement(By.tagName("body")).getText()).contains("Unicode Demo").contains("∮ E⋅da = Q").contains("ሰማይ");
    }

    /**
     * The browser decompresses gzip and deflate bodies by itself and shows the JSON.
     */
    @Test
    @DisplayName("/gzip and /deflate are decompressed transparently")
    void compressedJson(WebDriver driver) {
        assertThat(JsonPage.at(driver, "/gzip").json().get("gzipped").asBoolean()).isTrue();
        assertThat(JsonPage.at(driver, "/deflate").json().get("deflated").asBoolean()).isTrue();
    }

    /**
     * Each format decodes in the browser's own image decoder: {@code naturalWidth > 0}.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "<img src=/image/{0}> decodes")
    @ValueSource(strings = {"png", "jpeg", "webp", "svg", "avif"})
    void imagesDecode(String kind, WebDriver driver) {
        driver.get(Playground.url("/robots.txt")); // any same-origin page to host the <img>
        Object width = ((JavascriptExecutor) driver).executeAsyncScript("""
                const done = arguments[arguments.length - 1];
                const img = new Image();
                img.onload = () => done(img.naturalWidth);
                img.onerror = () => done(-1);
                img.src = arguments[0];
                """, "/image/" + kind);
        assertThat(((Number) width).intValue()).isPositive();
    }

    /**
     * {@code EventSource} receives the events {@code /sse} sends.
     */
    @Test
    @DisplayName("EventSource receives /sse events")
    void serverSentEvents(WebDriver driver) {
        driver.get(Playground.url("/robots.txt"));
        @SuppressWarnings("unchecked")
        List<String> events = (List<String>) ((JavascriptExecutor) driver).executeAsyncScript("""
                const done = arguments[arguments.length - 1];
                const received = [];
                const es = new EventSource('/sse?count=3&duration=1ms&delay=0'); // duration must be >= 1ns, as upstream
                es.addEventListener('ping', e => {
                  received.push(e.data);
                  if (received.length === 3) { es.close(); done(received); }
                });
                es.onerror = () => { if (received.length < 3) { es.close(); done(received); } };
                """);
        assertThat(events).hasSize(3);
        assertThat(events.getFirst()).startsWith("{\"id\":0,");
    }

    /**
     * A browser WebSocket gets its message echoed back.
     */
    @Test
    @DisplayName("A browser WebSocket gets its message echoed")
    void webSocketEcho(WebDriver driver) {
        driver.get(Playground.url("/robots.txt"));
        Object echoed = ((JavascriptExecutor) driver).executeAsyncScript("""
                const done = arguments[arguments.length - 1];
                const ws = new WebSocket('ws://' + location.host + '/websocket/echo');
                ws.onopen = () => ws.send('héllo €');
                ws.onmessage = e => { done(e.data); ws.close(); };
                ws.onerror = () => done('error');
                """);
        assertThat(echoed).isEqualTo("héllo €");
    }

    /**
     * The browser exposes the {@code Server-Timing} values to scripts, as dev tools show them.
     */
    @Test
    @DisplayName("Server-Timing from /delay shows up in the browser's performance timeline")
    void serverTimingIsExposed(WebDriver driver) {
        driver.get(Playground.url("/delay/0.1"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timings = (List<Map<String, Object>>) ((JavascriptExecutor) driver).executeScript(
                "return performance.getEntriesByType('navigation')[0].serverTiming.map(t => ({name: t.name, duration: t.duration}))");
        assertThat(timings).extracting(t -> t.get("name")).contains("initial_delay");
    }
}
