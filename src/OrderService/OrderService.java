import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderService {
    private static int PORT;
    private static String IP;
    static String PRODUCT_SERVER_PORT;
    static String PRODUCT_SERVER_IP;
    static String USER_SERVER_PORT;
    static String USER_SERVER_IP;

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
        // loadConfig(args[0]);
        loadConfig(null);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/product", new ProductHandler());
        server.createContext("/user", new UserHandler());
        server.createContext("/order", new OrderHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("OrderService started on port " + PORT);
    }
}
class UserHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        // Forward the request to the User Server
        String userServerUrl = String.format("http://%s:%s%s", 
            OrderService.USER_SERVER_IP, 
            OrderService.USER_SERVER_PORT, 
            exchange.getRequestURI().getPath());

        // System.out.println(userServerUrl);
        
        if ("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
            try {
                forwardRequest(exchange, userServerUrl);
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
class Order {
    private static int nextId = 1;
    final int id;
    final int productId;
    final int userId;
    final int quantity;
    final String status;

   public Order(int productId, int userId, int quantity, String status) {
       this.id = nextId++;
       this.productId = productId;
       this.userId = userId;
       this.quantity = quantity;
       this.status = status;
   }

   public String toJson() {
       return String.format(
           "{\"id\":%d,\"product_id\":%d,\"user_id\":%d,\"quantity\":%d,\"status\":\"%s\"}",
           id, productId, userId, quantity, status
       );
   }
}

class OrderManager {
    private final List<Order> orders = Collections.synchronizedList(new ArrayList<>());

    public void addOrder(Order order) {
        orders.add(order);
    }

    public List<Order> getOrders() {
        return orders;
    }
}


class OrderHandler implements HttpHandler {
    private final Map<Integer, Product> productCache = new ConcurrentHashMap<>();
    private final Set<Integer> validUserIds = Collections.synchronizedSet(new HashSet<>());

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

    private void handleOrderCommand(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        
        try {
            // Clean up the input string and parse JSON
            Map<String, Object> commandMap = parseJson(requestBody);
            // System.out.println(commandMap);
            // Extract and validate command
            String command = (String) commandMap.get("command");
            if (!"place".equals(command)) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Extract and validate numerical values
            int productId = ((Number) commandMap.get("product_id")).intValue();
            int userId = ((Number) commandMap.get("user_id")).intValue();
            int quantity = ((Number) commandMap.get("quantity")).intValue();
    
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
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Verify user exists
            if (!checkUserExists(userId)) {
                sendErrorResponse(exchange, "Invalid Request");
                return;
            }
    
            // Check if enough stock is available
            if (product.getQuantity() < quantity) {
                sendErrorResponse(exchange, "Exceeded quantity limit");
                return;
            }
    
            // Create Order object
            Order order = new Order(productId, userId, quantity, "Success");
    
            // Update product quantity
            int newQuantity = product.getQuantity() - quantity;
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
        "\"status\":\"Success\"," +
        "\"id\":%d," +
        "\"product_id\":%d," +
        "\"user_id\":%d," +
        "\"quantity\":%d" +
        "}",
        order.id,
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
    private Product getProduct(int productId) throws IOException, URISyntaxException {
        // Check cache first
        Product cachedProduct = productCache.get(productId);
        if (cachedProduct != null) {
            return cachedProduct;
        }

        // If not in cache, fetch from product service
        String productUrl = String.format("http://%s:%s/product/%d",
            OrderService.PRODUCT_SERVER_IP,
            OrderService.PRODUCT_SERVER_PORT,
            productId);

        String response = sendGetRequest(productUrl);
        if (response != null) {
            Product product = Product.fromJson(response);
            productCache.put(productId, product);
            return product;
        }
        return null;
    }

    private boolean checkUserExists(int userId) throws IOException, URISyntaxException {
        if (validUserIds.contains(userId)) {
            return true;
        }

        String userUrl = String.format("http://%s:%s/user/%d",
            OrderService.USER_SERVER_IP,
            OrderService.USER_SERVER_PORT,
            userId);

        String response = sendGetRequest(userUrl);
        if (response != null) {
            validUserIds.add(userId);
            return true;
        }
        return false;
    }

    private String sendGetRequest(String urlString) throws IOException, URISyntaxException {
        URI uri = new URI(urlString);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        try {
            // if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                // }
            }
        } finally {
            conn.disconnect();
        }
        // return null;
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