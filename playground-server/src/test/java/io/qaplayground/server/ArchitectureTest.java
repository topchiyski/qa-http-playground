package io.qaplayground.server;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Architecture rules, checked as ordinary tests with ArchUnit.
 *
 * <p>They protect two properties the test strategy relies on. The hand-ported Go code stays framework-free, so its
 * unit tests run in milliseconds. And controllers stay independent, so each endpoint group can be read and tested
 * on its own. A rule that lives only in a wiki erodes; a rule that fails the build doesn't.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    /**
     * Imports the production classes once for all rules. Test classes are left out.
     */
    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.qaplayground.server");
    }

    /**
     * Only the application class may use {@code web}, and the core packages ({@code support}, {@code auth},
     * {@code websocket}) never use {@code web}.
     */
    @Test
    @DisplayName("Layers: web on top; support, auth and websocket never depend on web")
    void layers() {
        ArchRule rule = layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Application").definedBy("io.qaplayground.server")
                .layer("Web").definedBy("io.qaplayground.server.web..")
                .layer("Config").definedBy("io.qaplayground.server.config..")
                .layer("Core").definedBy("io.qaplayground.server.support..", "io.qaplayground.server.auth..",
                        "io.qaplayground.server.websocket..")
                .whereLayer("Web").mayOnlyBeAccessedByLayers("Application")
                .whereLayer("Core").mayOnlyBeAccessedByLayers("Web", "Config", "Core", "Application");
        rule.check(classes);
    }

    /**
     * The Go ports in {@code support} use no Spring and no servlet API, so they stay plain, fast unit-test targets.
     */
    @Test
    @DisplayName("Go ports and codecs are framework-free (plain Java, unit-testable without Spring)")
    void goPortsAreFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("io.qaplayground.server.support..", "io.qaplayground.server.auth..",
                        "io.qaplayground.server.websocket..")
                // These adapt the framework on purpose: they read servlet requests or build Spring responses.
                .and().doNotHaveSimpleName("EchoResponses")
                .and().doNotHaveSimpleName("HttpbinResponses")
                .and().doNotHaveSimpleName("RequestBodyParser")
                .and().doNotHaveSimpleName("RequestInfo")
                .and().doNotHaveSimpleName("ServeContent")
                .and().doNotHaveSimpleName("EchoUpgradeHandler")
                .and().areNotNestedClasses()
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.servlet..",
                        "org.apache.catalina..", "org.apache.tomcat..")
                .because("the Go ports are the riskiest code (docs/testing/RISK-REGISTER.md R8-R11) and must stay "
                        + "testable with plain unit tests");
        rule.check(classes);
    }

    /**
     * No controller uses another controller. Shared logic belongs in {@code support}.
     */
    @Test
    @DisplayName("Controllers do not call each other; shared logic lives in support")
    void controllersAreIndependent() {
        ArchCondition<JavaClass> notDependOnOtherControllers =
                new ArchCondition<>("not depend on other controllers") {
                    @Override
                    public void check(JavaClass controller, ConditionEvents events) {
                        for (Dependency d : controller.getDirectDependenciesFromSelf()) {
                            JavaClass target = d.getTargetClass();
                            if (!target.equals(controller) && isController(target)) {
                                events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                            }
                        }
                    }
                };
        classes().that().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                .should(notDependOnOtherControllers)
                .check(classes);
    }

    /**
     * Whether a class is a controller. {@code @RestController} is itself meta-annotated with {@code @Controller}, so
     * annotation types are excluded.
     */
    private static boolean isController(JavaClass c) {
        return !c.isAnnotation() && (c.isAnnotatedWith(RestController.class) || c.isAnnotatedWith(Controller.class));
    }

    /**
     * Dependencies come through constructors, so every class can be built in a test without Spring.
     */
    @Test
    @DisplayName("Constructor injection only (no @Autowired fields)")
    void noFieldInjection() {
        noFields().should().beAnnotatedWith(Autowired.class).check(classes);
    }

    /**
     * Every controller lives in the {@code web} package, where a reader expects to find it.
     */
    @Test
    @DisplayName("Controllers live in the web package")
    void controllersInWeb() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("io.qaplayground.server.web..")
                .check(classes);
    }
}
