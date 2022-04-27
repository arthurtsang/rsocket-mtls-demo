package org.example;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.SharedKernel.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@RestController
@RequestMapping("/")
@Slf4j
public class RSocketClientController {
    @Autowired
    RSocketRequester requester;

    @GetMapping(path = "/request-response")
    public Mono<String> requestResponse() {
        Location location = Location.newBuilder().setLocation("s3://bucket/file").build();
        Mono<Location> result = requester
                .route("request-response")
                /*
                 * BearerTokenMetadata.BEARER_AUTHENTICATION_MIME_TYPE is deprecated, but using MESSAGE_RSOCKET_AUTHENTICATION won't trigger the JWT authentication manager on the server.
                 * likely have to use simple authentication instead, but a simple test failed, didn't trigger the authentication manager registered through simpleAuthentication
                 * -> .metadata(getJWT(), MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.getString()))
                 * also, do not add spring-security-rsocket for the BearerTokenMetadata class as this would turn on spring security on this server
                 */
                .metadata(getJWT(), MimeType.valueOf("message/x.rsocket.authentication.bearer.v0"))
                .data(location)
                .retrieveMono(Location.class)
                .doOnError(e->log.error(e.getMessage(), e));
        return result.map(Location::getLocation);
    }

    @GetMapping( path = "/fire-and-forget")
    public Mono<Void> fireAndForget() {
        Location location = Location.newBuilder().setLocation("s3://bucket/file").build();
        return requester.route("fire-and-forget").data(location).send();
    }

    @SneakyThrows
    private String getJWT() {
        // a shared secret between the client and server, should be a parameter fetched from a secure store in production
        byte[] sharedSecret = "S5oG45AXChoQdVC4oBjiVXhtcOcPblNQ".getBytes();

        JWSSigner signer = new MACSigner(sharedSecret);

        // Payload
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer("client")
                .audience("server")
                .subject("user") // username
                .expirationTime(Date.from(Instant.now().plusSeconds(120)))
                .claim("scope", "USER") // roles
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
        JWSObject jwsObject = new JWSObject( header, claimsSet.toPayload() );

        jwsObject.sign(signer);

        // Serialize the JWS to compact form
        return jwsObject.serialize();
    }
}
