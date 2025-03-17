import requests
import json
import os
import sys

# Function to load config.json manually without additional imports
def load_config():
    try:
        with open("config.json", "r", encoding="utf-8") as file:
            content = file.read().replace("\n", "").replace("\t", "").replace(" ", "")  # Remove unnecessary spaces
            config = json.loads(content)  # Parse JSON
            return config
    except Exception as e:
        print(f"Error loading config.json: {e}")
        return {}

# Load configuration data
config_data = load_config()

def construct_service_url(service_name, endpoint):
    service = config_data.get(service_name, {})
    ip = service.get("ip", "127.0.0.1")
    port = service.get("port", "14000")
    return f"http://{ip}:{port}/{endpoint}"

# Dynamically construct base URLs
ORDER_SERVICE_URL = construct_service_url("OrderService", "order")
USER_SERVICE_URL = construct_service_url("UserService", "user")
PRODUCT_SERVICE_URL = construct_service_url("ProductService", "product")

def parse_workload(file_path):
    """
    Parses the workload file and executes HTTP requests based on the commands.
    """
    with open(file_path, "r") as file:
        for line in file:
            line = line.strip()
            if not line:
                continue
            
            parts = line.split()

            # if len(parts) == 1 and parts[0].lower() == "shutdown":
            #     handle_shutdown()
            #     continue
                
            # if len(parts) == 1 and parts[0].lower() == "restart":
            #     handle_restart()
            #     continue

            service = parts[0].upper()
            action = parts[1].lower()

            if service == "USER":
                handle_user_action(action, parts[2:])
            elif service == "PRODUCT":
                handle_product_action(action, parts[2:])
            elif service == "ORDER":
                handle_order_action(action, parts[2:])
            else:
                print(f"Unknown service type: {service}")

def handle_shutdown():
    print("Sending shutdown command to all services...")
    try:
        requests.post(ORDER_SERVICE_URL + "/shutdown", json={"command": "shutdown"})
        print("OrderService shutdown command sent")
    except Exception as e:
        print(f"Error shutting down OrderService: {e}")
        
    try:
        requests.post(USER_SERVICE_URL + "/shutdown", json={"command": "shutdown"})
        print("UserService shutdown command sent")
    except Exception as e:
        print(f"Error shutting down UserService: {e}")
        
    try:
        requests.post(PRODUCT_SERVICE_URL + "/shutdown", json={"command": "shutdown"})
        print("ProductService shutdown command sent")
    except Exception as e:
        print(f"Error shutting down ProductService: {e}")

    
    print("All shutdown commands sent")

def handle_restart():
    print("Sending restart command to OrderService...")
    try:
        response = requests.post(ORDER_SERVICE_URL + "/restart", json={"command": "restart"})
        print(f"OrderService restart response: {response.status_code}")
    except Exception as e:
        print(f"Error restarting OrderService: {e}")

def handle_user_action(action, params):
    if action == "create":
        data = {
            "command": "create",
            "id": int(params[0]) if len(params) > 0 else "",
            "username": params[1] if len(params) > 1 else "",
            "email": params[2] if len(params) > 2 else "",
            "password": params[3] if len(params) > 3 else ""
        }
        response = requests.post(USER_SERVICE_URL, json=data)
        print_response("USER CREATE", response)
    elif action == "update":
        id = int(params[0])
        fields = {f.split(":")[0]: f.split(":")[1] for f in params[1:]}
        fields["command"] = "update"
        fields["id"] = id
        response = requests.post(USER_SERVICE_URL, json=fields)
        print_response("USER UPDATE", response)
    elif action == "delete":
        data = {
            "command": "delete",
            "id": int(params[0]) if len(params) > 0 else "",
            "username": params[1] if len(params) > 1 else "",
            "email": params[2] if len(params) > 2 else "",
            "password": params[3] if len(params) > 3 else ""
        }
        # data = {"command": "delete", "id": int(params[0]), "username": params[1], "email": params[2], "password": params[3]}
        response = requests.post(USER_SERVICE_URL, json=data)
        print_response("USER DELETE", response)
    elif action == "get":
        id = int(params[0])
        response = requests.get(f"{USER_SERVICE_URL}/{id}")
        print_response("USER GET", response)
    else:
        print(f"Unknown USER action: {action}")

def handle_product_action(action, params):
    if action == "create":
        ls = params[1].split("-")
        # data = {
        #     "command": "create",
        #     "id": int(params[0]),
        #     "productname": ls[0],
        #     "description": ls[1],
        #     "price": float(params[2]),
        #     "quantity": int(params[3])
        # }
        data = {
            "command": "create",
            "id": int(params[0]) if len(params) > 0 else "",
            "name": params[1] if len(params) > 1 else "",
            "description": params[2] if len(params) > 2 else "",
            "price": float(params[3]) if len(params) > 3 else "",
            "quantity": int(params[4]) if len(params) > 4 else ""
        }
        response = requests.post(PRODUCT_SERVICE_URL, json=data)
        print_response("PRODUCT CREATE", response)
    elif action == "update":
        id = int(params[0])
        fields = {f.split(":")[0]: f.split(":")[1] for f in params[1:]}
        fields["command"] = "update"
        fields["id"] = id
        response = requests.post(PRODUCT_SERVICE_URL, json=fields)
        print_response("PRODUCT UPDATE", response)
    elif action == "delete":
        # data = {"command": "delete", "id": int(params[0]), "productname": params[1], "price": float(params[2]), "quantity": int(params[3])}
        data = {
            "command": "delete",
            "id": int(params[0]) if len(params) > 0 else "",
            "name": params[1] if len(params) > 1 else "",
            "price": float(params[2]) if len(params) > 2 else "",
            "quantity": int(params[3]) if len(params) > 3 else ""
        }
        response = requests.post(PRODUCT_SERVICE_URL, json=data)
        print_response("PRODUCT DELETE", response)
    elif action == "info":
        id = int(params[0])
        response = requests.get(f"{PRODUCT_SERVICE_URL}/{id}")
        print_response("PRODUCT INFO", response)
    else:
        print(f"Unknown PRODUCT action: {action}")

def handle_order_action(action, params):
    if action == "place":
        data = {
            "command": "place order",
            "product_id": int(params[0]) if len(params) > 0 else -1,
            "user_id": int(params[1]) if len(params) > 1 else -1,
            "quantity": int(params[2]) if len(params) > 2 else -1
        }
        response = requests.post(ORDER_SERVICE_URL, json=data)
        print_response("ORDER PLACE", response)
    else:
        print(f"Unknown ORDER action: {action}")

def print_response(action, response):
    print(f"{action} RESPONSE: {response.status_code}")
    if response.status_code == 200:
        if (response.headers.get('Content-Type') == 'application/json' and response.text):
            print(response.json())
        else:
            print(response.text)
    else:
        print(response.text)

if __name__ == "__main__":
    # parse_workload("workload.txt")
    workload_file = sys.argv[1]
    parse_workload(workload_file)
