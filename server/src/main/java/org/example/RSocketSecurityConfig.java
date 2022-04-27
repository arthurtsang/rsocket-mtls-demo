package org.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.messaging.handler.invocation.reactive.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableRSocketSecurity
@EnableReactiveMethodSecurity
@Slf4j
public class RSocketSecurityConfig {

    /**
     * add AuthenticationPrincipleArgumentResolver to the message handler
     * The AuthenticationPrincipleArgumentResolver will look for argument annotated with AuthenticationPrinciple and verify if the type is assignable to the actual principle object
     *
     * Note the principle object could be different if different authentication method is used.
     * In this case, using JWT, the principle object is `org.springframework.security.oauth2.jwt.JWT` but unfortunately this class is not exported, thus we'll have to use `JwtCliamAccessor` instead.
     * and for simple authentication, it's likely to be a `UserDetails` object instead.
     * @param strategies
     * @return RSocketMessageHandler
     */
    @Bean
    RSocketMessageHandler messageHandler(RSocketStrategies strategies) {
        RSocketMessageHandler handler = new RSocketMessageHandler();
        handler.getArgumentResolverConfigurer().addCustomResolver(new AuthenticationPrincipalArgumentResolver());
        handler.setRSocketStrategies(strategies);
        return handler;
    }

    /**
     * decode JWT and verify the signature with the shared secret
     * @return ReactiveJwtDecoder
     * @throws Exception
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec("S5oG45AXChoQdVC4oBjiVXhtcOcPblNQ".getBytes(), mac.getAlgorithm());

        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * returns a JwtReactiveAuthenticationManager wich the decoder set to the `reactiveJwtDecoder` bean
     * @param reactiveJwtDecoder
     * @return
     */
    @Bean
    public JwtReactiveAuthenticationManager jwtReactiveAuthenticationManager(ReactiveJwtDecoder reactiveJwtDecoder) {
        JwtReactiveAuthenticationManager jwtReactiveAuthenticationManager = new JwtReactiveAuthenticationManager(reactiveJwtDecoder);

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        jwtReactiveAuthenticationManager.setJwtAuthenticationConverter( new ReactiveJwtAuthenticationConverterAdapter(authenticationConverter));
        return jwtReactiveAuthenticationManager;
    }

    /**
     * setup RSocketSecurity, all requests needs to be authenticated and jwt to use the `jwtReactiveAuthenticateManager` bean.
     * @param rSocketSecurity
     * @param jwtReactiveAuthenticationManager
     * @return
     */
    @Bean
    PayloadSocketAcceptorInterceptor rSocketInterceptor(RSocketSecurity rSocketSecurity, JwtReactiveAuthenticationManager jwtReactiveAuthenticationManager) {
        rSocketSecurity.authorizePayload(authorize ->
                authorize
                        .anyRequest().authenticated()
                        .anyExchange().permitAll()
        ).jwt( jwtSpec -> {
            try {
                jwtSpec.authenticationManager(jwtReactiveAuthenticationManager);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return rSocketSecurity.build();
    }

}
