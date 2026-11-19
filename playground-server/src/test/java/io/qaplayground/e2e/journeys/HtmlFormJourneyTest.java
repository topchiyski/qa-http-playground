package io.qaplayground.e2e.journeys;

import io.qaplayground.e2e.pages.FormsPostPage;
import io.qaplayground.e2e.pages.JsonPage;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.testing.technique.AcceptanceTest;
import io.qaplayground.testing.technique.BoundaryValue;
import io.qaplayground.testing.technique.EquivalencePartitioning;
import io.qaplayground.testing.technique.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openqa.selenium.WebDriver;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 2: an HTML form round trip.
 *
 * <p><b>Given</b> a learner opens {@code /forms/post} <b>When</b> they fill in name, telephone, email, a size, two
 * toppings, a delivery time and comments, and submit <b>Then</b> {@code /post} echoes every field under
 * {@code form}, the toppings as a list <b>And</b> the request was sent as
 * {@code application/x-www-form-urlencoded}.
 */
@E2eTest
@AcceptanceTest
@UseCase
class HtmlFormJourneyTest {

    /**
     * Main flow: every field arrives at {@code /post}, two toppings as a list of two.
     */
    @Test
    @DisplayName("A submitted form arrives field by field")
    void formRoundTrip(WebDriver driver) {
        JsonPage result = FormsPostPage.open(driver)
                .name("Ada Lovelace").telephone("+44 20 7946 0000").email("ada@example.org")
                .size("medium").topping("bacon").topping("onion")
                .delivery("19:30").comments("Ring twice.\nThank you!")
                .submit();
        JsonNode json = result.json();
        assertThat(json.at("/form/custname/0").asString()).isEqualTo("Ada Lovelace");
        assertThat(json.at("/form/custtel/0").asString()).isEqualTo("+44 20 7946 0000");
        assertThat(json.at("/form/custemail/0").asString()).isEqualTo("ada@example.org");
        assertThat(json.at("/form/size/0").asString()).isEqualTo("medium");
        assertThat(json.at("/form/topping").valueStream().map(JsonNode::asString)).containsExactly("bacon", "onion");
        assertThat(json.at("/form/delivery/0").asString()).isEqualTo("19:30");
        assertThat(json.at("/form/comments/0").asString()).isEqualTo("Ring twice.\r\nThank you!"); // browsers send CRLF
        assertThat(json.at("/headers/Content-Type/0").asString()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(json.at("/method").asString()).isEqualTo("POST");
    }

    /**
     * An empty form still submits: empty text fields are sent, unticked checkboxes and radio buttons are not.
     */
    @Test
    @DisplayName("An empty form still submits; empty fields arrive as empty strings, unchecked boxes not at all")
    void emptyForm(WebDriver driver) {
        JsonNode json = FormsPostPage.open(driver).submit().json();
        assertThat(json.at("/form/custname/0").asString()).isEmpty();
        assertThat(json.at("/form").has("topping")).isFalse();
        assertThat(json.at("/form").has("size")).isFalse();
    }

    /**
     * The delivery time's {@code min}, {@code max} and {@code step} are enforced by the browser before anything is
     * sent.
     */
    @BoundaryValue
    @ParameterizedTest(name = "delivery {0}: valid {1}")
    @CsvSource({"10:45, false", "11:00, true", "11:15, true", "20:45, true", "21:00, true", "21:15, false",
            "11:05, false"}) // off the 15-minute step
    void deliveryTimeConstraints(String time, boolean valid, WebDriver driver) {
        FormsPostPage form = FormsPostPage.open(driver).delivery(time);
        assertThat(form.isValid()).isEqualTo(valid);
        if (!valid) {
            assertThat(form.validationMessage("delivery")).isNotBlank();
        }
    }

    /**
     * The browser checks {@code type=email} itself, before anything is sent.
     */
    @EquivalencePartitioning
    @ParameterizedTest(name = "email \"{0}\": valid {1}")
    @CsvSource({"ada@example.org, true", "'', true", "not-an-email, false", "a@, false"})
    void emailField(String email, boolean valid, WebDriver driver) {
        assertThat(FormsPostPage.open(driver).email(email).isValid()).isEqualTo(valid);
    }
}
