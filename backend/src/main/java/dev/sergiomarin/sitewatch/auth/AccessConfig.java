package dev.sergiomarin.sitewatch.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccessProperties.class)
class AccessConfig {
}
