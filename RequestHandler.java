
import java.net.Socket;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class RequestHandler implements Runnable {
    private Socket clientSocket;

    public RequestHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (InputStream input = clientSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = clientSocket.getOutputStream()) {

            String httpRequestLine = reader.readLine();
            if (httpRequestLine == null || httpRequestLine.isEmpty()) {
                sendBadRequest(output);
                return;
            }

            System.out.println(httpRequestLine);

            String[] requestParts = httpRequestLine.split(" ");
            if (requestParts.length < 3) {
                sendBadRequest(output);
                return;
            }

            String httpMethod = requestParts[0];
            String resourcePath = requestParts[1];
            String httpVersion = requestParts[2];

            boolean isAjax = resourcePath.contains("ajax=true");

            if (httpMethod.equals("GET")) {
                // Supprimez tout paramètre de requête de l'URL
                String cleanPath = resourcePath.split("\\?")[0];

                if (cleanPath.equals("/")) {
                    // Si la requête est pour la racine, servez 'menu.html'
                    handleFileRequest("index.html", output, isAjax);
                } else if (cleanPath.startsWith("/montrer-contenu")) {
                    // Traitez les demandes AJAX pour montrer le contenu d'un fichier spécifique
                    String filePath = extractFilePathFromQuery(resourcePath);
                    if (filePath != null && !filePath.isEmpty()) {
                        handleFileRequest(filePath, output, isAjax);
                    } else {
                        sendBadRequest(output);
                    }
                } else {
                    // Servez d'autres fichiers statiques comme 'index.html', 'contact.html', etc.
                    handleFileRequest(cleanPath, output, isAjax);
                }
            } else {
                // Répondez avec une erreur 400 pour toutes les autres méthodes HTTP
                sendBadRequest(output);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //

    private String extractFilePathFromQuery(String query) {
        try {
            String[] parts = query.split("\\?filepath=");
            return parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : null;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void handleFileRequest(String filePath, OutputStream output, boolean isAjax) throws IOException {

        // Liste des extensions de fichiers valides
        List<String> validExtensions = Arrays.asList("html", "txt", "pdf", "jpeg", "jpg", "png", "mp4", "py");

        if (filePath.equals("/")) {
            filePath = "index.html"; // Si le chemin est "/", servez "index.html"
        }

        File file = new File(filePath);

        // Obtenez l'extension du fichier demandé
        String fileExtension = getFileExtension(file);

        // Vérifiez si l'extension de fichier est valide
        if (!validExtensions.contains(fileExtension)) {
            sendBadRequest(output);
            return;
        }

        if (!file.exists()) {
            sendNotFound(output);
            return;
        }

        if (file.isDirectory()) {
            sendDirectoryListing(file, output);
            return;
        }

        if (isAjax) {
            switch (fileExtension) {
                case "html":
                    byte[] fileContent = Files.readAllBytes(Paths.get(file.getPath()));
                    output.write(fileContent);
                    break;

                default:
                    sendFileContent(file, output);
                    break;
            }
        } else {
            switch (fileExtension) {
                case "py":
                    executePythonScript(file, output);
                    break;
                case "html":
                    sendFile(file, output);
                    break;
                case "mp4":
                    sendVideoFile(file, output);
                    break;
                case "txt":
                    sendTextFile(file, output);
                    break;
                case "jpg":
                case "jpeg":
                case "png":
                    sendImageFile(file, output);
                    break;
                case "pdf":
                    sendPdfFile(file, output);
                    break;
                default:
                    sendFile(file, output);
                    break;
            }
        }
    }

    // New helper method to send just the file content
    private void sendFileContent(File file, OutputStream output) throws IOException {
        byte[] content = Files.readAllBytes(file.toPath());
        output.write(content);
    }

    // méthode executePythonScript pour exécuter les scripts Python et renvoyer leur
    // sortie.
    // Cette méthode est appelée lorsque la requête concerne un fichier .py.
    private void executePythonScript(File scriptFile, OutputStream output) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", scriptFile.getAbsolutePath());
            Process p = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = p.getInputStream()) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, length);
                }
            }
            p.waitFor();

            byte[] scriptOutput = baos.toByteArray();
            String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: "
                    + scriptOutput.length + "\r\n\r\n";
            output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
            output.write(scriptOutput);
        } catch (Exception e) {
            sendInternalServerError(output);
        }

    }

    private void sendInternalServerError(OutputStream output) throws IOException {
        String response = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n";
        output.write(response.getBytes(StandardCharsets.UTF_8));
    }

    // Méthode pour obtenir l'extension d'un fichier
    private String getFileExtension(File file) {
        String fileName = file.getName();
        if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        else
            return "";
    }

    // Cette méthode envoie une réponse HTTP avec le statut 400 (Bad Request) et
    // aucune donnée supplémentaire.
    private void sendBadRequest(OutputStream output) {
        try {
            String responseBody = "Mauvaise requête : l'extension du fichier n'est pas supportée.";
            String response = "HTTP/1.1 400 Bad Request\r\n"
                    + "Content-Type: text/html\r\n"
                    + "Content-Length: " + responseBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                    + responseBody;
            output.write(response.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Cette méthode envoie une réponse HTTP avec le statut 404 (Not Found) et
    // aucune donnée supplémentaire.
    private void sendNotFound(OutputStream output) throws IOException {
        String responseHeader = "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\n\r\n";
        String responseBody = "<html><head><title>Page Not Found</title></head><body>" +
                "<h1>Erreur 404 : Page non trouvée</h1>" +
                "<p>La ressource demandée n'est pas disponible sur ce serveur.</p>" +
                "</body></html>";

        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        output.write(responseBody.getBytes(StandardCharsets.UTF_8));
    }

    // Cette méthode envoie un en-tête HTTP avec le statut 200 (OK) et le type MIME
    // approprié, puis envoie le contenu du fichier spécifié.
    private void sendFile(File resource, OutputStream output) throws IOException {
        String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: " + guessContentType(resource) + "\r\n\r\n";
        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        Files.copy(Paths.get(resource.getAbsolutePath()), output);
    }

    private String guessContentType(File resource) throws IOException {
        return Files.probeContentType(resource.toPath());
    }

    // Cette méthode génère un en-tête HTTP 200 OK et une liste HTML des fichiers et
    // dossiers dans le répertoire spécifié.
    private void sendDirectoryListing(File directory, OutputStream output) throws IOException {
        File[] files = directory.listFiles();
        StringBuilder contentBuilder = new StringBuilder("<html><body><ul>");
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory()) {
                name = name + "/";
            }
            contentBuilder.append("<li><a href=\"/montrer-contenu?filepath=")
                    .append(URLEncoder.encode(file.getPath(), StandardCharsets.UTF_8.name()))
                    .append("\">")
                    .append(name)
                    .append("</a></li>");
        }
        contentBuilder.append("</ul></body></html>");
        byte[] responseBytes = contentBuilder.toString().getBytes(StandardCharsets.UTF_8);

        String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: " + responseBytes.length
                + "\r\n\r\n";
        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        output.write(responseBytes);
    }

    private void sendPdfFile(File pdfFile, OutputStream output) throws IOException {
        String mimeType = "application/pdf";
        byte[] pdfData = Files.readAllBytes(pdfFile.toPath());
        String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeType + "\r\nContent-Length: " + pdfData.length
                + "\r\n\r\n";
        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        output.write(pdfData);
    }

    private void sendImageFile(File imageFile, OutputStream output) throws IOException {
        String mimeType = Files.probeContentType(imageFile.toPath());
        byte[] imageData = Files.readAllBytes(imageFile.toPath());
        String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeType + "\r\nContent-Length: "
                + imageData.length + "\r\n\r\n";
        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        output.write(imageData);
    }

    private void sendTextFile(File textFile, OutputStream output) throws IOException {
        String mimeType = "text/plain";
        byte[] textData = Files.readAllBytes(textFile.toPath());
        String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeType + "\r\nContent-Length: "
                + textData.length + "\r\n\r\n";
        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        output.write(textData);
    }

    private void sendVideoFile(File videoFile, OutputStream output) throws IOException {
        String mimeType = "video/mp4"; // Assurez-vous que cela correspond au format de votre vidéo
        String responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeType + "\r\nContent-Length: "
                + videoFile.length() + "\r\n\r\n";
        output.write(responseHeader.getBytes(StandardCharsets.UTF_8));

        FileInputStream fis = new FileInputStream(videoFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        fis.close();
    }

}
