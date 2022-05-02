package org.example;

import com.google.protobuf.Any;
import io.cloudevents.v1.proto.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * A mock handler to return a random s3 location with a second delay
 * it ignores the incoming CloudEvent and returns a CloudEvent with the random s3 location
 */
@Slf4j
@RequiredArgsConstructor
public class CloudEventHandler {
    private final ProtoFluxSinkConsumer protoFluxSinkConsumer;

    @SneakyThrows
    public void handle(Any protoEvent) {
        CloudEvent cloudEvent = protoEvent.unpack(CloudEvent.class);
        log.info( "handler received: {}", cloudEvent );
        Thread.sleep(1000);
        String id = UUID.randomUUID().toString();
        SharedKernel.Location newLocatioin = SharedKernel.Location.newBuilder().setLocation("s3://bucket/"+ id).build();
        CloudEvent outEvent = CloudEvent.newBuilder()
                .setId(id)
                .setType("com.example.command.location.v1")
                .setSource("http://localhost:7001/handler")
                .setProtoData(Any.pack(newLocatioin))
                .build();
        log.info( "sending new location {}", outEvent );
        protoFluxSinkConsumer.publishProtoAny(Any.pack(outEvent));
    }
}
