package org.example;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.SharedKernel.Location;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Controller
public class RSocketController {

    /**
     * A sample request-response to read the request data (Location), the request metadata (annotated with @Header) and the principle object (annotated with @AuthenticationPrinciple)
     * The request data is actually ignored in this example and constructed a new Location object as reply.
     * @param request
     * @param metadata
     * @param user
     * @return
     */
    @SneakyThrows
    @MessageMapping("request-response")
    Mono<Location> requestResponse(Location request, @Headers Map<String, Object> metadata, @AuthenticationPrincipal JwtClaimAccessor user ) {
        log.info("Received request-response request: {}", request);
        log.info("Received request-response header: {}", metadata);
        log.info("Received request-response user details: {} {}", user.getSubject(), user.getClaim("scope"));
        return Mono.just(Location.newBuilder().setLocation("s3://newbucket/newfile\n").build());
    }

    @MessageMapping("fire-and-forget")
    Mono<Void> fireAndForget(Location request) {
        log.info( "location received is " + request.getLocation() );
        return Mono.never();
    }
}
