package dev.sergiomarin.sitewatch.auth;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import dev.sergiomarin.sitewatch.config.JwtProperties;
import dev.sergiomarin.sitewatch.user.AppUser;

/** Issues signed access tokens. The subject is the user id; the email travels as a claim. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final AccessProperties access;

    public TokenService(JwtEncoder encoder, JwtProperties properties, AccessProperties access) {
        this.encoder = encoder;
        this.properties = properties;
        this.access = access;
    }

    public TokenResponse issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail());
        if (access.isDemo(user.getEmail())) {
            builder.claim("readOnly", true);
        }
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return TokenResponse.bearer(token, expiresAt);
    }
}
