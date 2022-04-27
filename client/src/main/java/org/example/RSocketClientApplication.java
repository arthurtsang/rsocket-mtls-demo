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

import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertificateException;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
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

    private SslContext getSSLContext() throws IOException {
        SslContext sslContext = null;
        try
        {
            // truststore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX", "SunJSSE");
            // initialize certification path checking for the offered certificates and revocation checks against CLRs
            CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
            PKIXRevocationChecker rc = (PKIXRevocationChecker)cpb.getRevocationChecker();
            rc.setOptions(EnumSet.of(
                    //PKIXRevocationChecker.Option.SOFT_FAIL, // won't fail if it can't access the CRL distribution (testing only)
                    PKIXRevocationChecker.Option.PREFER_CRLS, // prefer CLR over OCSP
                    PKIXRevocationChecker.Option.ONLY_END_ENTITY,
                    PKIXRevocationChecker.Option.NO_FALLBACK)); // don't fall back to OCSP checking

            PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(this.getKeystore(trustStoreName, trustStorePassword), new X509CertSelector());
            pkixParams.addCertPathChecker(rc);

            tmf.init( new CertPathTrustManagerParameters(pkixParams) );

            // keystore holding client certificate
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX", "SunJSSE");
            kmf.init(this.getKeystore(keyStoreName, keyStorePassword), keyStorePassword.toCharArray());

            SslContextBuilder builder = SslContextBuilder.forClient().keyManager(kmf).trustManager(tmf); //.ciphers(pfs_ciphers);

            // build context
            sslContext = builder.build();
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException | KeyStoreException | IllegalStateException | UnrecoverableKeyException | CertificateException | InvalidAlgorithmParameterException e)
        {
            throw new IOException("Unable to create client TLS context", e);
        }
        return sslContext;
    }

    private KeyStore getKeystore(String filename, String password) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        // Load keystore
        InputStream is = ClassLoader.getSystemResourceAsStream(filename);
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(is, password.toCharArray());
        return keystore;
    }
}
