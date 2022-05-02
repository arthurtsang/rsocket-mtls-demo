package org.example;

import com.google.protobuf.Any;
import reactor.core.publisher.FluxSink;

import java.util.function.Consumer;

/**
 * A Consumer to a FluxSink with Any Protobuf type
 * It is used to pass into rsocket channel so it has a reference to the FluxSink it created
 * and a method to publish data to the FluxSink
 */
public class ProtoFluxSinkConsumer implements Consumer<FluxSink<Any>> {
    private FluxSink<Any> anyFluxSink;

    @Override
    public void accept(FluxSink<Any> anyFluxSink) {
        this.anyFluxSink = anyFluxSink;
    }

    public void publishProtoAny(Any any) {
        this.anyFluxSink.next(any);
    }
}
