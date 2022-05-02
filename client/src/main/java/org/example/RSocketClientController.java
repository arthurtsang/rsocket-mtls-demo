package org.example;

import com.google.protobuf.Any;
import io.cloudevents.v1.proto.CloudEvent;
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

import java.util.UUID;

@RestController
@RequestMapping("/")
@Slf4j
public class RSocketClientController {
    @Autowired
    RSocketRequester requester;
    @Autowired
    JwtFactory jwtFactory;
    @Autowired
    CloudEventHandler cloudEventHandler;

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
                .metadata(jwtFactory.getJWT(), MimeType.valueOf("message/x.rsocket.authentication.bearer.v0"))
                .data(location)
                .retrieveMono(Location.class)
                .doOnError(e -> log.error(e.getMessage(), e));
        return result.map(Location::getLocation);
    }

    @GetMapping(path = "/fire-and-forget")
    public Mono<Void> fireAndForget() {
        Location location = Location.newBuilder().setLocation("s3://bucket/file").build();
        return requester.route("fire-and-forget").data(location).send();
    }

    @SneakyThrows
    @GetMapping(path = "/handler")
    public void handler() {
        Location location = Location.newBuilder().setLocation("s3://bucket/fromapi").build();
        CloudEvent cloudEvent = CloudEvent.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setType("com.example.command.location.v1")
                .setSource("http://localhost:7001/handler")
                .setProtoData(Any.pack(location))
                .build();
        cloudEventHandler.handle(Any.pack(cloudEvent));
    }
}
