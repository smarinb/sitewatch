package dev.sergiomarin.sitewatch.verification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VerificationProperties.class)
class VerificationConfig {
}
