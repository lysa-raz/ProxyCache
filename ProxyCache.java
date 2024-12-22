import java.io.*;
import java.net.*;
import java.util.*;

public class ProxyCache {
    // Attributs statiques modifiables
    private static int PROXY_PORT;
    private static String SERVER_IP;
    private static int XAMPP_PORT;
    private static long CACHE_DURATION;

    private static boolean running = true;
    private static final Map<String, CacheEntry> cache = new HashMap<>();

    public static void main(String[] args) {
        loadConfig("donnees.txt");

        System.out.println("Proxy Cache dÃ©marrÃ© sur le port " + PROXY_PORT);
        try (ServerSocket serverSocket = new ServerSocket(PROXY_PORT)) {
            new Thread(ProxyCache::handleServerCommands).start();
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig(String fileName) {
        Properties properties = new Properties();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            properties.load(reader);

            // Charger les configurations
            PROXY_PORT = Integer.parseInt(properties.getProperty("PROXY_PORT"));
            SERVER_IP = properties.getProperty("SERVER_IP");
            XAMPP_PORT = Integer.parseInt(properties.getProperty("XAMPP_PORT"));
            CACHE_DURATION = Long.parseLong(properties.getProperty("CACHE_DURATION"));

            // Afficher les valeurs chargÃ©es pour vÃ©rification
            System.out.println("Configuration chargÃ©e avec succÃ¨s :");
            System.out.println("PROXY_PORT = " + PROXY_PORT);
            System.out.println("SERVER_IP = " + SERVER_IP);
            System.out.println("XAMPP_PORT = " + XAMPP_PORT);
            System.out.println("CACHE_DURATION = " + CACHE_DURATION);

        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur lors de la lecture du fichier de configuration : " + e.getMessage());
        }
    }

    private static void handleServerCommands() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                System.out.print("$CommandeProxy > ");
                String command = scanner.nextLine().trim();
    
                if ("listecache".equalsIgnoreCase(command)) {
                    listCache();
                } else if ("drop all".equalsIgnoreCase(command)) { 
                    clearAllCache();
                } else if (command.startsWith("drop ")) { 
                    String key = command.substring(5).trim();
                    deleteCacheEntry(key);
                } else if (command.startsWith("changeduration ")) { //Pour tous les fichiers
                    String durationStr = command.substring(15).trim();
                    try {
                        CACHE_DURATION = Long.parseLong(durationStr);
                        System.out.println("DurÃ©e de cache modifiÃ©e Ã  " + CACHE_DURATION + " ms.");
                    } catch (NumberFormatException e) {
                        System.out.println("Erreur : Veuillez fournir une durÃ©e valide en millisecondes.");
                    }
                } else if (command.startsWith("changedurationentry ")) { //Pour un fichier
                    String[] parts = command.split(" ");
                    if (parts.length == 3) {
                        String key = parts[1];
                        long duration = Long.parseLong(parts[2]);
                        changeDurationForEntry(key, duration);
                    } else {
                        System.out.println("Commande incorrecte. Utilisation : changedurationentry <clÃ©> <durÃ©e>");
                    }
                } else if (command.startsWith("durationextension ")) { //extension
                    String[] parts = command.split(" ");
                    if (parts.length == 3) {
                        String extension = parts[1];
                        try {
                            long duration = Long.parseLong(parts[2]);
                            changeDurationForExtension(extension, duration);
                        } catch (NumberFormatException e) {
                            System.out.println("Erreur : Veuillez fournir une durée valide en millisecondes.");
                        }
                    } else {
                        System.out.println("Commande incorrecte. Utilisation : changeduration <extension> <durée>");
                    }
                } else if (command.startsWith("durationtaille ")) { // Pour changer la durée pour la taille du fichier
                    String[] parts = command.split(" ");
                    if (parts.length == 3) {
                        try {
                            long fileSizeInKB = Long.parseLong(parts[1]);  // Taille en Ko
                            long newDuration = Long.parseLong(parts[2]);  // Nouvelle durée en millisecondes
                            changeDurationForSize(fileSizeInKB, newDuration);
                        } catch (NumberFormatException e) {
                            System.out.println("Erreur : Veuillez fournir des valeurs valides pour la taille en Ko et la durée.");
                        }
                    } else {
                        System.out.println("Commande incorrecte. Utilisation : changeduration <taille_en_Ko> <durée_en_ms>");
                    } 
                } else if ("exit".equalsIgnoreCase(command)) {
                    System.out.println("ArrÃªt du serveur proxy...");
                    running = false;
                    System.exit(1);
                } else {
                    System.out.println("Commande non reconnue.");
                }
            }
        }
    }

    private static void changeDurationForExtension(String extension, long newDuration) {
        if (extension != null && !extension.isEmpty()) {
            boolean extensionFound = false;  // Variable pour vérifier si l'extension est trouvée

            // Applique la durée à tous les fichiers dans le cache qui correspondent à cette extension
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                String fileName = entry.getKey();
                CacheEntry cacheEntry = entry.getValue();

                // Vérifie si le fichier se termine par l'extension spécifiée
                if (fileName.endsWith(extension)) {
                    cacheEntry.timestamp = System.currentTimeMillis() + newDuration;  // Mettre à jour la durée
                    System.out.println("Durée mise à jour pour le fichier : " + fileName);
                    extensionFound = true;  // Marquer que l'extension a été trouvée
                }
            }

            // Si aucune correspondance n'a été trouvée
            if (!extensionFound) {
                System.out.println("Aucun fichier avec l'extension " + extension + " trouvé dans le cache.");
            }
        } else {
            System.out.println("Erreur : Veuillez spécifier une extension valide.");
        }
    }


    private static void changeDurationForSize(long fileSizeInKB, long newDuration) {
        if (fileSizeInKB > 0) {
            boolean sizeFound = false;  // Variable pour vérifier si un fichier correspondant à la taille a été trouvé

            // Convertir la taille du fichier en octets (1 Ko = 1024 octets)
            long fileSizeInBytes = fileSizeInKB * 1024;

            // Applique la durée à tous les fichiers dans le cache dont la taille correspond à fileSizeInBytes
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                CacheEntry cacheEntry = entry.getValue();
                System.out.println("taille de"+cacheEntry.data.length);
                if (cacheEntry.data.length == fileSizeInBytes) {  // Comparaison de la taille des fichiers en octets
                    cacheEntry.timestamp = System.currentTimeMillis() + newDuration;  // Mettre à jour la durée
                    System.out.println("Durée mise à jour pour le fichier : " + entry.getKey());
                    sizeFound = true;  // Marquer que la taille a été trouvée
                }
            }

            // Si aucune correspondance n'a été trouvée
            if (!sizeFound) {
                System.out.println("Aucun fichier de taille " + fileSizeInKB + " Ko trouvé dans le cache.");
            }
        } else {
            System.out.println("Erreur : Veuillez spécifier une taille valide en Ko.");
        }
    }

    private static void changeDurationForEntry(String key, long newDuration) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            entry.timestamp = System.currentTimeMillis(); // RÃ©initialise le timestamp
            entry.timestamp += newDuration; // Met Ã  jour la durÃ©e
            System.out.println("DurÃ©e du cache mise Ã  jour pour : " + key + " Ã  " + newDuration + " ms.");
        } else {
            System.out.println("Aucune entrÃ©e trouvÃ©e avec la clÃ© : " + key);
        }
    }
    
    private static void clearAllCache() {
        if (cache.isEmpty()) {
            System.out.println("Le cache est dÃ©jÃ  vide.");
        } else {
            cache.clear();
            System.out.println("Tous les caches ont Ã©tÃ© effacÃ©s.");
        }
    }
    

    private static void listCache() {
        if (cache.isEmpty()) {
            System.out.println("Le cache est vide");
        } else {
            System.out.println("Contenu du cache :");
            cache.forEach((key, value) -> System.out.println("- " + key));
        }
    }

    private static void deleteCacheEntry(String key) {
        if (cache.containsKey(key)) {
            cache.remove(key);
            System.out.println("EntrÃ©e du cache supprimÃ©e : " + key);
        } else {
            System.out.println("Aucune entrÃ©e trouvÃ©e avec la clÃ© : " + key);
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();
            System.out.println("RequÃªte reÃ§ue : " + requestLine);

            // VÃ©rifier la requÃªte
            if (requestLine == null || !requestLine.startsWith("GET")) {
                sendError(out, "400 Bad Request");
                return;
            }

            // Extraire le chemin du fichier demandÃ©
            String fileRequested = requestLine.split(" ")[1];
            if (fileRequested.startsWith("/")) fileRequested = fileRequested.substring(1);

            // VÃ©rifier dans le cache
            if (cache.containsKey(fileRequested)) {
                CacheEntry cachedEntry = cache.get(fileRequested);
                // VÃ©rifier si le cache n'est pas expirÃ©
                if (System.currentTimeMillis() - cachedEntry.timestamp <= CACHE_DURATION) {
                    System.out.println("CACHE_DURATION : " + CACHE_DURATION);
                    System.out.println("Fichier servi depuis le cache: " + fileRequested);
                    out.write(cachedEntry.data);
                    return;
                } else {
                    cache.remove(fileRequested); // Supprimer l'entrÃ©e expirÃ©e
                    System.out.println("Cache expirÃ© pour: " + fileRequested);
                }
            }

            // RÃ©cupÃ©rer depuis le serveur et stocker dans le cache
            System.out.println("RÃ©cupÃ©ration depuis le serveur pour: " + fileRequested);
            byte[] content = fetchFromServer(fileRequested);
            if (content != null) {
                cache.put(fileRequested, new CacheEntry(content, System.currentTimeMillis())); // Mettre Ã  jour le cache
                out.write(content);
            } else {
                sendError(out, "404 Not Found");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendError(OutputStream out, String errorMessage) throws IOException {
        String errorResponse = "HTTP/1.1 " + errorMessage + "\r\nConnection: close\r\n\r\n" + errorMessage;
        out.write(errorResponse.getBytes());
        out.flush();
    }

    private static byte[] fetchFromServer(String fileRequested) {
        try (Socket serverSocket = new Socket(SERVER_IP, XAMPP_PORT);
             OutputStream serverOutput = serverSocket.getOutputStream();
             InputStream serverInput = serverSocket.getInputStream()) {

            PrintWriter out = new PrintWriter(new OutputStreamWriter(serverOutput, "UTF-8"), true);
            out.print("GET /" + fileRequested + " HTTP/1.1\r\n");
            out.print("Host: " + SERVER_IP + "\r\n");
            out.print("User-Agent: ProxyClient/1.0\r\n");
            out.print("Accept: */*\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.flush();

            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            System.out.println("RequÃªte envoyÃ©e au serveur : GET /" + fileRequested);

            while ((bytesRead = serverInput.read(buffer)) != -1) {
                responseBuffer.write(buffer, 0, bytesRead);
            }
            return responseBuffer.toByteArray();

        } catch (IOException e) {
            System.err.println("Erreur lors de la requÃªte vers le serveur : " + e.getMessage());
            return null;
        }
    }

    // Classe interne pour gÃ©rer les entrÃ©es du cache
    private static class CacheEntry {
        byte[] data;
        long timestamp;

        CacheEntry(byte[] data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}