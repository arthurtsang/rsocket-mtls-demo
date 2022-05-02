package org.example;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.rsocket.transport.netty.client.TcpClientTransport;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;

@RequiredArgsConstructor
public class TcpClientTransportFactory {
    private final RSocketClientConfiguration config;

    @SneakyThrows
    public TcpClientTransport getTcpClientTransport() throws IOException {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
        tmf.init(this.getKeystore(config.ssl.server.trustStoreName, config.ssl.server.trustStorePassword));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX", "SunJSSE");
        kmf.init(this.getKeystore(config.ssl.client.keyStoreName, config.ssl.client.keyStorePassword), config.ssl.client.keyStorePassword.toCharArray());

        SslContext sslContext = SslContextBuilder.forClient().keyManager(kmf).trustManager(tmf).build();
        SslProvider sslProvider = SslProvider.builder().sslContext(sslContext).build();
        TcpClient tcpClient = TcpClient.create().host(config.remote.host).port(config.remote.port).secure(sslProvider);
        return TcpClientTransport.create(tcpClient);
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