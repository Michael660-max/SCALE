import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class UserService {
    // private static final int PORT = 14001;
    private static int PORT;
    private static String IP;

    private static void loadConfig(String path) {
        try {
            if (path == null || path.isEmpty()) {
                // throw new IllegalArgumentException("Path cannot be null or empty.");
                path = "config.json";
            }
            String filePath = new File(path).getAbsolutePath();
            String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            // String content = new String(Files.readAllBytes(Paths.get("config.json")), StandardCharsets.UTF_8);
            Map<String, Map<String, String>> config = parseNestedJson(content);
            Map<String, String> userService = config.get("UserService");

            if (userService != null) {
                String portStr = userService.get("port");
                String ipStr = userService.get("ip");
                
                if (portStr != null) {
                    PORT = Integer.parseInt(portStr);
                }
                
                if (ipStr != null) {
                    IP = ipStr;
                }
            }
        } catch (IOException e) {
            // System.out.println("Warning: Could not read config.json. Using default port " + PORT + " and IP " + IP);
            // System.out.println("hehe");
        } catch (Exception e) {
            // System.out.println("Warning: Error parsing config.json. Using default port " + PORT + " and IP " + IP);
            // System.out.println("haha");
        }
    }

    

    
    /** 
     * @param json
     * @return Map<String, Map<String, String>>
     */
    private static Map<String, Map<String, String>> parseNestedJson(String json) {
        Map<String, Map<String, String>> result = new HashMap<>();
        
        // Remove whitespace and newlines
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        
        // Split top-level JSON keys
        String[] topLevelEntries = json.split("},");
        for (String entry : topLevelEntries) {
            entry = entry.trim();
            if (!entry.endsWith("}")) {
                entry += "}";  // Fix missing closing brace in split
            }
    
            // Extract the key
            int colonIndex = entry.indexOf(":");
            if (colonIndex == -1) continue; // Skip invalid entries
            
            String key = entry.substring(0, colonIndex).replaceAll("[\"{}]", "").trim();
            String value = entry.substring(colonIndex + 1).trim();
            
            // Parse the nested object
            Map<String, String> nestedMap = parseInnerObject(value);
            result.put(key, nestedMap);
        }
        
        return result;
    }
    

    
    /** 
     * @param json
     * @return Map<String, String>
     */
    private static Map<String, String> parseInnerObject(String json) {
        Map<String, String> result = new HashMap<>();
        
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        
        // Split into key-value pairs safely
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(":");
            if (colonIndex == -1) continue; // Skip malformed entries
            
            String key = pair.substring(0, colonIndex).replaceAll("[\"{}]", "").trim();
            String value = pair.substring(colonIndex + 1).replaceAll("[\"{}]", "").trim();
            
            result.put(key, value);
        }
        
        return result;
    }
    

    
    /** 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // loadConfig(args[0]);
        loadConfig(null);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/user", new UserHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("UserService started on port " + PORT);
    }
}

class User {
    private int id;
    private String username;
    private String email;
    private String password;

    public User(int id, String username, String email, String password) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Getters and setters remain the same
    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public void setEmail(String email) { this.email = email; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, hash);
            return number.toString(16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "hash_error";
        }
    }

    public String toResponseString(String command) {
        if (command == null) {
            // For GET requests, exclude command field
            return String.format(
                "{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                id, username, email, hashPassword(password)
            );
        } else {
            // For CREATE and UPDATE requests, include command field
            return String.format(
                "{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"command\":\"%s\"}",
                id, username, email, hashPassword(password), command
            );
        }
    }
}


class UserManager {
    private Map<Integer, User> users;

    public UserManager() {
        this.users = new HashMap<>();
    }

    public User addUser(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return null;
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return null;
        }

        if (users.containsKey(user.getId())) {
            return null;
        }

        users.put(user.getId(), user);
        return user;
    }

    public User updateUser(int id, String username, String email, String password) {
        User existingUser = users.get(id);
        if (existingUser == null || id <= 0) {
            return null;
        }

        User updatedUser = new User(
            id, 
            username != null ? username : existingUser.getUsername(),
            email != null ? email : existingUser.getEmail(),
            password != null ? password : existingUser.getPassword()
        );
        users.put(id, updatedUser);
        return updatedUser;
    }

    public boolean deleteUser(int id, String username, String email, String password) {
        User existingUser = users.get(id);
        if (existingUser == null ||
            !existingUser.getUsername().equals(username) ||
            !existingUser.getEmail().equals(email) ||
            !existingUser.getPassword().equals(password)) {
            return false;
        }

        users.remove(id);
        return true;
    }

    public User getUser(int id) {
        return users.get(id);
    }
}

class UserHandler implements HttpHandler {
    private static UserManager userManager = new UserManager();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange);
        } else if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange);
        } else {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String requestBody = getRequestBody(exchange);
        Map<String, String> requestData = parseJsonToMap(requestBody);
        String command = requestData.get("command");

        if (command == null) {
            sendErrorResponse(exchange, 400, "");
            return;
        }

        switch (command) {
            case "create":
                handleCreateUser(exchange, requestData);
                break;
            case "update":
                handleUpdateUser(exchange, requestData);
                break;
            case "delete":
                handleDeleteUser(exchange, requestData);
                break;
            default:
                sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] segments = path.split("/");
        
        if (segments.length != 3) {
            sendErrorResponse(exchange, 400, "");
            return;
        }
    
        try {
            int id = Integer.parseInt(segments[2]);
            User user = userManager.getUser(id);
    
            if (user != null) {
                // Pass null as command to exclude it from the response
                sendResponse(exchange, 200, user.toResponseString(null));
            } else {
                sendErrorResponse(exchange, 404, "");
            }   
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleCreateUser(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String username = requestData.get("username");
            String email = requestData.get("email");
            String password = requestData.get("password");

            if (username == null || email == null || password == null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }

            User user = new User(id, username, email, password);

            if (userManager.getUser(id) != null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }
            
            User createdUser = userManager.addUser(user);
            
            if (createdUser == null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }

            sendResponse(exchange, 200, createdUser.toResponseString(null));
            // sendResponse(exchange, 200, createdUser.toResponseString("create"));
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleUpdateUser(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String username = requestData.get("username");
            String email = requestData.get("email");
            String password = requestData.get("password");

            User updatedUser = userManager.updateUser(id, username, email, password);
            if (updatedUser == null) {
                sendErrorResponse(exchange, 404, "");
                return;
            }
            sendResponse(exchange, 200, updatedUser.toResponseString(null));
            // sendResponse(exchange, 200, updatedUser.toResponseString("update"));
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleDeleteUser(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String username = requestData.get("username");
            String email = requestData.get("email");
            String password = requestData.get("password");

            if (username == null || email == null || password == null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }

            if (!userManager.deleteUser(id, username, email, password)) {
                sendErrorResponse(exchange, 404, "");
                return;
            }

            sendResponse(exchange, 200, "");
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.replaceAll("[{}\"]", "");
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return map;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
        // sendResponse(exchange, statusCode, "Error: " + errorMessage);
        sendResponse(exchange, statusCode, errorMessage);
    }
}