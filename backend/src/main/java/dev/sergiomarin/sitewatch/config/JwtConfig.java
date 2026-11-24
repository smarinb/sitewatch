package dev.sergiomarin.sitewatch.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    private final SecretKey key;
    private final JwtProperties properties;

    JwtConfig(JwtProperties properties) {
        this.properties = properties;
        this.key = toKey(properties.secret());
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static SecretKey toKey(String base64Secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("sitewatch.jwt.secret (JWT_SECRET) must be Base64-encoded", e);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("sitewatch.jwt.secret (JWT_SECRET) must decode to at least "
                    + MIN_KEY_BYTES + " bytes; generate one with: openssl rand -base64 32");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
