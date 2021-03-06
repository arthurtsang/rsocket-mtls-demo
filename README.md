# RSocket Demo with mTLS, CRL, JWS, Cloudevents, protobuf and RabbitMQ backend

## RSocket with mTLS and JWT authentication

I've put together a little demo on how to setup mTLS (clientAuth) with RSocket/Spring Boot and turn on CRL.

The `cert` directory contains a simple script to generate certificates WITHOUT CRL.
The `cert-crl/rootca` directory contains a script, `gen_certs` which generates 

1. a self-signed root CA cert and necessarily files for the root CA
2. an intermediate cert signed by the root CA and necessarily files for the intermediate CA
3. a cert for the server, signed by the intermediate cert
4. two certs for the clients, signed by the intermediate cert and revoke client2 cert.

both, root CA and intermediate CA has CRL setup, to launch the CRL, 
run `python3 -m http.server 4000` under `cert-crl/rootca` and 
`python3 -m http.server 4100` under `cert-crl/intermediate`.

The demo assumes both client and server shared an object, `Location`, using protobuf.
The protobuf class is located in `sharedkernel` model.
To generate the protobuf class, run
`~/protoc-3.20.0-osx-x86_64/bin/protoc --java_out=java --proto_path=./resources ./resources/sharedkernel.proto` under `sharedkernel/src/main`

The `client` module is a simple reactive web server which has 2 endpoints `/request-response` and `/fire-and-forget` to demonstrate two of the rsocket interaction model.
The `client` will add the `client[1|2] cert` to the rsocket SSL context for mTLS.
It will also add a `JWS` (signed JWT) to the rsocket request metadata.

The `server` module is a rsocket server which has 2 matching message mapping `/request-response` and `/fire-and-forget`.
The `client` will call the corresponding message mapping using the interaction model as named.
To enable revocation checking and CRL distribution point (JSSE disabled both by default), run the server with `-Dcom.sun.security.enableCRLDP=true -Dcom.sun.net.ssl.checkRevocation=true`

The `client` module has 2 profiles, `client1` to use `client-keystore.p12` and `client2` to use `client2-keystore.p12` (which is revoked).
With both `checkRevocation` and `enableCRLDP`, the `server` will check against `http://localhost:4100/intermediate.crl`.

```bash
arthur@Arthur:~/rsocket$ curl http://localhost:7001/request-response
s3://newbucket/newfile
arthur@Arthur:~/rsocket$ curl http://localhost:7002/request-response
{"timestamp":"2022-04-27T22:13:01.911+00:00","path":"/request-response","status":500,"error":"Internal Server Error","requestId":"831f1a23-1"}
```

## RSocket channel with CloudEvents (over protobuf) and RabbitMQ eventbus

I've adding a RSocket channel implementation between the `client` and `server`.  
The `client` will set up the RSocket channel to the `server` upon startup (however, the connection didn't establish right away, even though the resulting Flux is subscribed).
A new component `CloudEventHandler` is added to mock handling cloud events (commands) from the server and post a cloud event back to the server to mimic the process completion event.

The `server` has a new `channel` rsocket endpoint which would post the cloud event from the incoming flux to a rabbit queue (the name of the queue would be the audience field of the jwt token which would better to be a field in the cloud event instead).
It will also listen to a queue with the same name as the subject in the JWT and return the Flux to the queue as the rsocket endpoint returns.

### Scenario

* Client 1 connects to Server 1 over RSocket Channel
* Server 1 listens to Rabbit queue named Client 1
* Client 2 connects to Server 2 over Rsocket Channel
* Server 2 listens to Rabbit queue named Client 2
* Client 1 sends a cloud event to Server 1 with the audience in the JWT as Client 2
* Server 1 put the cloud event to the Rabbit queue Client 2
* Server 2 picks up the cloud event and send it to Client 2 through RSocket Channel
* Client 2 procecss the cloud event with the Cloud Event Handler and post a result cloud event to another recipient.


### TODO

* FluxSinkConsumer cannot be a bean.  when more than one clients connected to the server, they need to have different instance of FluxSinkConsumer, one for each of the Flux instance.  need to implement a Factory and Registry so the correct one can be fetched to send to the correct client.