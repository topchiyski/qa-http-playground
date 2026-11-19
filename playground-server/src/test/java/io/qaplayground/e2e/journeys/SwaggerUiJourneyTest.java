package io.qaplayground.e2e.journeys;

import io.qaplayground.e2e.pages.SwaggerUiPage;
import io.qaplayground.e2e.support.E2eTest;
import io.qaplayground.testing.Endpoints;
import io.qaplayground.testing.technique.AcceptanceTest;
import io.qaplayground.testing.technique.UseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Journey 3: trying an endpoint in Swagger UI.
 *
 * <p><b>Given</b> a learner opens {@code /swagger-ui} <b>When</b> they expand {@code GET /status/{code}}, click
 * "Try it out", enter 418 and execute <b>Then</b> Swagger UI shows status 418 and the body {@code I'm a teapot!}.
 *
 * <p>Swagger UI is third-party. We test our contract with it (it loads our spec and can call us), not its internals.
 */
@E2eTest
@AcceptanceTest
@UseCase
class SwaggerUiJourneyTest {

    /**
     * Swagger UI lists every route in the inventory.
     */
    @Test
    @DisplayName("Swagger UI lists every endpoint of the inventory")
    void everyEndpointIsListed(WebDriver driver) {
        var documented = SwaggerUiPage.open(driver).documentedPaths();
        Endpoints.ROUTES.forEach(r -> assertThat(documented).as("Swagger UI paths").contains(r.template()));
    }

    /**
     * Main flow: try {@code GET /status/418} from Swagger UI and see the teapot.
     */
    @Test
    @DisplayName("Try it out: GET /status/418 shows the teapot")
    void tryItOut(WebDriver driver) {
        SwaggerUiPage swagger = SwaggerUiPage.open(driver)
                .expand("GET", "/status/{code}").tryItOut().parameter("code", "418").execute();
        assertThat(swagger.responseStatus()).isEqualTo("418");
        assertThat(swagger.responseBody()).contains("I'm a teapot!");
    }
}
