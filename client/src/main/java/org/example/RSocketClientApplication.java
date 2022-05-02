package org.example;

import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.http.codec.protobuf.ProtobufEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeType;
import reactor.util.retry.Retry;

import java.time.Duration;

import static org.springframework.messaging.rsocket.RSocketRequester.builder;

@SpringBootApplication
public class RSocketClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(RSocketClientApplication.class, args);
    }

    @Bean
    public TcpClientTransportFactory tcpClientTransportFactory(RSocketClientConfiguration config) {
        return new TcpClientTransportFactory(config);
    }

    @SneakyThrows
    @Bean
    public RSocketRequester getRSocketRequester(TcpClientTransportFactory tcpClientTransportFactory) {
        return builder().rsocketStrategies(
                        strategy -> {
                            strategy.decoder(new ProtobufDecoder());
                            strategy.encoder(new ProtobufEncoder());
                        })
                .dataMimeType(new MimeType("application", "x-protobuf"))
                .rsocketConnector(
                        connector -> connector
                                .reconnect(Retry.indefinitely())
                                .keepAlive(Duration.ofSeconds(2), Duration.ofDays(2))
                )
                .transport(tcpClientTransportFactory.getTcpClientTransport());
    }

    @Bean
    CloudEventFluxSinkConsumer protoFluxSink() {
        return new CloudEventFluxSinkConsumer();
    }

    @Bean
    CloudEventHandler cloudEventHandler(CloudEventFluxSinkConsumer cloudEventFluxSinkConsumer) {
        return new CloudEventHandler(cloudEventFluxSinkConsumer);
    }

    @Bean
    JwtFactory jwtFactory(RSocketClientConfiguration config) {
        return new JwtFactory(config);
    }
}
