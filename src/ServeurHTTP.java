import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServeurHTTP {

    private static final int PORT = 8080;
    private static final int MAX_THREADS = 10;


    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Serveur HTTP en cours exécution...");

            ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connexion établie avec " + clientSocket.getInetAddress());
                pool.execute(new Traitement(serverSocket, clientSocket));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}