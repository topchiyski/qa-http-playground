package io.qaplayground.server;

import io.qaplayground.server.config.PlaygroundProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * qa-http-playground: an HTTP server you can practise API testing and load testing against.
 *
 * <p>It speaks the httpbin API, runs on your own machine, and needs nothing but a JDK. It is built to be poked,
 * broken and measured. That also makes it unsafe in public: run it on local and lab networks only, never on the
 * internet.
 */
@SpringBootApplication
@EnableConfigurationProperties(PlaygroundProperties.class)
public class PlaygroundApplication {

    /**
     * Starts the playground: the API on port 8080, health and metrics on port 8081.
     */
    public static void main(String[] args) {
        SpringApplication.run(PlaygroundApplication.class, args);
    }
}
