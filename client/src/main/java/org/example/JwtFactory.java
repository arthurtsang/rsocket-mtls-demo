package org.example;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * A singleton bean to generate JWT token
 * note that token has a 2-min expiration time
 */
@RequiredArgsConstructor
public class JwtFactory {
    protected final RSocketClientConfiguration config;

    @SneakyThrows
    public String getJWT() {
        // a shared secret between the client and server, should be a parameter fetched from a secure store in production
        byte[] sharedSecret = "S5oG45AXChoQdVC4oBjiVXhtcOcPblNQ".getBytes();

        JWSSigner signer = new MACSigner(sharedSecret);

        // Payload
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer("client")
                .audience(config.jwt.audience)
                .subject(config.jwt.subject) // username
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("scope", "USER") // roles
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
        JWSObject jwsObject = new JWSObject( header, claimsSet.toPayload() );

        jwsObject.sign(signer);

        // Serialize the JWS to compact form
        return jwsObject.serialize();
    }
}
