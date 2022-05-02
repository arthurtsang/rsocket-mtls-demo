package org.example;

import com.google.protobuf.Any;
import io.cloudevents.v1.proto.CloudEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.example.SharedKernel.Location;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Controller
public class RSocketController {

    @Autowired
    SimpleMessageListenerContainerFactory simpleMessageListenerContainerFactory;
    @Autowired RabbitTemplate rabbitTemplate;

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
    Mono<Location> requestResponse(Location request, @Headers Map<String, Object> metadata, @AuthenticationPrincipal JwtClaimAccessor user, @CurrentSecurityContext SecurityContext securityContext) {
        log.info("Received request-response request: {}", request);
        log.info("Received request-response header: {}", metadata);
        log.info("Received request-response user details: {} {}", user.getSubject(), user.getClaim("scope"));
        log.info("Received request-response security context {} {}", securityContext.getAuthentication().getName(), securityContext.getAuthentication().getAuthorities());
        return Mono.just(Location.newBuilder().setLocation("s3://newbucket/newfile\n").build());
    }

    @MessageMapping("fire-and-forget")
    Mono<Void> fireAndForget(Location request) {
        log.info( "location received is " + request.getLocation() );
        return Mono.never();
    }


    @MessageMapping("channel")
    Flux<CloudEvent> channel(Flux<CloudEvent> cloudEventFlux, @AuthenticationPrincipal JwtClaimAccessor user) {
        String queueName = user.getSubject();
        String jmsRoutingKey = user.getAudience().get(0);
        log.info( "established connection with {} to {}", queueName, jmsRoutingKey);
        RabbitFluxSinkConsumer rabbitFluxSinkConsumer = new RabbitFluxSinkConsumer(queueName, simpleMessageListenerContainerFactory);
        cloudEventFlux
                .doOnError(e->log.error("channel error: " + e.getMessage(), e))
                .doOnCancel(()->log.info("channel cancelled"))
                .doAfterTerminate(()->{
                    log.info("connection terminated");
                    try {
                        rabbitFluxSinkConsumer.close();
                    } catch (IOException e) {
                        log.error("failed closing rabbit listen when rsocket channel terminated\n" + e.getMessage(), e);
                    }
                })
                .subscribe( cloudEvent -> {
                    try {
                        Location location = cloudEvent.getProtoData().unpack(Location.class);
                        log.info("received cloud event {}" + cloudEvent);
                        rabbitTemplate.convertAndSend(jmsRoutingKey, location.getLocation() );
                    }catch ( Exception e ) {
                        log.error( "error reading channel " + e.getMessage(), e);
                    }
                });
        return Flux.create(rabbitFluxSinkConsumer);
    }

}
