package org.example;

import io.rsocket.loadbalance.LoadbalanceTarget;
import io.rsocket.loadbalance.RoundRobinLoadbalanceStrategy;
import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.http.codec.protobuf.ProtobufEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

import static org.springframework.messaging.rsocket.RSocketRequester.builder;

@SpringBootApplication
@EnableScheduling
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
        Flux<List<LoadbalanceTarget>> serversMono = Mono.just(List.of(LoadbalanceTarget.from("client", tcpClientTransportFactory.getTcpClientTransport()))).repeatWhen(f->f.delayElements(Duration.ofSeconds(2)));
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
                .transports(serversMono, new RoundRobinLoadbalanceStrategy());
    }

    @Bean
    CloudEventFluxSinkConsumer cloudEventFluxSinkConsumer() {
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
