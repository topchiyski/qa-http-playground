package io.qaplayground.integration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.qaplayground.testing.Endpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three views of "the API" must agree (risk R15).
 *
 * <p>The routes the server really registers, the test inventory ({@link Endpoints}, which drives the checklist and
 * traceability), and the OpenAPI document behind Swagger UI. A route added without the other two fails here, before
 * anyone ships a route that nothing tests or documents.
 */
class RoutesContractIT extends ServerIT {

    @Autowired
    ApplicationContext context;

    /**
     * Each route template with its allowed methods, as our controllers registered them in Spring MVC.
     */
    private Map<String, Set<String>> registeredRoutes() {
        Map<String, Set<String>> routes = new TreeMap<>();
        for (RequestMappingHandlerMapping mapping : context.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
                if (!e.getValue().getBeanType().getPackageName().startsWith("io.qaplayground.server.web")) {
                    continue;
                }
                Set<String> methods = e.getKey().getMethodsCondition().getMethods().stream()
                        .map(RequestMethod::name).collect(Collectors.toCollection(TreeSet::new));
                for (String pattern : e.getKey().getPatternValues()) {
                    if (!pattern.equals("/error")) {
                        routes.put(pattern, methods);
                    }
                }
            }
        }
        return routes;
    }

    /**
     * Leaves out OPTIONS and HEAD: the CORS filter answers OPTIONS, and Spring answers HEAD for every GET.
     */
    private static Set<String> normalise(Set<String> methods) {
        Set<String> m = new TreeSet<>(methods);
        m.remove("OPTIONS");
        if (m.contains("GET")) {
            m.remove("HEAD");
        }
        if (m.containsAll(Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "TRACE"))) {
            return Set.of(); // "any method", as an unrestricted @RequestMapping reports it
        }
        return m;
    }

    /**
     * The test inventory lists exactly the routes the server registers, with the same methods.
     */
    @Test
    @DisplayName("The test inventory lists exactly the routes the server registers")
    void inventoryMatchesRegisteredRoutes() {
        Map<String, Set<String>> inventory = new TreeMap<>();
        Endpoints.ROUTES.forEach(r -> inventory.put(r.template(), normalise(r.methods())));
        Map<String, Set<String>> registered = new TreeMap<>();
        registeredRoutes().forEach((k, v) -> registered.put(k, normalise(v)));
        assertThat(inventory).isEqualTo(registered);
    }

    /**
     * Every route appears in the OpenAPI document, with a summary.
     */
    @Test
    @DisplayName("Swagger UI documents every route, each with a summary")
    void openApiDocumentsEveryRoute() {
        JsonNode doc = JsonMapper.builder().build().readTree(get("/v3/api-docs").body());
        Set<String> documented = new HashSet<>();
        doc.get("paths").properties().forEach(e -> {
            documented.add(e.getKey());
            e.getValue().properties().forEach(op ->
                    assertThat(op.getValue().path("summary").asString())
                            .as("summary of %s %s", op.getKey(), e.getKey()).isNotBlank());
        });
        Set<String> inventory = Endpoints.ROUTES.stream().map(Endpoints.Route::template).collect(Collectors.toSet());
        assertThat(documented).isEqualTo(inventory);
    }
}
