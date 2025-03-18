import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.Document;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

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
            // String content = new String(Files.readAllBytes(Paths.get("config.json")),
            // StandardCharsets.UTF_8);
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
            // System.out.println("Warning: Could not read config.json. Using default port "
            // + PORT + " and IP " + IP);
            // System.out.println("hehe");
        } catch (Exception e) {
            // System.out.println("Warning: Error parsing config.json. Using default port "
            // + PORT + " and IP " + IP);
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
                entry += "}"; // Fix missing closing brace in split
            }

            // Extract the key
            int colonIndex = entry.indexOf(":");
            if (colonIndex == -1)
                continue; // Skip invalid entries

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
            if (colonIndex == -1)
                continue; // Skip malformed entries

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
        loadConfig(args.length > 0 ? args[0] : null);

        // Use IP from config, defaulting to 0.0.0.0 if not set
        String bindAddress = (IP != null && !IP.isEmpty()) ? IP : "0.0.0.0";

        // Ensure PORT is valid
        if (PORT <= 0) {
            PORT = 14001; // Default port
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, PORT), 0);
        server.createContext("/user", new UserHandler());
        server.createContext("/shutdown", new ShutdownHandler(server));
        server.createContext("/restart", new RestartHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("UserService started on " + bindAddress + ":" + PORT);
    }

    static class ShutdownHandler implements HttpHandler {
        private final HttpServer server;

        public ShutdownHandler(HttpServer server) {
            this.server = server;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"shutting_down\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

            // Simple shutdown - SQLite automatically persists data
            System.out.println("Shutdown command received, shutting down UserService");
            server.stop(0);
        }
    }

    static class RestartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // For UserService, we just need to acknowledge the restart command
            // Only OrderService needs special handling for determining data persistence
            String response = "{\"status\":\"restart_received\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

            System.out.println("Restart command received by UserService");
        }
    }
}

class User {
    private int id;
    private String username;
    private String email;
    private String password;
    private Map<Integer, Integer> purchasedProducts; // Key: productId, Value: quantity

    public User(int id, String username, String email, String password) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.purchasedProducts = new HashMap<>(); // added this to initialize the map --> saw current impl. led to an
                                                  // error.
    }

    // Getters and setters remain the same

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void addPurchase(int productId, int quantity) {
        purchasedProducts.merge(productId, quantity, Integer::sum);
    }

    public Map<Integer, Integer> getPurchasedProducts() {
        return purchasedProducts;
    }

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
                    id, username, email, hashPassword(password).toUpperCase());
        } else {
            // For CREATE and UPDATE requests, include command field
            return String.format(
                    "{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"command\":\"%s\"}",
                    id, username, email, hashPassword(password).toUpperCase(), command);
        }
    }
}

class DatabaseManager {
    private static MongoDatabase database;

    public static void initializeDatabase() {
        if (database == null) {
            try {
                String mongoUri = "mongodb://mongoadmin:1234@localhost:27017";
                MongoClient mongoClient = MongoClients.create(mongoUri);
                database = mongoClient.getDatabase("mydatabase");
                System.out.println("MongoDB connected successfully!");
            } catch (Exception e) {
                System.err.println("Error connecting to MongoDB: " + e.getMessage());
            }
        }
    }

    public static MongoDatabase getDatabase() {
        if (database == null) {
            throw new IllegalStateException("Database not initialized. Call initializeDatabase() first.");
        } else {
            return database;
        }
    }

}

class UserManager {
    MongoCollection<Document> collection;
    private final String collectionName = "users";

    public UserManager() {
        try {
            DatabaseManager.initializeDatabase();
            MongoDatabase database = DatabaseManager.getDatabase();
            createUserCollection(database);
            collection = database.getCollection(collectionName);
            collection.createIndex(new Document("id", 1));
        } catch (Exception e) {
            System.err.println("Error initializing user collection: " + e.getMessage());
        }
    }

    private void createUserCollection(MongoDatabase db) {
        List<String> collectionNames = db.listCollectionNames().into(new ArrayList<>());

        if (!collectionNames.contains(collectionName)) {
            db.createCollection(collectionName);
        }
    }

    public User addUser(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return null;
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return null;
        }

        if (getUser(user.getId()) != null) {
            return null;
        }

        try {
            Document newUser = new Document("id", user.getId()).append("username", user.getUsername())
                    .append("email", user.getEmail()).append("password", user.getPassword());
            InsertOneResult result = collection.insertOne(newUser);
            if (result.getInsertedId() != null) {
                System.out.println("User inserted correctly");
                return user;
            } else {
                System.out.println("User not inserted");
                return null;
            }
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                System.err.println("Error: User ID already exists.");
            }
        } catch (Exception e) {
            System.err.println("Error adding user: " + e.getMessage());
        }
        return null;
    }

    public User updateUser(int id, String username, String email, String password) {
        User existingUser = getUser(id);
        if (existingUser == null || id <= 0) {
            return null;
        }
        if (email.trim().isEmpty() || password.trim().isEmpty()) {
            return null;
        }
        UpdateResult result = collection.updateOne(Filters.eq("id", id), Updates.combine(
                Updates.set("username", username),
                Updates.set("email", email),
                Updates.set("password", password)));

        if (result.getModifiedCount() > 0) {
            return getUser(id);
        } else {
            return null;
        }
    }

    public boolean deleteUser(int id, String username, String email, String password) {
        User existingUser = getUser(id);
        if (existingUser == null ||
                !existingUser.getUsername().equals(username) ||
                !existingUser.getEmail().equals(email) ||
                !existingUser.getPassword().equals(password)) {
            return false;
        }
        DeleteResult result = collection.deleteOne(Filters.eq("id", id));
        if (result.getDeletedCount() > 0) {
            return true;
        } else {
            System.out.println("No documents were deleted.");
            return false;
        }
    }

    public User getUser(int id) {
        try {
            Document foundUser = collection.find(Filters.eq("id", id)).first();
            if (foundUser != null) {
                return new User(foundUser.getInteger("id"), foundUser.getString("username"),
                        foundUser.getString("email"), foundUser.getString("password"));
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error adding user: " + e.getMessage());
            return null;
        }
    }
}

class UserHandler implements HttpHandler {
    private static UserManager userManager = new UserManager();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("POST".equalsIgnoreCase(method) && path.matches("/user/\\d+/purchase")) {
            handleAddPurchase(exchange);
        } else if ("GET".equalsIgnoreCase(method) && path.matches("/user/purchased/\\d+")) {
            handleGetPurchased(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange);
        } else if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange);
        } else {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleAddPurchase(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int userId = Integer.parseInt(path.split("/")[2]);

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> requestData = parseJsonToMap(requestBody);

        int productId = Integer.parseInt(requestData.get("product_id"));
        int quantity = Integer.parseInt((requestData.get("quantity")));

        User user = userManager.getUser(userId);
        if (user == null) {
            sendErrorResponse(exchange, 404, "User not found");
            return;
        }

        user.addPurchase(productId, quantity);
        sendResponse(exchange, 200, "{\"status\":\"Purchase added\"}");
    }

    private void handleGetPurchased(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int userId = Integer.parseInt(path.split("/")[3]);
        User user = userManager.getUser(userId);
        if (user == null) {
            sendErrorResponse(exchange, 404, "");
            return;
        }

        Map<Integer, Integer> purchasedProducts = user.getPurchasedProducts();
        StringBuilder jsonResponse = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : purchasedProducts.entrySet()) {
            if (!first) {
                jsonResponse.append(",");
            }
            jsonResponse.append(String.format("\"%d\":%d", entry.getKey(), entry.getValue()));
            first = false;
        }
        jsonResponse.append("}");

        sendResponse(exchange, 200, jsonResponse.toString());
        // sendResponse(exchange, 200, "jsonResponse.toString()");
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
                sendErrorResponse(exchange, 404, "User not found");
            }
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "Invalid user ID");
        }
    }

    private void handleCreateUser(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String username = requestData.get("username");
            String email = requestData.get("email");
            String password = requestData.get("password");
            String regexPattern = "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                    + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
            boolean valid_email = Pattern.compile(regexPattern).matcher(email).matches();

            if (username == null || email == null || !valid_email || password == null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }

            User user = new User(id, username, email, password);

            // if (userManager.getUser(id) != null) {
            // sendErrorResponse(exchange, 400, "");
            // return;
            // }

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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
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
        exchange.getResponseHeaders().set("Content-Type", "application/json");
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