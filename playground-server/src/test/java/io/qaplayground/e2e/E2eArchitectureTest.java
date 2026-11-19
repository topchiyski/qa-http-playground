package io.qaplayground.e2e;

import java.time.Duration;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Rules for the E2E code itself: the habits that keep browser tests fast and stable.
 *
 * <p>Browser tests decay faster than any other kind. These rules stop the most common causes before they spread.
 */
class E2eArchitectureTest {

    /**
     * The E2E classes the rules check.
     */
    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("io.qaplayground.e2e");

    /**
     * No {@code Thread.sleep}. A fixed sleep is either too short (flaky) or too long (slow); wait for a condition.
     */
    @Test
    @DisplayName("No Thread.sleep: wait for a condition instead")
    void noSleeps() {
        noClasses().should().callMethod(Thread.class, "sleep", long.class)
                .orShould().callMethod(Thread.class, "sleep", Duration.class)
                .because("fixed sleeps are either too short (flaky) or too long (slow); use WebDriverWait")
                .check(CLASSES);
    }

    /**
     * Journeys never use {@code By}. Locators live in page objects, so a page change breaks one file, not every test.
     */
    @Test
    @DisplayName("Journeys use page objects for locators; only pages and support know about By")
    void locatorsLiveInPageObjects() {
        noClasses().that().resideInAPackage("..journeys..")
                .and().doNotHaveSimpleName("ContentInTheBrowserJourneyTest") // reads one <h1>/<body> on raw endpoints
                .should().dependOnClassesThat().haveFullyQualifiedName("org.openqa.selenium.By")
                .check(CLASSES);
    }

    /**
     * Only {@code support} creates browsers, so every test gets the same setup, clean state and failure artifacts.
     */
    @Test
    @DisplayName("Tests never create browsers themselves; the extension owns their lifecycle")
    void browsersComeFromTheExtension() {
        noClasses().that().resideOutsideOfPackage("..support..")
                .should().callConstructorWhere(DescribedPredicate.describe("a WebDriver constructor",
                        (JavaConstructorCall c) -> c.getTarget().getOwner().isAssignableTo(WebDriver.class)))
                .check(CLASSES);
    }
}
