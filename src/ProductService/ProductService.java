import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            if (colonIndex == -1)
                continue;

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
            if (colonIndex == -1)
                continue;

            String key = pair.substring(0, colonIndex).replaceAll("[\"{}]", "").trim();
            String value = pair.substring(colonIndex + 1).replaceAll("[\"{}]", "").trim();

            result.put(key, value);
        }

        return result;
    }

    public static void main(String[] args) throws IOException {
        loadConfig(args.length > 0 ? args[0] : null);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Use IP from config, defaulting to 0.0.0.0 if not set
        String bindAddress = (IP != null && !IP.isEmpty()) ? IP : "0.0.0.0";

        // Ensure PORT is valid
        if (PORT <= 0) {
            PORT = 15000; // Default port
        }
        server.createContext("/product", new ProductHandler());
        server.createContext("/shutdown", new ShutdownHandler(server));
        server.createContext("/restart", new RestartHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("ProductService started on " + bindAddress + ":" + PORT);
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
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();

            System.out.println("Shutdown command received, shutting down ProductService");
            server.stop(0);
        }
    }

    static class RestartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"restart_received\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();

            System.out.println("Restart command received by ProductService");
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

    // Getters and setters remain the same
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String toResponseString(String command) {
        if (command == null) {
            return String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%.2f,\"quantity\":%d}",
                    id, name, description, price, quantity);
        } else {
            return String.format(
                    "{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%.2f,\"quantity\":%d,\"command\":\"%s\"}",
                    id, name, description, price, quantity, command);
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

class ProductManager {
    MongoCollection<Document> collection;
    private final String collectionName = "products";

    public ProductManager() {
        try {
            DatabaseManager.initializeDatabase();
            MongoDatabase database = DatabaseManager.getDatabase();
            createProductCollection(database);
            collection = database.getCollection(collectionName);
            collection.createIndex(new Document("id", 1));
        } catch (Exception e) {
            System.err.println("Error initializing user collection: " + e.getMessage());
        }
    }

    private void createProductCollection(MongoDatabase db) {
        List<String> collectionNames = db.listCollectionNames().into(new ArrayList<>());

        if (!collectionNames.contains(collectionName)) {
            db.createCollection(collectionName);
        }
    }

    public Product addProduct(Product product) {
        if (getProduct(product.getId()) != null) {
            return null;
        }

        try {
            Document newProduct = new Document("id", product.getId())
                    .append("name", product.getName())
                    .append("description", product.getDescription())
                    .append("price", product.getPrice())
                    .append("quantity", product.getQuantity());
            InsertOneResult result = collection.insertOne(newProduct);
            return (result.getInsertedId() != null) ? product: null;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                System.err.println("Error: Product ID already exists.");
            }
        } catch (Exception e) {
            System.err.println("Error adding product: " + e.getMessage());
        }
        return null;
    }

    public Product updateProduct(int id, String name, String description, Double price, Integer quantity) {

        Product existingProduct = getProduct(id);

        if (existingProduct == null || id <= 0) {
            return null;
        }

        UpdateResult result = collection.updateOne(Filters.eq("id", id), Updates.combine(
                Updates.set("name", (name != null) ? name : existingProduct.getName()),
                Updates.set("description", (description != null) ? description : existingProduct.getDescription()),
                Updates.set("price", (price != null) ? price : existingProduct.getPrice()),
                Updates.set("quantity", (quantity != null) ? quantity : existingProduct.getQuantity())));

        return (result.getModifiedCount() > 0) ? getProduct(id): null;
    }

    public boolean deleteProduct(int id, String name, Double price, Integer quantity) {
        Product existingProduct = getProduct(id);
        if (existingProduct == null ||
                (name != null && !existingProduct.getName().equals(name)) ||
                (price != null && Double.compare(existingProduct.getPrice(), price) != 0) ||
                (quantity != null && existingProduct.getQuantity() != quantity)) {
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

    public Product getProduct(int id) {
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
            Integer quantity = requestData.containsKey("quantity") ? Integer.parseInt(requestData.get("quantity"))
                    : null;

            if (price != null && price < 0 || quantity != null && quantity < 0) {
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
            Integer quantity = requestData.containsKey("quantity") ? Integer.parseInt(requestData.get("quantity"))
                    : null;

            if (name == null || !productManager.deleteProduct(id, name, price, quantity)) {
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
        sendResponse(exchange, statusCode, errorMessage);
    }
}