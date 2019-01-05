import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

class SslSocketOutputStreamCloseBug {
    public static void main(String... args) throws IOException, InterruptedException, ExecutionException {
        boolean useTls = args.length == 0 || !args[0].equalsIgnoreCase("noTls");
        SocketAddress serverSocketAddress = startServer(useTls);
        try (Socket clientSideSocket = useTls ? SSLSocketFactory.getDefault().createSocket() : new Socket()) {
            clientSideSocket.setSoTimeout(0);
            logClient("Connecting to " + serverSocketAddress);
            clientSideSocket.connect(serverSocketAddress, 0);
            if (clientSideSocket instanceof SSLSocket) {
                ((SSLSocket)clientSideSocket).startHandshake();
            }
            logClient("Connected via the socket " + clientSideSocket);
            InputStream clientSideInputStream = clientSideSocket.getInputStream();
            Thread clientSideReadingThread = new Thread(() -> {
                try {
                    logClient("Waiting for data from the server");
                    logClient("Received " + toHexOrEof(clientSideInputStream.read()));//wait for data from the server that never sends any
                } catch (IOException e) {
                    //clientSideSocket was closed
                } catch (RuntimeException e) {
                    e.printStackTrace(System.out);
                } finally {
                    logClient("Stopped waiting for data from the server");
                }
            });
            clientSideReadingThread.start();
            clientSideReadingThread.join(500);//wait until clientSideReadingThread starts reading
            logClient("Closing the socket");
            clientSideSocket.getOutputStream().close();//must close clientSideSocket
            if (clientSideSocket.isClosed()) {
                logClient("Closed the socket");
            } else {
                logClient("Failed to close the socket");
            }
            do {//wait until clientSideReadingThread dies
                clientSideReadingThread.join(1000);
                if (clientSideReadingThread.isAlive()) {
                    logClient("Still waiting for data from the server...");
                } else {
                    break;
                }
            } while (true);
        } finally {
            logClient("Disconnected");
        }
    }
    
    private static SocketAddress startServer(boolean useTls) throws ExecutionException, InterruptedException {
        CompletableFuture<SocketAddress> resultFuture = new CompletableFuture<>();
        Thread serverSideThread = new Thread(() -> {
            try (ServerSocket serverSocket = useTls ? SSLServerSocketFactory.getDefault().createServerSocket() : new ServerSocket()) {
                SocketAddress serverSocketAddress = new InetSocketAddress("localhost", 0);
                logServer("Starting on " + serverSocketAddress);
                serverSocket.setSoTimeout(0);
                serverSocket.bind(serverSocketAddress);
                resultFuture.complete(new InetSocketAddress(serverSocket.getInetAddress(), serverSocket.getLocalPort()));
                logServer("Accepting connections on " + serverSocket);
                Socket serverSideSocket = serverSocket.accept();
                logServer("Accepted a connection from " + serverSideSocket.getRemoteSocketAddress());
                logServer("Waiting for data from the client " + serverSideSocket.getRemoteSocketAddress());
                logServer("Received " + toHexOrEof(serverSideSocket.getInputStream().read()));//wait for data from the client that never sends any
            } catch (IOException | RuntimeException e) {
                resultFuture.completeExceptionally(e);
            } finally {
                resultFuture.completeExceptionally(new RuntimeException());
                logServer("Shut down");
            }
        });
        serverSideThread.start();
        return resultFuture.get();
    }

    private static void logServer(Object msg) {
        System.out.println("Server (:) " + msg);
    }

    private static void logClient(Object msg) {
        System.out.println("Client \uD83D\uDD0C " + msg);
    }

    private static String toHexOrEof(int baseTenByte) {
        return baseTenByte == -1 ? "EOF" : String.format(Locale.ROOT, "0x%02X", baseTenByte);
    }
}
