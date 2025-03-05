import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
        loadConfig(null);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/product", new ProductHandler());
        server.setExecutor(null);
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

class ProductManager {
    private Map<Integer, Product> products;

    public ProductManager() {
        this.products = new HashMap<>();
    }

    public Product addProduct(Product product) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            return null;
        }

        if (products.containsKey(product.getId())) {
            return null;
        }

        products.put(product.getId(), product);
        return product;
    }

    public Product updateProduct(int id, String name, String description, Double price, Integer quantity) {
        Product existingProduct = products.get(id);
        if (existingProduct == null || id <= 0) {
            return null;
        }

        Product updatedProduct = new Product(
            id, 
            name != null ? name : existingProduct.getName(),
            description != null ? description : existingProduct.getDescription(),
            price != null ? price : existingProduct.getPrice(),
            quantity != null ? quantity : existingProduct.getQuantity()
        );
        products.put(id, updatedProduct);
        return updatedProduct;
    }

    public boolean deleteProduct(int id, String name, Double price, Integer quantity) {
        Product product = products.get(id);
        if (product != null) {
            // boolean matches = (name == null || name.equals(product.getName())) &&
            //                   (price == null || price.equals(product.getPrice())) &&
            //                   (quantity == null || quantity.equals(product.getQuantity()));
            boolean matches = (name.equals(product.getName())) &&
                              (price.equals(product.getPrice())) &&
                              (quantity.equals(product.getQuantity()));
            // return true;
            if (matches) {
                products.remove(id);
                return true;
            }
        }
        return false;
    }

    public Product getProduct(int id) {
        return products.get(id);
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
            String name = requestData.get("productname");
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
            String name = requestData.get("productname");
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