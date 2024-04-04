
import java.net.ServerSocket;
import java.net.Socket;

public class WebServer {

    public static void main(String[] args) {
        int port = 8080; // Utilisez le port 80 pour le déploiement final
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Le serveur écoute sur le port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Créez une instance de RequestHandler pour gérer la requête
                RequestHandler handler = new RequestHandler(clientSocket);
                // Exécuter la gestion de la requête dans un nouveau thread
                new Thread(handler).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'start'");
    }
}
