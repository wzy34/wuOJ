package dev.wuoj.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Also needed without a web server, by the user importer. */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    /** bcrypt, with the algorithm stored as a {@code {bcrypt}} prefix so it can be changed later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
