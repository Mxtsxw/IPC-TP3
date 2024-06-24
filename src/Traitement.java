import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Classe de traitement | opérations du serveur
 */
public class Traitement implements Runnable {

    ServerSocket serverSocket;
    Socket clientSocket;
    BufferedReader in;
    OutputStream out;

    Map<Integer, String> messages;
    Map<Integer, String> responses;

    private static final String SERVER_NAME = "Serveur HTTP";
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=utf-8";

    private String SITE_DIRECTORY;
    private static final String HOSTNAME_1 = "siteweb1";
    private static final String HOSTNAME_2 = "siteweb2";
    private static final String SITE_DIRECTORY_1 = "./src/siteweb";
    private static final String SITE_DIRECTORY_2 = "./src/siteweb2";

    public Traitement(ServerSocket serverSocket, Socket clientSocket){
        this.serverSocket = serverSocket;
        this.clientSocket = clientSocket;
        initMessage();
        initResponse();
    }

    /**
     * Initialise le dictionnaire contenant les codes d'erreur et les messages associés.
     */
    private void initMessage(){
        this.messages = new HashMap<>();
        this.messages.put(200, "OK");
        this.messages.put(400, "BAD REQUEST");
        this.messages.put(404, "NOT FOUND");
        this.messages.put(405, "METHOD NOT ALLOWED");
        this.messages.put(500, "INTERNAL SERVER ERROR");
    }

    /**
     * Initialise le dictionnaire contenant les réponses associées au codes d'erreur.
     */
    private void initResponse(){
        this.responses = new HashMap<>();
        this.responses.put(400, "<html><body><h1>400 Requête Incorrecte</h1><p>Votre requête n'a pas pu être comprise par le serveur.</p></body></html>");
        this.responses.put(404, "<html><body><h1>404 Introuvable</h1><p>L'URL demandée n'a pas été trouvée sur ce serveur.</p></body></html>");
        this.responses.put(405, "<html><body><h1>405 Méthode Non Autorisée</h1><p>La méthode spécifiée dans la requête n'est pas autorisée pour la ressource identifiée par l'URL de la requête.</p></body></html>");
        this.responses.put(500, "<html><body><h1>500 Erreur Interne du Serveur</h1><p>Le serveur a rencontré une erreur interne ou une erreur de configuration et n'a pas pu terminer votre requête.</p></body></html>");
    }

    @Override
    public void run(){
        try {
            // Créer un flux d'entrée et de sortie
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = clientSocket.getOutputStream();

            // Gestion de la requête serveur
            this.handle();

            // Fermer la connexion avec le client
            this.close();
        } catch (IOException e) {
            this.generateErrorResponse(500);
            e.printStackTrace();
        }
    }

    /**
     * Ferme le socket de connexion client.
     */
    private void close() throws IOException {
        this.clientSocket.close();
    }

    /**
     * Méthode assurant le traitement des requêtes du serveur.
     */
    private void handle() throws IOException {
        String request = getRequest();
        System.out.println(request);

        // Gère l'hôte demandé dans la requête
        String host = getHostFromRequest(request);
        if (host != null && host.equals("localhost:8080") | host.equals(HOSTNAME_1)){
            this.SITE_DIRECTORY = SITE_DIRECTORY_1;
        } else if (host != null && host.equals(HOSTNAME_2)){
            this.SITE_DIRECTORY = SITE_DIRECTORY_2;
        } else {
            out.write(generateErrorResponse(400).getBytes());
            return;
        }

        int code = getRequestCode(request);
        if (code != 200){
            out.write(generateErrorResponse(code).getBytes());
        } else {
            servePage(buildFilePath(request));
        }
    }

    /**
     * Récupère et retourne la requête sous une chaine de caractères.
     */
    private String getRequest() throws IOException {
        StringBuilder request = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            request.append(line).append("\r\n");
        }
        request.append("\r\n");
        return request.toString();
    }

    /**
     * Vérifie la validité d'une requête.
     */
    public int getRequestCode(String request) {
        // Expression régulière pour une requête GET bien formée
        String regex = "^(GET|POST|PUT|DELETE) (/\\S*) HTTP/(1\\.0|1\\.1)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(request);

        if (!matcher.find()) {
            return 400;
        }

        // Vérification de la méthode : GET
        regex = "^GET";
        pattern = Pattern.compile(regex);
        matcher = pattern.matcher(request);
        if (!matcher.find()) {
            return 405;
        }

        return 200;
    }

    /**
     * Méthode pour obetnir la date au format : lun., 29 nov. 2020 09:45:39 CET.
     */
    private String getDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        return ZonedDateTime.now().format(formatter);
    }

    /**
     * Méthode pour générer l'en-tête d'une réponse du serveur avec seulement le code de statut
     */
    public String generateHeader(int code) {
        String message = this.messages.get(code);
        return "HTTP/1.1 " + code + " " + message + "\r\n" +
                "Date: " + getDate() + "\r\n" +
                "Server: " + SERVER_NAME + "\r\n" +
                "Content-Type: " + DEFAULT_CONTENT_TYPE + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }

    /**
     * Méthode pour générer l'en-tête d'une réponse du serveur avec le code de statut et la taille des données.
     */
    public String generateHeader(int code, int length, String path) throws IOException {
        String message = this.messages.get(code);
        return "HTTP/1.1 " + code + " " + message + "\r\n" +
                "Date: " + getDate() + "\r\n" +
                "Server: " + SERVER_NAME + "\r\n" +
                "Connection: close\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Content-Type: "+ Files.probeContentType(Paths.get(path)) + "\r\n" +
                "\r\n";
    }

    /**
     * Méthode pour générer une réponse d'erreur.
     */
    public String generateErrorResponse(int code) {
        String entete = generateHeader(code);
        String messageErreur = this.responses.get(code);
        return entete + messageErreur + "\r\n";
    }

    /**
     * Méthode chargée de construire le chemin vers la ressource demandée.
     */
    public String buildFilePath(String request) {
        // Extraire le chemin de la requête
        String requestPath = request.split("\\s+")[1];
        // Éliminer tout ce qui se trouve après le '?' dans le chemin de la requête
        requestPath = requestPath.split("\\?")[0];
        // Construire le chemin complet
        String filePath = SITE_DIRECTORY + requestPath;
        // Si le chemin mène à un dossier, ajouter "index.html"
        File file = new File(filePath);
        if (file.exists() && file.isDirectory()) {
            filePath = filePath + "index.html";
        }
        return filePath;
    }

    /**
     * Envoie la réponse contenant la page demandée.
     */
    public void servePage(String path) throws IOException {
        // Lire le contenu du fichier
        byte[] content = Files.readAllBytes(Paths.get(path));
        // Générer l'en-tête de la réponse
        String header = generateHeader(200, content.length, path);
        // Construire la réponse
        out.write(header.getBytes());
        out.write(content);
    }

    /**
     * Fonction qui permet d'extraire l'attribut Host de l'en-tête de la requête
     */
    public String getHostFromRequest(String request) {
        // Expression régulière pour extraire l'Host de la requête
        String regex = "Host:\\s*(\\S+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(request);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }
}
