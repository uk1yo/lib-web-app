package com.library.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Spring MVC (Web layer) Configuration — child context of {@link AppConfig}.
 *
 * <p>Configures:</p>
 * <ul>
 *   <li>Thymeleaf template engine and view resolver</li>
 *   <li>Static resource handlers (CSS, JS, images)</li>
 *   <li>i18n: {@link MessageSource}, {@link LocaleResolver}, {@link LocaleChangeInterceptor}</li>
 * </ul>
 *
 * <p>Only the {@code com.library.controller} package is scanned here
 * to keep the MVC child context focused on web concerns.</p>
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {"com.library.controller", "com.library.interceptor"})
public class WebMvcConfig implements WebMvcConfigurer {

    // =========================================================================
    // Thymeleaf
    // =========================================================================

    /**
     * Resolves template paths under {@code /WEB-INF/templates/} with
     * {@code .html} suffix and UTF-8 encoding.
     */
    @Bean
    public SpringResourceTemplateResolver templateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Cache disabled in dev — set to true in production
        resolver.setCacheable(false);
        return resolver;
    }

    /**
     * Wires together the template resolver and the Spring-aware template engine.
     * The engine understands Spring EL expressions and Spring's {@link MessageSource}.
     */
    @Bean
    public SpringTemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver());
        engine.setEnableSpringELCompiler(true);
        // Add message source so #{...} expressions in templates resolve i18n keys
        engine.setMessageSource(messageSource());
        return engine;
    }

    /**
     * Thymeleaf view resolver — replaces Spring MVC's default InternalResourceViewResolver.
     */
    @Bean
    public ThymeleafViewResolver viewResolver() {
        ThymeleafViewResolver resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(templateEngine());
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setOrder(1);
        return resolver;
    }

    // =========================================================================
    // Static Resources
    // =========================================================================

    /**
     * Serves static assets (CSS, JS, images, fonts) from {@code /static/} classpath path.
     * Accessible via {@code /static/**} URL pattern.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    // =========================================================================
    // i18n — Internationalization (EN, RU, KZ)
    // =========================================================================

    /**
     * Loads message bundles: {@code messages_en.properties}, {@code messages_ru.properties},
     * {@code messages_kz.properties} from the classpath.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    /**
     * Stores the user's selected locale in a cookie so it persists across sessions.
     * Default locale is English.
     */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("lang");
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    /**
     * Intercepts requests with a {@code lang} query parameter (e.g. {@code ?lang=ru})
     * and switches the locale accordingly. Works together with {@link CookieLocaleResolver}.
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    private final com.library.interceptor.SecurityInterceptor securityInterceptor;
    private final com.library.interceptor.RoleInterceptor roleInterceptor;
    private final com.library.interceptor.LockCheckInterceptor lockCheckInterceptor;

    public WebMvcConfig(
            com.library.interceptor.SecurityInterceptor securityInterceptor,
            com.library.interceptor.RoleInterceptor roleInterceptor,
            com.library.interceptor.LockCheckInterceptor lockCheckInterceptor) {
        this.securityInterceptor = securityInterceptor;
        this.roleInterceptor = roleInterceptor;
        this.lockCheckInterceptor = lockCheckInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // i18n interceptor — must be registered first
        registry.addInterceptor(localeChangeInterceptor());

        registry.addInterceptor(securityInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/register", "/static/**");

        registry.addInterceptor(lockCheckInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/register", "/static/**");

        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/register", "/static/**");
    }
}
