package org.example;

import io.cloudevents.v1.proto.CloudEvent;
import reactor.core.publisher.FluxSink;

import java.util.function.Consumer;

/**
 * A Consumer to a FluxSink with Any Protobuf type
 * It is used to pass into rsocket channel so it has a reference to the FluxSink it created
 * and a method to publish data to the FluxSink
 */
public class CloudEventFluxSinkConsumer implements Consumer<FluxSink<CloudEvent>> {
    private FluxSink<CloudEvent> cloudEventFluxSink;

    @Override
    public void accept(FluxSink<CloudEvent> cloudEventFluxSink) {
        this.cloudEventFluxSink = cloudEventFluxSink;
    }

    public void publish(CloudEvent cloudEvent) {
        this.cloudEventFluxSink.next(cloudEvent);
    }
}
