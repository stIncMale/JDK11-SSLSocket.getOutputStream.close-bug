# JDK11-SSLSocket.getOutputStream.close-bug
A minimal working example that reproduces
```OpenJDK 11.0.1``` bug which manifests itself in ```SSLSocket.getOutputStream().close()``` not closing the socket, which contradicts [the specification of ```SSLSocket.getOutputStream``` method](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/Socket.html#getOutputStream()): _Closing the returned OutputStream will close the associated socket_. This example not only shows the broken behaviour but also shows a situation in which the broken behaviour causes an application to hang. The problem was reported to Oracle via https://bugreport.java.com and was accepted: https://bugs.openjdk.java.net/browse/JDK-8216326, https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8216326.

## Environment
OS: ```macOS 10.13.6 (17G4015)```
### JDKs with this bug
11:
* ```openjdk 11.0.1 2018-10-16```, ```OpenJDK Runtime Environment 18.9 (build 11.0.1+13)```, ```OpenJDK 64-Bit Server VM 18.9 (build 11.0.1+13, mixed mode)```
* ```openjdk 11 2018-09-25```, ```OpenJDK Runtime Environment 18.9 (build 11+28)```, ```OpenJDK 64-Bit Server VM 18.9 (build 11+28, mixed mode)```
### JDKs without this bug
10, 9, 8:
* ```openjdk version "10.0.2" 2018-07-17```, ```OpenJDK Runtime Environment 18.3 (build 10.0.2+13)```, ```OpenJDK 64-Bit Server VM 18.3 (build 10.0.2+13, mixed mode)```
* ```openjdk version "10.0.1" 2018-04-17```, ```OpenJDK Runtime Environment (build 10.0.1+10)```, ```OpenJDK 64-Bit Server VM (build 10.0.1+10, mixed mode)```
* ```openjdk version "10" 2018-03-20```, ```OpenJDK Runtime Environment 18.3 (build 10+46)```, ```OpenJDK 64-Bit Server VM 18.3 (build 10+46, mixed mode)```
* ```openjdk 9.0.4```, ```OpenJDK Runtime Environment (build 9.0.4+11)```, ```OpenJDK 64-Bit Server VM (build 9.0.4+11, mixed mode)```
* ```java version "1.8.0_162"```, ```Java(TM) SE Runtime Environment (build 1.8.0_162-b12)```, ```Java HotSpot(TM) 64-Bit Server VM (build 25.162-b12, mixed mode)```

## Running the example and reproducing the bug
### For JDK 11
Run the example with
```
java -Djavax.net.ssl.trustStore=./keystore.jks -Djavax.net.ssl.trustStorePassword=password -Djavax.net.ssl.keyStore=./keystore.jks -Djavax.net.ssl.keyStorePassword=password ./SslSocketOutputStreamCloseBug.java
```
#### Sample output
```
Server (:) Starting on localhost/127.0.0.1:0
Server (:) Accepting connections on [SSL: ServerSocket[addr=localhost/127.0.0.1,localport=63197]]
Client 🔌 Connecting to localhost/127.0.0.1:63197
Server (:) Accepted a connection from /127.0.0.1:63198
Server (:) Waiting for data from the client /127.0.0.1:63198
Client 🔌 Connected via the socket Socket[addr=localhost/127.0.0.1,port=63197,localport=63198]
Client 🔌 Waiting for data from the server
Client 🔌 Closing the socket
Client 🔌 Failed to close the socket
Server (:) Received EOF
Server (:) Shut down
Client 🔌 Still waiting for data from the server...
Client 🔌 Still waiting for data from the server...
Client 🔌 Still waiting for data from the server...
...continues infinitely...
```
#### Explanation
The client (specifically ```clientSideReadingThread```) is blocked waiting for data from the server that never sends any. Then client tries to close its socket via ```SSLSocket.getOutputStream().close()``` and fails (```SSLSocket.isClosed()``` returns false). As a result the client stays infinitely blocked.

A server-side ```SSLSocket``` obtained via ```SSLServerSocket.accept()``` has the same bug, I tested this separately. ```SSLSocket.getInputStream().close()``` also does not close the associated ```SSLSocket```, and the code in the example can be trivially modified to observe this.
#### Run without TLS
One can run the example without TLS and see that ```Socket.getOutputStream().close()``` works as expected (closes the socket):
```
java ./SslSocketOutputStreamCloseBug.java noTls
```

### For JDK 10, 9, 8
Run the example with
```
javac ./SslSocketOutputStreamCloseBug.java && java -Djavax.net.ssl.trustStore=./keystore.jks -Djavax.net.ssl.trustStorePassword=password -Djavax.net.ssl.keyStore=./keystore.jks -Djavax.net.ssl.keyStorePassword=password SslSocketOutputStreamCloseBug ; rm ./SslSocketOutputStreamCloseBug*.class
```
#### Sample output
```
Server (:) Starting on localhost/127.0.0.1:0
Server (:) Accepting connections on [SSL: ServerSocket[addr=localhost/127.0.0.1,localport=62884]]
Client 🔌 Connecting to localhost/127.0.0.1:62884
Server (:) Accepted a connection from /127.0.0.1:62885
Server (:) Waiting for data from the client /127.0.0.1:62885
Client 🔌 Connected via the socket b1bc7ed[TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384: Socket[addr=localhost/127.0.0.1,port=62884,localport=62885]]
Client 🔌 Waiting for data from the server
Client 🔌 Closing the socket
Server (:) Received EOF
Server (:) Shut down
Client 🔌 Stopped waiting for data from the server
Client 🔌 Closed the socket
Client 🔌 Disconnected
```

## Workaround
As a workaround in projects we can:
* Close an ```SSLSocket``` directly via ```SSLSocket.close()```.
* Use ```Socket``` :)  At least ```Socket.getOutputStream().close()``` does not have this bug.

## Comments
```keystore.jks``` was generated with ```keytool``` from OpenJDK 11:
```
keytool -keystore ./keystore.jks -storepass "password" -storetype JKS -genkeypair -dname "CN=Unknown" -alias "myKey" -keypass "password" -keyalg RSA -validity 999999
```

---

All content is licensed under [![WTFPL logo](http://www.wtfpl.net/wp-content/uploads/2012/12/wtfpl-badge-2.png)](http://www.wtfpl.net/),
except where another license is explicitly specified.
