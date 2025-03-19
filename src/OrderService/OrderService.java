import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.bson.Document;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class OrderService {
    private static int PORT;
    private static String IP;
    static String PRODUCT_SERVER_PORT;
    static String PRODUCT_SERVER_IP;
    static String USER_SERVER_PORT;
    static String USER_SERVER_IP;
    public static Map<Integer, UserInfo> userCache = new ConcurrentHashMap<>();


    private static void loadConfig(String path) {
        try {
            if (path == null || path.isEmpty()) {
                // throw new IllegalArgumentException("Path cannot be null or empty.");
                path = "config.json";
            }
            String filePath = new File(path).getAbsolutePath();
            String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            Map<String, Map<String, String>> config = parseNestedJson(content);
            
            Map<String, String> ordService = config.get("OrderService");
            Map<String, String> productService = config.get("ProductService");
            Map<String, String> userService = config.get("UserService");

            if (ordService != null) {
                PORT = Integer.parseInt(ordService.get("port"));
                IP = ordService.get("ip");
            }
            
            if (productService != null) {
                PRODUCT_SERVER_PORT = productService.get("port");
                PRODUCT_SERVER_IP = productService.get("ip");
            }
            
            if (userService != null) {
                USER_SERVER_PORT = userService.get("port");
                USER_SERVER_IP = userService.get("ip");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
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

    public static void main(String[] args) throws IOException {
        loadConfig(args.length > 0 ? args[0] : null);

        // Use IP from config, defaulting to 0.0.0.0 if not set
        String bindAddress = (IP != null && !IP.isEmpty()) ? IP : "0.0.0.0";
        
        // Ensure PORT is valid
        if (PORT <= 0) {
            PORT = 14000; // Default port
        }

        
        
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, PORT), 0);

        int numProcessors = Runtime.getRuntime().availableProcessors();
        server.setExecutor(Executors.newFixedThreadPool(numProcessors * 3)); // maybe 3?

        server.createContext("/product", new ProductHandler());
        server.createContext("/user", new UserHandler());
        server.createContext("/order", new OrderHandler());
        server.createContext("/shutdown", new ShutdownHandler(server));
        server.createContext("/restart", new RestartHandler());
        // server.setExecutor(null);
        
        

        server.start();
        System.out.println("OrderService started on port " + PORT);
    }

    static class ShutdownHandler implements HttpHandler {
        private final HttpServer server;
        
        public ShutdownHandler(HttpServer server) {
            this.server = server;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            new File("shutdown_flag.txt").createNewFile();
            String response = "{\"status\":\"shutting_down\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            
            // SQLite automatically persists data, no need for special handling
            System.out.println("Shutdown command received, shutting down OrderService");
            server.stop(0);
        }
    
    }

    static class RestartHandler implements HttpHandler {
        private static final String FLAG_FILE = "restart_flag.txt";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                // Create a flag file to indicate restart was the first command
                new File(FLAG_FILE).createNewFile();
                System.out.println("Restart command received - data will be preserved");
                
                String response = "{\"status\":\"restart_received\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (IOException e) {
                String errorResponse = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.sendResponseHeaders(500, errorResponse.length());
                OutputStream os = exchange.getResponseBody();
                os.write(errorResponse.getBytes());
                os.close();
            }
        
        }
    }
    

    // TODOTODOTODO
    static {
        // Check if restart was the first command after startup
        File restartFlag = new File("restart_flag.txt");
        File shutdownFlag = new File("shutdown_flag.txt");
        if (!restartFlag.exists() && shutdownFlag.exists()) {
            // No restart flag, so wipe all databases
            System.out.println("No restart flag detected - wiping all databases");
            MongoDatabase database = DatabaseManager.getDatabase();
            MongoCollection<Document> ordersCollection = database.getCollection("orders");
            MongoCollection<Document> productsCollection = database.getCollection("products");;
            MongoCollection<Document> usersCollection = database.getCollection("users");;
            
            try {
                // Wipe OrderService database
                ordersCollection.deleteMany(new Document());
                
                // Wipe UserService database
                usersCollection.deleteMany(new Document());
                
                // Wipe ProductService database
                productsCollection.deleteMany(new Document());

                shutdownFlag.delete();
            } catch (Exception e) {
                System.err.println("Error during database wiping: " + e.getMessage());
            }
        } else {
            System.out.println("Restart flag detected - keeping existing data in all databases");
            // Delete the flag file after processing
            restartFlag.delete();
        }
    }}

    class UserInfo {
        public int id;
        public String username;
        public String email;
        public String password;
        public Map<Integer, Integer> purchasedProducts = new HashMap<>();
    
        public UserInfo(int id, String username, String email, String password) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.password = password;
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
    
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"id\":").append(id).append(",");
            sb.append("\"username\":\"").append(username).append("\",");
            sb.append("\"email\":\"").append(email).append("\",");
            sb.append("\"password\":\"").append(hashPassword(password)).append("\"");
            sb.append("}");
            return sb.toString();
    
            // sb.append("\"password\":\"").append(password).append("\",");
            // sb.append("\"password\":\"").append(hashPassword(password)).append("\",");
            // sb.append("\"purchasedProducts\":").append(convertMapToJson(purchasedProducts));
        }
    
        private String convertMapToJson(Map<Integer, Integer> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }

    class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
    
            String userServerUrl = String.format("http://%s:%s%s", 
                OrderService.USER_SERVER_IP, 
                OrderService.USER_SERVER_PORT, 
                exchange.getRequestURI().getPath());
    
            // Route based on URL pattern.
            if (path.matches("/user/\\d+/purchase")) {
                try {
                    forwardRequest(exchange, userServerUrl, null);
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            } else if (path.matches("/user/purchased/\\d+")) {
                try {
                    forwardRequest(exchange, userServerUrl, null);
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            } else if (path.matches("/user/\\d+")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange);
                } else {
                    sendErrorResponse(exchange, 405, "Invalid Request");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // For create, update, delete commands.
                handlePost(exchange);
            } else {
                sendErrorResponse(exchange, 400, "Invalid Request");
            }
        }
    
        private void handlePost(HttpExchange exchange) throws IOException {
            String userServerUrl = String.format("http://%s:%s%s", 
                OrderService.USER_SERVER_IP, 
                OrderService.USER_SERVER_PORT, 
                exchange.getRequestURI().getPath());
    
            // Read request body once and store it
            String requestBody = getRequestBody(exchange);
            Map<String, String> requestData = parseJsonToMap(requestBody);
            String command = requestData.get("command");
    
            if (command == null) {
                sendErrorResponse(exchange, 400, "Missing command");
                return;
            }
            switch (command) {
                case "create":
                    handleCreateUser(exchange, requestData, userServerUrl, requestBody);
                    break;
                case "update":
                    handleUpdateUser(exchange, requestData, userServerUrl, requestBody);
                    break;
                case "delete":
                    handleDeleteUser(exchange, requestData, userServerUrl, requestBody);
                    break;
                default:
                    sendErrorResponse(exchange, 400, "Unknown command");
                    break;
            }
        }

        private String forwardRequest2(HttpExchange exchange, String targetUrl, String preReadBody) throws IOException, URISyntaxException {
            // StringBuilder responseBuilder = new StringBuilder();
            String responseBuilder = "";
            try {
                // Create connection to target server
                URI uri = new URI(targetUrl);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(exchange.getRequestMethod());
                
                // Copy request headers
                exchange.getRequestHeaders().forEach((key, values) -> {
                    for (String value : values) {
                        conn.addRequestProperty(key, value);
                    }
                });
                
                // Forward request body if present
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    conn.setDoOutput(true);
                    if (preReadBody != null) {
                        // Use the pre-read body
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(preReadBody.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
                
                // Get response from target server
                int responseCode = conn.getResponseCode();
                InputStream responseBody;
                try {
                    responseBody = conn.getInputStream();
                } catch (IOException e) {
                    responseBody = conn.getErrorStream();
                }
                
                conn.disconnect();
            } catch (IOException e) {
                String errorMessage = "" + e.getMessage();
                exchange.sendResponseHeaders(500, errorMessage.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                }
            }
            return responseBuilder.toString();
        }
    
        private void handleGet(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            
            if (segments.length != 3) {
                sendErrorResponse(exchange, 400, "Invalid request");
                return;
            }
            try {
                int id = Integer.parseInt(segments[2]);
                if (OrderService.userCache.containsKey(id)) {
                    UserInfo user = OrderService.userCache.get(id);
                    sendResponse(exchange, 200, user.toJson());
                } else {
                    try {
                        forwardRequest(exchange, String.format("http://%s:%s%s", 
                            OrderService.USER_SERVER_IP, 
                            OrderService.USER_SERVER_PORT, 
                            path), 
                            null);
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                    }  
                }
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid user ID");
            }
        }
    
        private void handleCreateUser(HttpExchange exchange, Map<String, String> requestData, String url, String requestBody) throws IOException {
            try {
                int id = Integer.parseInt(requestData.get("id"));
                String username = requestData.get("username");
                String email = requestData.get("email");
                String password = requestData.get("password");
    
                if (username == null || email == null || password == null || username.equals("") || email.equals("") || password.equals("")) {
                    sendErrorResponse(exchange, 400, "");
                    return;
                }
                if (OrderService.userCache.containsKey(id)) {
                    sendErrorResponse(exchange, 409, "");
                    return;
                }
                
                UserInfo user = new UserInfo(id, username, email, password);
                OrderService.userCache.put(id, user);   
                sendResponse(exchange, 200, user.toJson());
     
                
                try {
                    String userServiceUrl = String.format("http://%s:%s/user", 
                        OrderService.USER_SERVER_IP, 
                        OrderService.USER_SERVER_PORT);
                    forwardRequest2(exchange, userServiceUrl, requestBody);
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                sendErrorResponse(exchange, 400, "Invalid Request");
            }
        }
    
        private void handleUpdateUser(HttpExchange exchange, Map<String, String> requestData, String url, String requestBody) throws IOException {
            try {
                int id = Integer.parseInt(requestData.get("id"));
                String username = requestData.get("username");
                String email = requestData.get("email");
                String password = requestData.get("password");
    
                if (OrderService.userCache.containsKey(id)) {
                    // User exists in cache
                    UserInfo user = OrderService.userCache.get(id);
                    if (username != null && !username.equals("")) {
                        user.username = username;
                    }
                    if (email != null && !email.equals("")) {
                        user.email = email;
                    }
                    if (password != null && !password.equals("")) {
                        user.password = password;
                    }
                    OrderService.userCache.put(id, user);
                    sendResponse(exchange, 200, user.toJson());

                    try {
                        forwardRequest2(exchange, url, requestBody);
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                        sendResponse(exchange, 200, user.toJson());
                    }
                    
                } else {
                    // If the user doesn't exist in cache
                    try {
                        forwardRequest(exchange, url, requestBody);
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                        sendErrorResponse(exchange, 404, "");
                    }
                }
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "");
            }
        }
    
        private void handleDeleteUser(HttpExchange exchange, Map<String, String> requestData, String url, String requestBody) throws IOException {
            try {
                int id = Integer.parseInt(requestData.get("id"));
                String username = requestData.get("username");
                String email = requestData.get("email");
                String password = requestData.get("password");
                
                if (OrderService.userCache.containsKey(id)) {
                    if (username == null || email == null || password == null || 
                        username.equals("") || email.equals("") || password.equals("")) {
                        sendErrorResponse(exchange, 400, "");
                        return;
                    }
                    OrderService.userCache.remove(id);

                    sendResponse(exchange, 200, "");

                    try {
                        forwardRequest2(exchange, url, requestBody);
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                        sendResponse(exchange, 200, "");
                    }
                } else {
                    try {
                        forwardRequest(exchange, url, requestBody);
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                        sendErrorResponse(exchange, 404, "");
                    }
                }
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "");            
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Server error");
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
    
        // Updated method that can take a pre-read request body
        private void forwardRequest(HttpExchange exchange, String targetUrl, String preReadBody) throws IOException, URISyntaxException {
            try {
                // Create connection to target server
                URI uri = new URI(targetUrl);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(exchange.getRequestMethod());
                
                // Copy request headers
                exchange.getRequestHeaders().forEach((key, values) -> {
                    for (String value : values) {
                        conn.addRequestProperty(key, value);
                    }
                });
                
                // Forward request body if present
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    conn.setDoOutput(true);
                    if (preReadBody != null) {
                        // Use the pre-read body
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(preReadBody.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
                
                // Get response from target server
                int responseCode = conn.getResponseCode();
                InputStream responseBody;
                try {
                    responseBody = conn.getInputStream();
                } catch (IOException e) {
                    responseBody = conn.getErrorStream();
                }
                
                // Forward response headers
                conn.getHeaderFields().forEach((key, values) -> {
                    if (key != null) {  // Skip status line
                        exchange.getResponseHeaders().put(key, values);
                    }
                });
                
                // Forward response to client
                exchange.sendResponseHeaders(responseCode, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    responseBody.transferTo(os);
                }
                
                conn.disconnect();
            } catch (IOException e) {
                String errorMessage = "" + e.getMessage();
                exchange.sendResponseHeaders(500, errorMessage.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                }
            }
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
            sendResponse(exchange, statusCode, errorMessage);
        }
    }



class ProductHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        // Forward the request to the Product Server
        String productServerUrl = String.format("http://%s:%s%s", 
            OrderService.PRODUCT_SERVER_IP, 
            OrderService.PRODUCT_SERVER_PORT, 
            exchange.getRequestURI().getPath());
        
        if ("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
            try {
                forwardRequest(exchange, productServerUrl);
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        } else {
            String errorMessage = "Method not supported";
            exchange.sendResponseHeaders(405, errorMessage.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorMessage.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void forwardRequest(HttpExchange exchange, String targetUrl) throws IOException, URISyntaxException {
        try {
            // Create connection to target server
            URI uri = new URI(targetUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(exchange.getRequestMethod());
            
            // Copy request headers
            exchange.getRequestHeaders().forEach((key, values) -> {
                for (String value : values) {
                    conn.addRequestProperty(key, value);
                }
            });
            
            // Forward request body if present (for POST requests)
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    exchange.getRequestBody().transferTo(os);
                }
            }
            
            // Get response from target server
            int responseCode = conn.getResponseCode();
            InputStream responseBody;
            try {
                responseBody = conn.getInputStream();
            } catch (IOException e) {
                responseBody = conn.getErrorStream();
            }
            
            // Forward response headers
            conn.getHeaderFields().forEach((key, values) -> {
                if (key != null) {  // Skip status line
                    exchange.getResponseHeaders().put(key, values);
                }
            });
            
            // Forward response to client
            exchange.sendResponseHeaders(responseCode, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                responseBody.transferTo(os);
            }
            
            conn.disconnect();
        } catch (IOException e) {
            String errorMessage = "" + e.getMessage();
            exchange.sendResponseHeaders(500, errorMessage.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorMessage.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}

class Product {
    private int id;
    private String name;
    private String description;
    private double price;
    private int quantity;

    public Product(int id, String name, String description, double price, int quantity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public static Product fromJson(String json) {
        Map<String, String> map = parseJsonToMap(json);
        return new Product(
            Integer.parseInt(map.get("id")),
            map.get("name"),
            map.get("description"),
            Double.parseDouble(map.get("price")),
            Integer.parseInt(map.get("quantity"))
        );
    }

    private static Map<String, String> parseJsonToMap(String json) {
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
}

class DatabaseManager {
    private static MongoDatabase database;

    public static void initializeDatabase() {
        if (database == null) {
            try {
                String mongoUri = "mongodb://mongoadmin:1234@142.1.114.66:27017";
                MongoClient mongoClient = MongoClients.create(mongoUri);
                database = mongoClient.getDatabase("mydatabase");
                System.out.println("MongoDB connected successfully on PRODUCT service!");
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

class Order {
    private static int nextId = 1;
    public int id;
    final int productId;
    final int userId;
    final int quantity;
    final String status;

    // TODOTODOTODO
   public Order(int productId, int userId, int quantity, String status) {
        MongoDatabase database = DatabaseManager.getDatabase();
        MongoCollection<Document> collection = database.getCollection("orders");

        this.id = nextId++;
        try {
            // Query the collection to find the largest "id" value (sorted in descending order)
            FindIterable<Document> result = collection.find()
            .sort(Sorts.descending("id"))  // Sort by the "id" field in descending order
            .limit(1);  // Limit the result to just one document

            // Retrieve the document with the largest "id"
            Document largestIdDocument = result.first();
            if (largestIdDocument != null) {
                this.id = largestIdDocument.getInteger("id") + 1;
            }
        } catch (Exception e) {
            System.err.println("Error getting product: " + e.getMessage());
        }
        // System.out.println("this id: " + this.id);
        this.productId = productId;
        this.userId = userId;
        this.quantity = quantity;
        this.status = status;
   }

    public int getId() { return id; }
    public int getProductID() { return productId; }
    public int getUserID() { return userId; }
    public int getQuantity() { return quantity; }

   public String toJson() {
       return String.format(
           "{\"id\":%d,\"product_id\":%d,\"user_id\":%d,\"quantity\":%d,\"status\":\"%s\"}",
           id, productId, userId, quantity, status
       );
   }
}

class OrderManager {
    MongoCollection<Document> collection;
    private final String collectionName = "orders";

    public OrderManager() {
        try {
            DatabaseManager.initializeDatabase();
            MongoDatabase database = DatabaseManager.getDatabase();
            createOrderCollection(database);
            collection = database.getCollection(collectionName);
            collection.createIndex(new Document("id", 1));
        } catch (Exception e) {
            System.err.println("Error initializing order collection: " + e.getMessage());
        }
    }

    private void createOrderCollection(MongoDatabase db) {
        List<String> collectionNames = db.listCollectionNames().into(new ArrayList<>());

        if (!collectionNames.contains(collectionName)) {
            db.createCollection(collectionName);
        }
    }

    public void addOrder(Order order) {
        try {
            Document newOrder = new Document("id", order.getId())
                    .append("product_id", order.getProductID())
                    .append("user_id", order.getUserID())
                    .append("quantity", order.getQuantity());
            collection.insertOne(newOrder);
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                System.err.println("Error: Order ID already exists.");
            }
        } catch (Exception e) {
            System.err.println("Error adding order: " + e.getMessage());
        }
    }
}

class OrderHandler implements HttpHandler {
    private static OrderManager orderManager = new OrderManager();
    private final Map<Integer, Product> productCache = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            
            if ("POST".equalsIgnoreCase(method)) {
                handleOrderCommand(exchange);
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }
    private static Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.replaceAll("[{}\"]", "").trim(); // Remove braces and quotes
    
        for (String pair : json.split(",")) {
            String[] keyValue = pair.split(":", 2); // Ensure only first ":" is split
            if (keyValue.length == 2) {
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();
    
                // Try parsing as a number
                if (value.matches("-?\\d+")) {  // Integer
                    map.put(key, Integer.parseInt(value));
                } else if (value.matches("-?\\d+\\.\\d+")) {  // Double
                    map.put(key, Double.parseDouble(value));
                } else if ("true".equalsIgnoreCase(value)) {  // Boolean true
                    map.put(key, Boolean.TRUE);
                } else if ("false".equalsIgnoreCase(value)) {  // Boolean false
                    map.put(key, Boolean.FALSE);
                } else if ("null".equalsIgnoreCase(value)) {  // null
                    map.put(key, null);
                } else {
                    map.put(key, value);  // String
                }
            }
        }
        return map;
    }

    private void handleOrderCommand(HttpExchange exchange) throws IOException, URISyntaxException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        // System.out.println("reqbody" + requestBody);
        try {
            // Clean up the input string and parse JSON
            Map<String, Object> commandMap = parseJson(requestBody);
            // System.out.println("commandmap: " + commandMap);
            // Extract and validate command
            String command = (String) commandMap.get("command");
            if (!"place order".equals(command)) {
                sendErrorResponse(exchange, "Invalid Command");
                return;
            }
    
            // Extract and validate numerical values
            int productId = ((Number) commandMap.get("product_id")).intValue();
            int userId = ((Number) commandMap.get("user_id")).intValue();
            int quantity = ((Number) commandMap.get("quantity")).intValue();
            // System.out.println("productid: " + productId + " userID: " + userId + " quantity: " + quantity);


            // Check for invalid inputs (-1)
            if (productId == -1 || userId == -1 || quantity == -1) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Additional validation for positive values
            if (productId <= 0 || userId <= 0 || quantity <= 0) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Check if product exists and get its current state
            Product product = getProduct(productId);
            if (product == null) {
                // System.out.println("NULL");
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
            if (product == null) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Verify user exists
            if (!checkUserExists(userId)) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // System.out.println("prodID: " + productId);
            // System.out.println("quantity: " + getProductQuantity(productId));
            // System.out.println("cmdquantity: " + quantity);
            // Check if enough stock is available
            if (getProductQuantity(productId) < quantity) {
                sendErrorResponse(exchange, "Exceeded quantity limit");
                return;
            }

            // Create Order object
            Order order = new Order(productId, userId, quantity, "Success");
            orderManager.addOrder(order);
    
            // Update product quantity
            int newQuantity = getProductQuantity(productId) - quantity;
            String updateJson = String.format(
                "{\"command\":\"update\",\"id\":%d,\"quantity\":%d}",
                productId, newQuantity
            );

            // Send update request to Product service
            String productUrl = String.format("http://%s:%s/product",
                OrderService.PRODUCT_SERVER_IP,
                OrderService.PRODUCT_SERVER_PORT);
            
            String response = sendPostRequest(productUrl, updateJson);
            if (response == null) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Update cache with new quantity
            product.setQuantity(newQuantity);
            productCache.put(productId, product);

            // Update user's purchase history in UserService
            String userServiceUrl = String.format("http://%s:%s/user/%d/purchase",
            OrderService.USER_SERVER_IP,
            OrderService.USER_SERVER_PORT,
            userId);

            String purchaseJson = String.format("{\"product_id\":%d,\"quantity\":%d}", productId, quantity);
            String responseOrder = sendPostRequest(userServiceUrl, purchaseJson);

            if (responseOrder == null) {

                sendErrorResponse(exchange, "Failed to update user purchase history");
                return;
            }
    
            // Send success response with order details
            sendSuccessResponse(exchange, order);
    
        } catch (Exception e) {
            sendErrorResponse(exchange, "Invalid Request");
        }
    }
    
    private void sendErrorResponse(HttpExchange exchange, String status) throws IOException {
        String errorJson = String.format("{\"status\":\"%s\"}", status);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = errorJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(400, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private void sendSuccessResponse(HttpExchange exchange, Order order) throws IOException {
        String successJson = String.format(
        "{" +
        "\"product_id\":%d," +
        "\"user_id\":%d," +
        "\"quantity\":%d," +
        "\"status\":\"Success\"" +
        "}",
        order.productId,
        order.userId,
        order.quantity
    );
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = successJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    // TODOTODOTODO
    private Product getProduct(int id) throws IOException, URISyntaxException  {
        MongoDatabase database = DatabaseManager.getDatabase();
        MongoCollection<Document> collection = database.getCollection("products");

        try {
            Document foundProduct = collection.find(Filters.eq("id", id)).first();
            if (foundProduct != null) {
                return new Product(foundProduct.getInteger("id"), foundProduct.getString("name"),
                        foundProduct.getString("description"), foundProduct.getDouble("price"),
                        foundProduct.getInteger("quantity"));
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error getting product: " + e.getMessage());
            return null;
        }
    }

    private int getProductQuantity(int id) throws IOException, URISyntaxException  {
        MongoDatabase database = DatabaseManager.getDatabase();
        MongoCollection<Document> collection = database.getCollection("products");

        try {
            Document foundProduct = collection.find(Filters.eq("id", id)).first();
            if (foundProduct != null) {
                return foundProduct.getInteger("quantity");
            }
            return -1;
        } catch (Exception e) {
            System.err.println("Error getting product quantity: " + e.getMessage());
            return -1;
        }
    }

    // TODOTODOTODO
    private boolean checkUserExists(int id) throws IOException, URISyntaxException {
        MongoDatabase database = DatabaseManager.getDatabase();
        MongoCollection<Document> collection = database.getCollection("users");

        try {
            Document foundUser = collection.find(Filters.eq("id", id)).first();
            if (foundUser != null) {
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error checking user exists: " + e.getMessage());
            return false;
        }
    }

    private String sendPostRequest(String urlString, String jsonBody) throws IOException, URISyntaxException {
        URI uri = new URI(urlString);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try {
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            }
        } finally {
            conn.disconnect();
        }
        return null;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}