package org.example;

import com.google.protobuf.Any;
import io.cloudevents.v1.proto.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import reactor.core.publisher.FluxSink;

import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A consumer fluxsink where a JMS queue is publishing data to
 */
@Slf4j
public class RabbitFluxSinkConsumer implements Consumer<FluxSink<CloudEvent>>, Closeable {
    private final SimpleMessageListenerContainer simpleMessageListenerContainer;
    private FluxSink<CloudEvent> cloudEventFluxSink;
    /**
     * Listen to a queue (queueName) and pass the content as SharedKernal.Location CloudEvent to the fluxsink
     * @param queueName
     * @param simpleMessageListenerContainerFactory
     */
    public RabbitFluxSinkConsumer(String  queueName, SimpleMessageListenerContainerFactory simpleMessageListenerContainerFactory) {
        simpleMessageListenerContainer = simpleMessageListenerContainerFactory.createNewMessageListenerContainer(queueName, message -> {
            try {
                String location = new String(message.getBody());
                log.info( "received message from rabbit {}", location );
                if( cloudEventFluxSink != null ) {
                    SharedKernel.Location newLocation = SharedKernel.Location.newBuilder().setLocation(location).build();
                    CloudEvent cloudEvent = CloudEvent.newBuilder()
                            .setId(UUID.randomUUID().toString())
                            .setType("com.example.command.location.v1")
                            .setSource("http://localhost:3000/channel")
                            .setProtoData(Any.pack(newLocation))
                            .build();
                    cloudEventFluxSink.next(cloudEvent);
                } else
                    log.error( "cloudEventFluxSink is null");
            } catch (Exception e) {
                log.error("jms flux sink error: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void accept(FluxSink<CloudEvent> cloudEventFluxSink) {
        this.cloudEventFluxSink = cloudEventFluxSink;
    }

    @Override
    public void close() throws IOException {
        simpleMessageListenerContainer.stop();
    }
}
