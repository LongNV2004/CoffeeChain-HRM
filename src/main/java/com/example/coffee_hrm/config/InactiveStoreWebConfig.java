package com.example.coffee_hrm.config;

import com.example.coffee_hrm.security.InactiveStoreInterceptor;
import com.example.coffee_hrm.security.StoreAccessGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InactiveStoreWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<StoreAccessGuard> storeAccessGuard;

    public InactiveStoreWebConfig(ObjectProvider<StoreAccessGuard> storeAccessGuard) {
        this.storeAccessGuard = storeAccessGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        StoreAccessGuard guard = storeAccessGuard.getIfAvailable();
        if (guard == null) {
            return;
        }
        registry.addInterceptor(new InactiveStoreInterceptor(guard))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/coffee",
                        "/login",
                        "/logout",
                        "/store-inactive",
                        "/account/password",
                        "/error",
                        "/favicon.ico",
                        "/css/**",
                        "/js/**",
                        "/images/**");
    }
}
