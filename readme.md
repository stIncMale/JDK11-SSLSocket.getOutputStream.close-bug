# JDK11-SSLSocket.getOutputStream.close-bug
A minimal working example that reproduces
```OpenJDK 11.0.1``` (```openjdk 11.0.1 2018-10-16```, ```OpenJDK Runtime Environment 18.9 (build 11.0.1+13)```, ```OpenJDK 64-Bit Server VM 18.9 (build 11.0.1+13, mixed mode)``` on ```macOS 10.13.6 (17G4015)```)
bug which manifests itself in ```SSLSocket.getOutputStream().close()``` not closing the socket, which contradicts [the specification of ```SSLSocket.getOutputStream``` method](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/net/Socket.html#getOutputStream()): ```_Closing the returned OutputStream will close the associated socket_```. This problem was reported to Oracle via https://bugreport.java.com.

As a workaround in projects we can:
* Close an ```SSLSocket``` directly via ```SSLSocket.close()```.
* Use ```Socket``` :)  At least ```Socket.getOutputStream().close()``` does not have this bug.

All content is licensed under [![WTFPL logo](http://www.wtfpl.net/wp-content/uploads/2012/12/wtfpl-badge-2.png)](http://www.wtfpl.net/),
except where another license is explicitly specified.

