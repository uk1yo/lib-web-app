package com.library.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * Root Spring Application Configuration.
 *
 * <p>This is the <em>root</em> application context (parent context). It scans and
 * registers all non-web beans: services, DAOs, the connection pool, AOP aspects,
 * and utility components.</p>
 *
 * <ul>
 *   <li>{@code @Configuration}     — marks this as a Java-based Spring config class.</li>
 *   <li>{@code @ComponentScan}     — scans the entire {@code com.library} base package.
 *       The web layer ({@code com.library.controller}) will be picked up by the child
 *       {@link WebMvcConfig} context; scanning it here is harmless but it is explicitly
 *       narrowed to non-controller packages to keep concerns separate.</li>
 *   <li>{@code @PropertySource}    — loads {@code db.properties} from the classpath so
 *       that {@code @Value("${db.*}")} annotations in {@link ConnectionPool} work.</li>
 *   <li>{@code @EnableAspectJAutoProxy} — activates Spring AOP proxy creation for
 *       {@code @Aspect} classes (logging, auditing).</li>
 * </ul>
 *
 * <p><b>No XML configuration is used anywhere in this project.</b></p>
 */
@Configuration
@ComponentScan(basePackages = {
        "com.library.config",
        "com.library.service",
        "com.library.dao",
        "com.library.util",
        "com.library.exception"
})
@PropertySource("classpath:application.properties")
@EnableAspectJAutoProxy
public class AppConfig {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
