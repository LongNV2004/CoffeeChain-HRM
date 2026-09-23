package com.example.coffee_hrm.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DatabaseUserDetailsService userDetailsService;
    private final RoleBasedRedirector roleBasedRedirector;

    @Value("${coffee-hrm.security.remember-me.key}")
    private String rememberMeKey;

    @Value("${coffee-hrm.security.remember-me.seconds:604800}")
    private int rememberMeSeconds;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CoffeeRememberMeServices rememberMeServices() {
        CoffeeRememberMeServices services = new CoffeeRememberMeServices(rememberMeKey, userDetailsService);
        services.setParameter("rememberMe");
        services.setCookieName("coffee-hrm-remember-me");
        services.setTokenValiditySeconds(rememberMeSeconds);
        return services;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            String dashboard = roleBasedRedirector.resolve(SecurityContextHolder.getContext().getAuthentication());
            String target = dashboard != null ? dashboard : "/login";
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CoffeeRememberMeServices rememberMeServices,
                                                   SecurityContextRepository securityContextRepository,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .rememberMe(remember -> remember
                        .rememberMeServices(rememberMeServices)
                        .key(rememberMeKey))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/coffee", "/login", "/logout", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error")
                        .permitAll()
                        .requestMatchers("/dashboard/admin", "/admin/training", "/admin/training/**").hasRole("ADMIN")
                        .requestMatchers("/dashboard/manager", "/training", "/training/**",
                                "/schedule/manager", "/schedule/manager/**",
                                "/availability/manager", "/availability/manager/**").hasRole("MANAGER")
                        .requestMatchers("/dashboard/staff", "/schedule/staff", "/schedule/staff/**",
                                "/availability", "/availability/**").hasRole("STAFF")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendRedirect(request.getContextPath() + "/login"))
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
