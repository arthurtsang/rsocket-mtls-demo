package org.example;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.codec.protobuf.ProtobufDecoder;
import org.springframework.http.codec.protobuf.ProtobufEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.util.MimeType;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.springframework.messaging.rsocket.RSocketRequester.Builder;
import static org.springframework.messaging.rsocket.RSocketRequester.builder;

@SpringBootApplication
public class RSocketClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(RSocketClientApplication.class, args);
    }

    @Value("${rsocket.remote.port}")
    int remotePort = 3000;

    @Value("${rsocket.remote.host}")
    String remoteHost = "localhost";

    @Value("${rsocket.ssl.server.trustStoreFileName}")
    String trustStoreName;

    @Value("${rsocket.ssl.server.trustStorePassword}")
    String trustStorePassword;

    @Value("${rsocket.ssl.client.keyStoreFileName}")
    String keyStoreName;

    @Value("${rsocket.ssl.client.keyStorePassword}")
    String keyStorePassword;

    List<String> pfs_ciphers = Arrays.asList("TLS_EDCHE_RSA_WITH_AES_128_CBC_SHA", "TLS_EDCHE_RSA_WITH_AES_256_CBC_SHA", "TLS_EDCHE_RSA_WITH_3DES_EDE_CBC_SHA");

    @SneakyThrows
    @Bean
    public RSocketRequester getRSocketRequester() {
        Builder builder = builder();

        SslProvider sslProvider = SslProvider.builder().sslContext(getSSLContext()).build();
        TcpClient tcpClient = TcpClient.create().host(remoteHost).port(remotePort).secure(sslProvider);
        TcpClientTransport tcpClientTransport = TcpClientTransport.create(tcpClient);
        return builder.rsocketStrategies(
                    strategy -> {
                        strategy.decoder(new ProtobufDecoder());
                        strategy.encoder(new ProtobufEncoder());
                    })
                .dataMimeType(new MimeType("application", "x-protobuf"))
                .rsocketConnector(
                        connector -> connector
                                .reconnect(Retry.indefinitely())
                                .keepAlive(Duration.ofSeconds(2),Duration.ofDays(2))
                )
                .transport(tcpClientTransport);
    }

    @SneakyThrows
    private SslContext getSSLContext() throws IOException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
        tmf.init( this.getKeystore(trustStoreName, trustStorePassword) );

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX", "SunJSSE");
        kmf.init(this.getKeystore(keyStoreName, keyStorePassword), keyStorePassword.toCharArray());

        SslContextBuilder builder = SslContextBuilder.forClient().keyManager(kmf).trustManager(tmf); //.ciphers(pfs_ciphers);
        return builder.build();
    }

    @SneakyThrows
    private KeyStore getKeystore(String filename, String password) {
        // Load keystore
        InputStream is = ClassLoader.getSystemResourceAsStream(filename);
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(is, password.toCharArray());
        return keystore;
    }
}
