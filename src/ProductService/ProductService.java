import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class ProductService {
    private static int PORT;
    private static String IP;

    private static void loadConfig(String path) {
        try {
            if (path == null || path.isEmpty()) {
                path = "config.json";
            }
            String filePath = new File(path).getAbsolutePath();
            String content = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
            Map<String, Map<String, String>> config = parseNestedJson(content);
            Map<String, String> productService = config.get("ProductService");

            if (productService != null) {
                String portStr = productService.get("port");
                String ipStr = productService.get("ip");
                
                if (portStr != null) {
                    PORT = Integer.parseInt(portStr);
                }
                
                if (ipStr != null) {
                    IP = ipStr;
                }
            }
        } catch (Exception e) {
            // Silently handle config loading errors
            System.err.println("Error loading config: " + e.getMessage());
        }
    }

    private static Map<String, Map<String, String>> parseNestedJson(String json) {
        Map<String, Map<String, String>> result = new HashMap<>();
        
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        
        String[] topLevelEntries = json.split("},");
        for (String entry : topLevelEntries) {
            entry = entry.trim();
            if (!entry.endsWith("}")) {
                entry += "}";
            }
    
            int colonIndex = entry.indexOf(":");
            if (colonIndex == -1) continue;
            
            String key = entry.substring(0, colonIndex).replaceAll("[\"{}]", "").trim();
            String value = entry.substring(colonIndex + 1).trim();
            
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
        
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(":");
            if (colonIndex == -1) continue;
            
            String key = pair.substring(0, colonIndex).replaceAll("[\"{}]", "").trim();
            String value = pair.substring(colonIndex + 1).replaceAll("[\"{}]", "").trim();
            
            result.put(key, value);
        }
        
        return result;
    }

    public static void main(String[] args) throws IOException {
        loadConfig(args.length > 0 ? args[0] : null);
        System.out.println("PORT: " + PORT);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Use IP from config, defaulting to 0.0.0.0 if not set
        String bindAddress = (IP != null && !IP.isEmpty()) ? IP : "0.0.0.0";
        
        // Ensure PORT is valid
        if (PORT <= 0) {
            PORT = 15000; // Default port
        }
        server.createContext("/product", new ProductHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("ProductService started on port " + PORT);
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

    // Getters and setters remain the same
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(double price) { this.price = price; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String toResponseString(String command) {
        if (command == null) {
            return String.format(
                "{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%.2f,\"quantity\":%d}",
                id, name, description, price, quantity
            );
        } else {
            return String.format(
                "{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%.2f,\"quantity\":%d,\"command\":\"%s\"}",
                id, name, description, price, quantity, command
            );
        }
    }
}

class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:products.db";
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
            
            stmt.execute("CREATE TABLE IF NOT EXISTS products (" +
                         "id INTEGER PRIMARY KEY, " +
                         "name TEXT NOT NULL, " +
                         "description TEXT NOT NULL, " +
                         "price REAL NOT NULL, " +
                         "quantity INT NOT NULL)");
            System.out.println("Database initialized successfully");

        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
        }
    }
}

class ProductManager {
    private Map<Integer, Product> products;

    public ProductManager() {
        try {
            DatabaseManager.getConnection().close();
        } catch (SQLException e) {
            System.err.println("Error initializing product manager: " + e.getMessage());
        }
    }

    public Product addProduct(Product product) {
        System.out.println("REACHED addProduct");
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            System.out.println("RETURN NULL");
            return null;
        }
        System.out.println("SECOND IF");
        if (getProduct(product.getId()) != null) {
            System.out.println("RETURN NULL");
            return null;
        }
        System.out.println("BEFORE TRY");
        try (Connection conn = DatabaseManager.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO products (id, name, description, price, quantity) VALUES (?, ?, ?, ?, ?)")) {
            System.out.println("INSIDE TRY");
            stmt.setInt(1, product.getId());
            stmt.setString(2, product.getName());
            stmt.setString(3, product.getDescription());
            stmt.setDouble(4, product.getPrice());
            stmt.setInt(5, product.getQuantity());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("ROWS AFFECTED: " + rowsAffected);
                return product;
            }
            System.out.println("BOTTOM NULL RETURN");
            return null;
        } catch (SQLException e) {
            System.err.println("Error adding product: " + e.getMessage());
            return null;
        }
    }

    public Product updateProduct(int id, String name, String description, Double price, Integer quantity) {
        System.out.println("IN UPDATE PRODUCT");
        Product existingProduct = getProduct(id);

        if (existingProduct == null || id <= 0) {
            return null;
        }
        System.out.println("BEFORE TRY");
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE products SET name = ?, description = ?, price = ?, quantity = ? WHERE id = ?")) {
                 
            stmt.setString(1, name != null ? name : existingProduct.getName());
            stmt.setString(2, description != null ? description : existingProduct.getDescription());
            stmt.setDouble(3, price != null ? price : existingProduct.getPrice());
            stmt.setInt(4, quantity != null ? quantity : existingProduct.getQuantity());
            stmt.setInt(5, id);
        
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                return getProduct(id);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error updating product: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteProduct(int id, String name, Double price, Integer quantity) {
        Product product = getProduct(id);
        if (product != null) {
            boolean matches = (name.equals(product.getName())) &&
                              (price.equals(product.getPrice())) &&
                              (quantity.equals(product.getQuantity()));
            if (matches) {
                try (Connection conn = DatabaseManager.getConnection();
                    PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM product WHERE id = ?")) {
                        
                    stmt.setInt(1, id);
                    
                    int rowsAffected = stmt.executeUpdate();
                    return rowsAffected > 0;
                } catch (SQLException e) {
                    System.err.println("Error deleting product: " + e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    public Product getProduct(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM products WHERE id = ?")) {
                 
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getInt("quantity")
                    );
                }
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving product: " + e.getMessage());
            return null;
        }
    }
}

class ProductHandler implements HttpHandler {
    private static ProductManager productManager = new ProductManager();

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
                handleCreateProduct(exchange, requestData);
                break;
            case "update":
                handleUpdateProduct(exchange, requestData);
                break;
            case "delete":
                handleDeleteProduct(exchange, requestData);
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
            Product product = productManager.getProduct(id);
    
            if (product != null) {
                sendResponse(exchange, 200, product.toResponseString(null));
            } else {
                sendErrorResponse(exchange, 404, "");
            }   
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleCreateProduct(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String name = requestData.get("name");
            String description = requestData.getOrDefault("description", "");
            double price = Double.parseDouble(requestData.get("price"));
            int quantity = Integer.parseInt(requestData.get("quantity"));

            if (name == null || price < 0 || quantity < 0) {
                sendErrorResponse(exchange, 400, "");
                return;
            }

            Product product = new Product(id, name, description, price, quantity);

            if (productManager.getProduct(id) != null) {
                sendErrorResponse(exchange, 409, "");
                return;
            }
            
            Product createdProduct = productManager.addProduct(product);
            
            if (createdProduct == null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }

            sendResponse(exchange, 200, createdProduct.toResponseString(null));
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleUpdateProduct(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String name = requestData.get("name");
            String description = requestData.get("description");
            Double price = requestData.containsKey("price") ? Double.parseDouble(requestData.get("price")) : null;
            Integer quantity = requestData.containsKey("quantity") ? Integer.parseInt(requestData.get("quantity")) : null;
            
            if (name == null || price < 0 || quantity < 0 || description == null) {
                sendErrorResponse(exchange, 400, "");
                return;
            }


            Product updatedProduct = productManager.updateProduct(id, name, description, price, quantity);
            if (updatedProduct == null) {
                sendErrorResponse(exchange, 404, "");
                return;
            }
            sendResponse(exchange, 200, updatedProduct.toResponseString(null));
            // sendResponse(exchange, 200, requestData.toString());
        } catch (NumberFormatException e) {
            sendErrorResponse(exchange, 400, "");
        }
    }

    private void handleDeleteProduct(HttpExchange exchange, Map<String, String> requestData) throws IOException {
        try {
            int id = Integer.parseInt(requestData.get("id"));
            String name = requestData.get("name");
            Double price = requestData.containsKey("price") ? Double.parseDouble(requestData.get("price")) : null;
            Integer quantity = requestData.containsKey("quantity") ? Integer.parseInt(requestData.get("quantity")) : null;

            if (!productManager.deleteProduct(id, name, price, quantity)) {
                sendErrorResponse(exchange, 404, "" );
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
        sendResponse(exchange, statusCode, errorMessage);
    }
}