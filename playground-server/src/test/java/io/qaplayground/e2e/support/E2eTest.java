package io.qaplayground.e2e.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a browser journey. The test gets a {@link org.openqa.selenium.WebDriver} parameter from
 * {@link BrowserExtension}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(BrowserExtension.class)
public @interface E2eTest {
}
