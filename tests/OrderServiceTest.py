import requests
import json

# User service URL
USER_SERVICE_URL = "http://localhost:14000/order"

# JSON request payloads (tests to send)
request_payloads = {
    "order_create_200": {
        "command": "place order",
        "product_id": 2011,
        "user_id": 1009,
        "quantity": 20
    },
    "order_create_400_missing_user_id": {
        "command": "place order",
        "product_id": 2012,
        "quantity": 1
    },
    "order_create_404_invalid_user_id": {
        "command": "place order",
        "product_id": 2013,
        "user_id": 10000,
        "quantity": 2
    },
    "order_create_404_invalid_product_id": {
        "command": "place order",
        "product_id": 10000,
        "user_id": 1009,
        "quantity": 3
    },
    "order_create_400_invalid_quantity": {
        "command": "place order",
        "product_id": 2014,
        "user_id": 1009,
        "quantity": -1
    },
    "order_create_400,409_exceeded_quantity": {
        "command": "place order",
        "product_id": 2011,
        "user_id": 1009,
        "quantity": 9
    }
}

# Expected responses
expected_responses = {
    "order_create_200": {
        "product_id": 2011,
        "user_id": 1009,
        "quantity": 20,
        "status": "Success"
    },
    "order_create_400_missing_user_id": {
        "status": "Invalid Request"
    },
    "order_create_404_invalid_user_id": {
        "status": "Invalid Request"
    },
    "order_create_404_invalid_product_id": {
        "status": "Invalid Request"
    },
    "order_create_400_invalid_quantity": {
        "status": "Invalid Request"
    },
    "order_create_400,409_exceeded_quantity": {
        "status": "Exceeded quantity limit"
    }
}

# Run tests
for test_name, payload in request_payloads.items():
    print(f"\nRunning test: {test_name}")

    # Determine request type
    if "command" in payload:  # POST request (create, update, delete)
        response = requests.post(USER_SERVICE_URL, json=payload, headers={"Content-Type": "application/json"})
    else:  # GET request
        response = requests.get(f"{USER_SERVICE_URL}/{payload['id']}", headers={"Content-Type": "application/json"})

    # Get response data
    response_json = {}
    try:
        response_json = response.json()
    except json.JSONDecodeError:
        print("Invalid JSON response")

    # Compare with expected response
    expected = expected_responses.get(test_name, {})
    if response_json == expected:
        print(f"✅ {test_name}: PASSED")
        print(response_json)
    else:
        print(f"❌ {test_name}: FAILED")
        print(f"Expected: {expected}")
        print(f"Got: {response_json}")
        print(f"STATUS_CODE: {response.status_code}")

    # Check response headers
    if response.headers.get("Content-Type") != "application/json":
        print(f"⚠️ Warning: Unexpected content-type in {test_name}")

print("\nAll tests completed.")
