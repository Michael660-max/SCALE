import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProductService {
    // private static final int PORT = 16001;
    private static int PORT;
    private static String IP;

    /**
     *
     * @param path
     */
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
            Map<String, String> userService = config.get("ProductService");

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
     *
     * @param json
     * @return
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
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        loadConfig(null);
        // loadConfig(args[0]);
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

    /**
     *
     * @param id
     * @param name
     * @param description
     * @param price
     * @param quantity
     */
    public Product(int id, String name, String description, double price, int quantity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(double price) { this.price = price; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String toJson() {
        return String.format(
                "{\"id\":%d,\"name\":\"%s\",\"description\":\"%s\",\"price\":%.2f,\"quantity\":%d}",
                id, name, description, price, quantity);
    }
}

class ProductManager {
    private final Map<Integer, Product> products = new ConcurrentHashMap<>();
    //private final AtomicInteger productIdCounter = new AtomicInteger(1);

    /**
     *
     * @param id
     * @param name
     * @param description
     * @param price
     * @param quantity
     * @return
     */
    public Product createProduct(int id, String name, String description, Double price, Integer quantity) {
        Product product = new Product(id, name, description, price, quantity);
        products.put(id, product);
        return product;
    }

    /**
     *
     * @param id
     * @param name
     * @param description
     * @param price
     * @param quantity
     * @return
     */
    public Product updateProduct(int id, String name, String description, Double price, Integer quantity) {
        Product product = products.get(id);
        if (product != null) {
            if (name != null) {
                product.setName(name);
            }
            if (description != null) {
                product.setDescription(description);
            }
            if (price != null) {
                product.setPrice(price);
            }
            if (quantity != null) {
                product.setQuantity(quantity);
            }
        }
        return product;
    }

    /**
     *
     * @param id
     * @param name
     * @param description
     * @param price
     * @param quantity
     * @return
     */
    public boolean deleteProduct(int id, String name, Double price, Integer quantity) {
        Product product = products.get(id);
        if (product != null) {
            boolean matches = (name == null || name.equals(product.getName())) &&
                              (price == null || price.equals(product.getPrice())) &&
                              (quantity == null || quantity.equals(product.getQuantity()));
            // boolean matches = (name.equals(product.getName())) &&
            //                   ( price.equals(product.getPrice())) &&
            //                   ( quantity.equals(product.getQuantity()));
            if (matches) {
                products.remove(id);
                //System.out.println("Product removed");
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param id
     * @return
     */
    public Product getProduct(int id) {
        return products.get(id);
    }
}

class ProductHandler implements HttpHandler {
    private static final ProductManager productManager = new ProductManager();

    /**
     *
     * @param exchange the exchange containing the request from the
     *                 client and used to send the response
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method)) {
            handlePost(exchange);
        } else if ("GET".equalsIgnoreCase(method)) {
            handleGet(exchange);
        } else {
            sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    /**
     *
     * @param exchange
     * @throws IOException
     */
    private void handlePost(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> data = parseJsonToMap(requestBody);
        // System.out.println("HAHAHAH");

        String command = data.get("command");
        int id = data.containsKey("id") ? Integer.parseInt(data.get("id")) : -1;
        String name = data.get("productname");
        String description = data.getOrDefault("description", "");

        // Handle price and quantity safely
        Double price = data.containsKey("price") && !data.get("price").isEmpty() ? Double.parseDouble(data.get("price")) : null;
        Integer quantity = data.containsKey("quantity") && !data.get("quantity").isEmpty() ? Integer.parseInt(data.get("quantity")) : null;

        switch (command.toLowerCase()) {
            case "create":
                // Ensure all required fields are provided for product creation
                try {
                    // Ensure 'id' is a valid number
                    id = Integer.parseInt(data.get("id"));

                    // Check if 'id' is negative
                    if (id < 0) {
                        sendResponse(exchange, 400, "");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "");
                    return;
                }

                // Ensure all required fields are provided for product creation
                if (name == null || price == null || quantity == null) {
                    sendResponse(exchange, 400, "");
                    return;
                }
                // Ensure price and quantity are non-negative
                if (price < 0 || quantity < 0) {
                    sendResponse(exchange, 400, "");
                    return;
                }

                // Check if the product ID already exists
                if (productManager.getProduct(id) != null) {
                    sendResponse(exchange, 409, "");
                    return;
                }
                Product createdProduct = productManager.createProduct(id, name, description, price, quantity);
                sendResponse(exchange, 200, "" + createdProduct.toJson());
                break;
            case "update":
                // Ensure 'id' and 'name' are present for updates, but allow nulls for price/quantity
                try {
                    // Ensure 'id' is a valid number
                    id = Integer.parseInt(data.get("id"));

                    // Check if 'id' is negative
                    if (id < 0) {
                        sendResponse(exchange, 400, "");
                        return;
                    }
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "");
                    return;
                }
                // Validate price and quantity if provided
                if (price != null && price < 0) {
                    sendResponse(exchange, 400, "");
                    return;
                }
                if (quantity != null && quantity < 0) {
                    sendResponse(exchange, 400, "");
                    return;
                }
                Product updatedProduct = productManager.updateProduct(id, name, description, price, quantity);
                if (updatedProduct != null) {
                    sendResponse(exchange, 200, "" + updatedProduct.toJson());
                } else {
                    sendResponse(exchange, 404, "");
                }
                break;
            case "delete":
                boolean deleted = productManager.deleteProduct(id, name, price, quantity);
                //System.out.println("Product removed ");
                if (deleted) {
                    sendResponse(exchange, 200, "");
                } else {
                    sendResponse(exchange, 404, "");
                }
                break;
            default:
                sendResponse(exchange, 400, "");
        }
    }

    /**
     *
     * @param exchange
     * @throws IOException
     */
    private void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] segments = path.split("/");

        if (segments.length == 3) {
            try {
                int id = Integer.parseInt(segments[2]);
                Product product = productManager.getProduct(id);
                if (product != null) {
                    sendResponse(exchange, 200, product.toJson());
                } else {
                    sendResponse(exchange, 404, "");
                }
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "");
            }
        } else {
            sendResponse(exchange, 400, "");
        }
    }

    /**
     *
     * @param json
     * @return
     */
    private Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        json = json.replaceAll("[{}\"]", "");
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            map.put(keyValue[0].trim(), keyValue[1].trim());
        }
        return map;
    }

    /**
     *
     * @param exchange
     * @param statusCode
     * @param response
     * @throws IOException
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
}