# Cert with CRL

run `gen_certs` under `rootca` directory, where it:

1. generate a self-signed certificate for Root CA.
2. create certindex, and initialize certserial and crlnumber to 1000 (used for openssl CA)
3. generate a CSR for intermediate CA and sign it with the Root CA with the following settings in the `rootca/ca.confg`
   1. crl distribution set to `http://localhost:4000/rootca.crl`
   2. CA basic constraint set to true
   2. path length basic constraint set to 0 (i.e. the intermediate cert should not be used to generate more certs)
   3. set CA to use current directory
      1. database to be certindex,
      2. serial to use certserial,
      3. crl number to use crlnumber, and
      4. private key to use rootca.key (generated in step 1)
4. generate an empty CRL 
5. cd to `intermediate` where it will host all CA files for the intermediate cert
6. create certindex, and initialize certserial and crlnumber to 1000 (used for openssl CA) 
7. generate a CSR for server and a CSR for client/client2 and sign it with the Intermediate CA with the following settings in the `intermediate/ca.confg`
   1. crl distribution set to `http://localhost:4100/intermediate.crl`
   2. CA basic constraint set to false
   2. subject alt names set to `localhost` and `127.0.0.1` (so the certs this CA signed can be used locally)
   3. set CA to use current directory
      1. database to be certindex,
      2. serial to use certserial,
      3. crl number to use crlnumber,
      4. private key to use intermediate.key (generated in step 3), and
      5. extended key usage to have server auth and client auth (without client auth, the server will throw a silent exception that the cert cannot be used for mTLS (turn on javax.net.debug to show the exception) and the client will throw an unknown cert exception)
8. generate an empty CRL 
9. revoke client2
9. cd to `client-server` where it will store the final certs for the client & server.
10. generate keystores and truststore
   1. client keystore -> private key for client, cert for client and cert chain for intermediate and root CA.
   2. server keystore -> private key for server, cert for server and cert chain for intermediate and root CA.
   2. truststore -> cert for root CA (make sure it's of type TrustedCertEntry and not PrivateKeyEntry)
   
   

----

# TODO:

* pick up the cn of the client cert on server
* SSLProvider.OPENSSL?  (SSLEngine)
