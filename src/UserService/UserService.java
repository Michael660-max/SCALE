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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        loadConfig(args.length > 0 ? args[0] : null);
        
        // Use IP from config, defaulting to 0.0.0.0 if not set
        String bindAddress = (IP != null && !IP.isEmpty()) ? IP : "0.0.0.0";
        
        // Ensure PORT is valid
        if (PORT <= 0) {
            PORT = 14001; // Default port
        }
        
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, PORT), 0);
        server.createContext("/user", new UserHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();
        
        System.out.println("UserService started on " + bindAddress + ":" + PORT);
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
        this.purchasedProducts = new HashMap<>(); // added this to initialize the map --> saw current impl. led to an error.
    }

    // Getters and setters remain the same

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public void setEmail(String email) { this.email = email; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
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
                id, username, email, password
            );
        } else {
            // For CREATE and UPDATE requests, include command field
            return String.format(
                "{\"id\":%d,\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\",\"command\":\"%s\"}",
                id, username, email, password, command
            );
        }
    }
}

class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:users.db";
    private static boolean initialized = false;
    
    static {
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite JDBC driver loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("Error loading SQLite driver: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        // Initialize database on first connection
        if (!initialized) {
            initialized = true;
            initializeDatabase();
        }
        return DriverManager.getConnection(DB_URL);
    }
    
    private static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                         "id INTEGER PRIMARY KEY, " +
                         "username TEXT NOT NULL, " +
                         "email TEXT NOT NULL, " +
                         "password TEXT NOT NULL)");
            System.out.println("Database initialized successfully");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }
}

class UserManager {

    public UserManager() {
        try {
            DatabaseManager.getConnection().close();
        } catch (SQLException e) {
            System.err.println("Error initializing user manager: " + e.getMessage());
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

        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?)")) {

            stmt.setInt(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getPassword());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return user;
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return null;
        }
    }

    public User updateUser(int id, String username, String email, String password) {
        User existingUser = getUser(id);
        if (existingUser == null || id <= 0) {
            return null;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?")) {
                 
            stmt.setString(1, username != null ? username : existingUser.getUsername());
            stmt.setString(2, email != null ? email : existingUser.getEmail());
            stmt.setString(3, password != null ? password : existingUser.getPassword());
            stmt.setInt(4, id);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return getUser(id);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error updating user: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteUser(int id, String username, String email, String password) {
        User existingUser = getUser(id);
        if (existingUser == null ||
            !existingUser.getUsername().equals(username) ||
            !existingUser.getEmail().equals(email)) {
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM users WHERE id = ?")) {
                 
            stmt.setInt(1, id);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    public User getUser(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM users WHERE id = ?")) {
                 
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("password")
                    );
                }
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user: " + e.getMessage());
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
            case "create" -> handleCreateUser(exchange, requestData);
            case "update" -> handleUpdateUser(exchange, requestData);
            case "delete" -> handleDeleteUser(exchange, requestData);
            default -> sendErrorResponse(exchange, 400, "");
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