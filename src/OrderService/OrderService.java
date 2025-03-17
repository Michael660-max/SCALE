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
    public static Map<Integer, Map<String, Object>> userCache = new ConcurrentHashMap<>();


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
    // private UserManager userManager = new UserManager();


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

        private void handleCreateUser(HttpExchange exchange, Map<String, String> requestData) throws IOException {
            try {
                int id = Integer.parseInt(requestData.get("id"));
                String username = requestData.get("username");
                String email = requestData.get("email");
                String password = requestData.get("password");

                if (username == null || email == null  || password == null) {
                    sendErrorResponse(exchange, 400, "");
                    return;
                }

                // Create the user (User is defined in another module)
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
                
                // Convert the returned user to a generic map
                Map<String, Object> userData = convertUserToMap(createdUser);
                OrderService.userCache.put((Integer) userData.get("id"), userData);

                sendResponse(exchange, 200, createdUser.toResponseString(null));
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
                
                // Convert to a generic map and update the cache
                Map<String, Object> userData = convertUserToMap(updatedUser);
                OrderService.userCache.put((Integer) userData.get("id"), userData);

                sendResponse(exchange, 200, updatedUser.toResponseString(null));
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "");
            }
        }
    
    private void handleDeleteUser(com.sun.net.httpserver.HttpExchange exchange, Map<String, String> requestData) throws IOException {
            try {
                int id = Integer.parseInt(requestData.get("id"));
                String username = requestData.get("username");
                String email = requestData.get("email");
                String password = requestData.get("password");

                if (username == null || email == null || password == null) {
                    sendErrorResponse(exchange, 400, "Missing data");
                    return;
                }

                if (!userManager.deleteUser(id, username, email, password)) {
                    sendErrorResponse(exchange, 404, "User not found or deletion failed");
                    return;
                }
                // Remove from cache as well
                userCache.remove(id);
                sendResponse(exchange, 200, "{\"status\":\"User deleted\"}");
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid numeric value");
            }
        }

        private void handleAddPurchase(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Expected format: /user/{id}/purchase
            String[] segments = path.split("/");
            if (segments.length < 3) {
                sendErrorResponse(exchange, 400, "Invalid URL");
                return;
            }
            int userId;
            try {
                userId = Integer.parseInt(segments[2]);
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid user id");
                return;
            }
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> requestData = parseJsonToMap(requestBody);
            int productId, quantity;
            try {
                productId = Integer.parseInt(requestData.get("product_id"));
                quantity = Integer.parseInt(requestData.get("quantity"));
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid product id or quantity");
                return;
            }
            // Retrieve the user from UserManager
            User user = userManager.getUser(userId);
            if (user == null) {
                sendErrorResponse(exchange, 404, "User not found");
                return;
            }
            // Update purchase history using the User method
            user.addPurchase(productId, quantity);
            // Update the cache with the new user state
            Map<String, Object> userData = convertUserToMap(user);
            userCache.put(userId, userData);

            sendResponse(exchange, 200, "{\"status\":\"Purchase added\"}");
        }

        // Implements the GET endpoint: /user/{id}
        private void handleGet(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            if (segments.length != 3) {
                sendErrorResponse(exchange, 400, "Invalid URL");
                return;
            }
            int userId;
            try {
                userId = Integer.parseInt(segments[2]);
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid user id");
                return;
            }
            // Try to retrieve user from the cache first
            Map<String, Object> cachedUser = userCache.get(userId);
            if (cachedUser != null) {
                sendResponse(exchange, 200, mapToJson(cachedUser));
                return;
            }
            // Fallback: retrieve from UserManager and update cache
            User user = userManager.getUser(userId);
            if (user != null) {
                Map<String, Object> userData = convertUserToMap(user);
                userCache.put(userId, userData);
                sendResponse(exchange, 200, user.toResponseString(null));
            } else {
                sendErrorResponse(exchange, 404, "User not found");
            }
        }

        // Implements the GET endpoint for purchased products: /user/purchased/{id}
        private void handleGetPurchased(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            // Expected format: /user/purchased/{id}
            String[] segments = path.split("/");
            if (segments.length < 4) {
                sendErrorResponse(exchange, 400, "Invalid URL");
                return;
            }
            int userId;
            try {
                userId = Integer.parseInt(segments[3]);
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid user id");
                return;
            }
            // Check cache first
            Map<String, Object> cachedUser = userCache.get(userId);
            if (cachedUser != null && cachedUser.containsKey("purchasedProducts")) {
                Object purchased = cachedUser.get("purchasedProducts");
                // Assume purchased is a Map; convert it to JSON.
                if (purchased instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> purchasedMap = (Map<String, Object>) ((Map<?, ?>) purchased);
                    sendResponse(exchange, 200, mapToJson(purchasedMap));
                    return;
                }
            }
            // Otherwise, retrieve user from UserManager
            User user = userManager.getUser(userId);
            if (user == null) {
                sendErrorResponse(exchange, 404, "User not found");
                return;
            }
            // Update the cache with fresh data
            Map<String, Object> userData = convertUserToMap(user);
            userCache.put(userId, userData);
            // Extract purchasedProducts from the user object
            Map<Integer, Integer> purchasedProducts = user.getPurchasedProducts();
            // Convert to a simple JSON string
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
        }
    
        private Map<String, Object> convertUserToMap(Object user) {
            Map<String, Object> map = new HashMap<>();
            try {
                Method getId = user.getClass().getMethod("getId");
                Method getUsername = user.getClass().getMethod("getUsername");
                Method getEmail = user.getClass().getMethod("getEmail");
                Method getPassword = user.getClass().getMethod("getPassword");
                Method getPurchased = null;
                try {
                    getPurchased = user.getClass().getMethod("getPurchasedProducts");
                } catch (NoSuchMethodException nsme) {
                    // If not available, ignore.
                }
                Object id = getId.invoke(user);
                Object username = getUsername.invoke(user);
                Object email = getEmail.invoke(user);
                Object password = getPassword.invoke(user);

                map.put("id", id);
                map.put("username", username);
                map.put("email", email);
                map.put("password", password);
                if (getPurchased != null) {
                    Object purchased = getPurchased.invoke(user);
                    map.put("purchasedProducts", purchased);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return map;
        }

        // Converts a generic Map<String, Object> into a JSON string.
        private String mapToJson(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                sb.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> subMap = (Map<String, Object>) value;
                    sb.append(mapToJson(subMap));
                } else {
                    sb.append(value);
                }
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        private String getRequestBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
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

        private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private void sendErrorResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
            sendResponse(exchange, statusCode, errorMessage);
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
            if (!"place order".equals(command)) {
                sendErrorResponse(exchange, "Invalid Command");
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