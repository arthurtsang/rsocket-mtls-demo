package org.example;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties( prefix = "rsocket")
public class RSocketClientConfiguration {
    Remote remote;
    SSL ssl;
    JWT jwt;
}

@Data class Remote {
    int port = 3000;
    String host = "localhost";
}

@Data class SSL {
    Server server;
    Client client;
}

@Data class Server {
    String trustStoreName;
    String trustStorePassword;
}

@Data class Client {
    String keyStoreName;
    String keyStorePassword;
}

@Data class JWT {
    String audience;
    String subject;
}