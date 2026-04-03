package pe.openstrategy.databaseproject.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.openstrategy.databaseproject.infrastructure.security.BasicAuthCredentialExtractor;
import pe.openstrategy.databaseproject.port.out.CredentialExtractor;

/**
 * Security-related bean configurations.
 */
@Configuration
public class SecurityConfig {

    // @Bean
    // public SecurityFilterChain securityFilterChain(HttpSecurity http) throws
    // Exception {
    // http
    // .csrf(csrf -> csrf.disable())
    // .authorizeHttpRequests(auth -> auth
    // .anyRequest().permitAll());

    // return http.build();
    // }

    @Bean
    public CredentialExtractor credentialExtractor(BasicAuthCredentialExtractor basicAuthCredentialExtractor) {
        return basicAuthCredentialExtractor;
    }

}