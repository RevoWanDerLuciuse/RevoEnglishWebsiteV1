package org.example.revoenglishwebsite.config;

import org.example.revoenglishwebsite.account.PortalAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Static asset caching and the student-portal access check.
 * <p>
 * CSS, fonts and images get content-hashed URLs and a long cache lifetime (see {@code spring.web.resources}
 * in application.yaml). JavaScript modules import each other by relative path, which content hashing
 * would not rewrite, so they are served without hashing and revalidated on every use instead
 * (cheap 304 responses thanks to Last-Modified).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PortalAccessInterceptor portalAccess;

    public WebConfig(PortalAccessInterceptor portalAccess) {
        this.portalAccess = portalAccess;
    }

    /** Private pages: re-check the account (active, no pending password change) on every request. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(portalAccess).addPathPatterns("/dashboard", "/dashboard/**", "/admin", "/admin/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.noCache().cachePublic());
    }
}
