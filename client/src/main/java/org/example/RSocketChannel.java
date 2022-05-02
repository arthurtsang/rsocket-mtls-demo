package org.example;

import com.google.protobuf.Any;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

/**
 * setup a rsocket channel with the server
 * The input flux has a FluxSink where the CloudEventHandler could publish cloudevents to
 * The output flux is subscribed and pass to a CloudEventHandler to process
 */
@Component
@Slf4j
public class RSocketChannel implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    RSocketRequester requester;
    @Autowired
    JwtFactory jwtFactory;
    @Autowired
    ProtoFluxSinkConsumer protoFluxSinkConsumer;
    @Autowired
    CloudEventHandler cloudEventHandler;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        requester.route("channel")
                .metadata(jwtFactory.getJWT(), MimeType.valueOf("message/x.rsocket.authentication.bearer.v0"))
                .data(Flux.create(protoFluxSinkConsumer))
                .retrieveFlux(Any.class)
                .doOnError(e->log.error("channel error: " + e.getMessage(), e))
                .doAfterTerminate(()->log.info("connection terminated"))
                .subscribe(cloudEventHandler::handle);
    }
}
