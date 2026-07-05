package com.library.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Programmatic replacement for {@code web.xml}.
 *
 * <p>Spring's {@link org.springframework.web.SpringServletContainerInitializer} detects
 * implementations of {@link WebApplicationInitializer} via the Servlet 3.0
 * {@code ServletContainerInitializer} SPI and calls {@link #onStartup} during
 * application deployment.</p>
 *
 * <h3>Two-context hierarchy</h3>
 * <ol>
 *   <li><b>Root context</b> ({@link AppConfig}) — services, DAOs, connection pool,
 *       AOP aspects. Shared across the entire application.</li>
 *   <li><b>Servlet (child) context</b> ({@link WebMvcConfig}) — controllers,
 *       view resolvers, interceptors. Scoped to the {@link DispatcherServlet}.</li>
 * </ol>
 */
public class WebAppInitializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {

        // ----------------------------------------------------------------
        // 1. Root Application Context (services, DAOs, connection pool)
        // ----------------------------------------------------------------
        AnnotationConfigWebApplicationContext rootContext =
                new AnnotationConfigWebApplicationContext();
        rootContext.register(AppConfig.class);

        // Tie root context lifecycle to the ServletContext
        servletContext.addListener(new ContextLoaderListener(rootContext));

        // ----------------------------------------------------------------
        // 2. DispatcherServlet with its own child context (MVC layer)
        // ----------------------------------------------------------------
        AnnotationConfigWebApplicationContext servletContext2 =
                new AnnotationConfigWebApplicationContext();
        servletContext2.register(WebMvcConfig.class);

        DispatcherServlet dispatcherServlet = new DispatcherServlet(servletContext2);
        dispatcherServlet.setThrowExceptionIfNoHandlerFound(true);

        ServletRegistration.Dynamic registration =
                servletContext.addServlet("dispatcher", dispatcherServlet);
        registration.setLoadOnStartup(1);
        registration.addMapping("/");

        // ----------------------------------------------------------------
        // 3. Character encoding filter (UTF-8 for all requests/responses)
        // ----------------------------------------------------------------
        var encodingFilter = servletContext.addFilter(
                "characterEncodingFilter",
                new org.springframework.web.filter.CharacterEncodingFilter());
        ((jakarta.servlet.FilterRegistration.Dynamic) encodingFilter)
                .setInitParameter("encoding", "UTF-8");
        ((jakarta.servlet.FilterRegistration.Dynamic) encodingFilter)
                .setInitParameter("forceEncoding", "true");
        ((jakarta.servlet.FilterRegistration.Dynamic) encodingFilter)
                .addMappingForUrlPatterns(null, false, "/*");

        // ----------------------------------------------------------------
        // 4. HiddenHttpMethodFilter (enables PUT/DELETE from HTML forms)
        // ----------------------------------------------------------------
        var hiddenMethodFilter = servletContext.addFilter(
                "hiddenHttpMethodFilter",
                new org.springframework.web.filter.HiddenHttpMethodFilter());
        ((jakarta.servlet.FilterRegistration.Dynamic) hiddenMethodFilter)
                .addMappingForUrlPatterns(null, false, "/*");
    }
}
