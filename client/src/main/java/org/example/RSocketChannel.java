package org.example;

import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Timestamp;
import io.cloudevents.v1.proto.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.UUID;

/**
 * setup a rsocket channel with the server
 * The input flux has a FluxSink where the CloudEventHandler could publish cloudevents to
 * The output flux is subscribed and pass to a CloudEventHandler to process
 */
@Component
@Slf4j
public class RSocketChannel implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired RSocketRequester requester;
    @Autowired JwtFactory jwtFactory;
    @Autowired CloudEventFluxSinkConsumer cloudEventFluxSinkConsumer;
    @Autowired CloudEventHandler cloudEventHandler;
    @Autowired RSocketClientConfiguration config;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        CloudEvent init = CloudEvent.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSource("tcp://"+config.jwt.subject)
                .setType("com.example.location.v1")
                .setProtoData(Any.pack(SharedKernel.Location.newBuilder().setLocation("init data").build()))
                .putAttributes("time",
                        CloudEvent.CloudEventAttributeValue.newBuilder().setCeTimestamp(
                                Timestamp.newBuilder().setNanos(
                                        Clock.systemDefaultZone().instant().getNano()
                                ).build()
                        ).build())
                .build();
        requester.route("channel")
                .metadata(jwtFactory.getJWT(), MimeType.valueOf("message/x.rsocket.authentication.bearer.v0"))
                .data(Flux.create(cloudEventFluxSinkConsumer).startWith(init))
                .retrieveFlux(CloudEvent.class)
                .doOnError(e->log.error("channel error: " + e.getMessage(), e))
                .doAfterTerminate(()->log.info("connection terminated"))
                .subscribe(cloudEventHandler::handle);
    }
}
