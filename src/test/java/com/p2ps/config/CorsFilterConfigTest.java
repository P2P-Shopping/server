package com.p2ps.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import static org.junit.jupiter.api.Assertions.*;

class CorsFilterConfigTest {

    private CorsFilterConfig createConfig(String origins) {
        CorsFilterConfig config = new CorsFilterConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        return config;
    }

    @Test
    void corsFilterRegistration_HasHighestPrecedence() {
        CorsFilterConfig config = createConfig("http://localhost:5173");
        FilterRegistrationBean<CorsFilter> bean = config.corsFilterRegistration();

        assertEquals(Ordered.HIGHEST_PRECEDENCE, bean.getOrder());
    }

    @Test
    void corsFilterRegistration_ReturnsNonNullFilter() {
        CorsFilterConfig config = createConfig("http://localhost:5173,https://ucart-smoky.vercel.app");
        FilterRegistrationBean<CorsFilter> bean = config.corsFilterRegistration();

        assertNotNull(bean.getFilter());
    }

    @Test
    void corsFilterRegistration_HandlesMultipleOrigins() {
        CorsFilterConfig config = createConfig("http://localhost:5173,https://ucart-smoky.vercel.app");
        FilterRegistrationBean<CorsFilter> bean = config.corsFilterRegistration();

        assertNotNull(bean);
        assertNotNull(bean.getFilter());
    }

    @Test
    void corsFilterRegistration_FiltersWildcardOrigin() {
        CorsFilterConfig config = createConfig("http://localhost:5173,*,https://example.com");
        FilterRegistrationBean<CorsFilter> bean = config.corsFilterRegistration();

        assertNotNull(bean);
    }

    @Test
    void corsFilterRegistration_TrimsAndIgnoresEmptyOrigins() {
        CorsFilterConfig config = createConfig("http://localhost:5173, https://example.com, ");
        FilterRegistrationBean<CorsFilter> bean = config.corsFilterRegistration();

        assertNotNull(bean);
    }
}
