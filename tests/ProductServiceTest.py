import requests
import json

# User service URL
USER_SERVICE_URL = "http://localhost:15000/product"

# JSON request payloads (tests to send)
request_payloads = {

    "product_create_200_2000": {
        "command": "create",
        "id": 2000,
        "name": "product2000",
        "description": "This is product 2000",
        "price": 162.58,
        "quantity": 90
    },
    "product_create_200_2001": {
        "command": "create",
        "id": 2001,
        "name": "product2001",
        "description": "This is product 2001",
        "price": 202.66,
        "quantity": 60
    },
    "product_create_200_2002": {
        "command": "create",
        "id": 2002,
        "name": "product2002",
        "description": "This is product 2002",
        "price": 98.47,
        "quantity": 96
    },
    "product_create_200_2003": {
        "command": "create",
        "id": 2003,
        "name": "product2003",
        "description": "This is product 2003",
        "price": 197.51,
        "quantity": 3
    },
    "product_create_200_2004": {
        "command": "create",
        "id": 2004,
        "name": "product2004",
        "description": "This is product 2004",
        "price": 98.61,
        "quantity": 26
    },
    "product_create_200_2005": {
        "command": "create",
        "id": 2005,
        "name": "product2005",
        "description": "This is product 2005",
        "price": 152.70,
        "quantity": 34
    },
    "product_create_200_2006": {
        "command": "create",
        "id": 2006,
        "name": "product2006",
        "description": "This is product 2006",
        "price": 269.23,
        "quantity": 89
    },
    "product_create_200_2007": {
        "command": "create",
        "id": 2007,
        "name": "product2007",
        "description": "This is product 2007",
        "price": 79.18,
        "quantity": 89
    },
    "product_create_200_2008": {
        "command": "create",
        "id": 2008,
        "name": "product2008",
        "description": "This is product 2008",
        "price": 77.68,
        "quantity": 79
    },
    "product_create_200_2009": {
        "command": "create",
        "id": 2009,
        "name": "product2009",
        "description": "This is product 2009",
        "price": 283.03,
        "quantity": 71
    },
    "product_create_200_2010": {
        "command": "create",
        "id": 2010,
        "name": "product2010",
        "description": "This is product 2010",
        "price": 37.32,
        "quantity": 75
    },
    "product_create_200_2011": {
        "command": "create",
        "id": 2011,
        "name": "product2011",
        "description": "This is product 2011",
        "price": 8.50,
        "quantity": 28
    },
    "product_create_200_2012": {
        "command": "create",
        "id": 2012,
        "name": "product2012",
        "description": "This is product 2012",
        "price": 173.31,
        "quantity": 74
    },
    "product_create_200_2013": {
        "command": "create",
        "id": 2013,
        "name": "product2013",
        "description": "This is product 2013",
        "price": 139.85,
        "quantity": 97
    },
    "product_create_200_2014": {
        "command": "create",
        "id": 2014,
        "name": "product2014",
        "description": "This is product 2014",
        "price": 157.00,
        "quantity": 35
    },
    "product_create_200_2015": {
        "command": "create",
        "id": 2015,
        "name": "product2015",
        "description": "This is product 2015",
        "price": 175.73,
        "quantity": 35
    },
    "product_create_200_2016": {
        "command": "create",
        "id": 2016,
        "name": "product2016",
        "description": "This is product 2016",
        "price": 52.38,
        "quantity": 19
    },
    "product_create_200_2017": {
        "command": "create",
        "id": 2017,
        "name": "product2017",
        "description": "This is product 2017",
        "price": 221.85,
        "quantity": 82
    },
    "product_create_200_2018": {
        "command": "create",
        "id": 2018,
        "name": "product2018",
        "description": "This is product 2018",
        "price": 111.86,
        "quantity": 14
    },
    "product_create_200_2019": {
        "command": "create",
        "id": 2019,
        "name": "product2019",
        "description": "This is product 2019",
        "price": 209.20,
        "quantity": 51
    },
    "product_create_400_empty_name": {
        "command": "create",
        "id": 2024,
        "name": "",
        "description": "This is product 2024",
        "price": 2.3,
        "quantity": 4
    },
    "product_create_400_missing_description": {
        "command": "create",
        "id": 2025,
        "name": "product2025",
        "price": 2.3,
        "quantity": 4
    },
    "product_create_400_invalid_quantity_type": {
        "command": "create",
        "id": 2026,
        "name": "product2026",
        "description": "This is product 2026",
        "price": 5.0,
        "quantity": 0.5597
    },
    "product_create_400_negative_quantity": {
        "command": "create",
        "id": 2027,
        "name": "product2027",
        "description": "This is product 2027",
        "price": 5.0,
        "quantity": -4
    },
    "product_create_400_negative_price": {
        "command": "create",
        "id": 2028,
        "name": "product2028",
        "description": "This is product 2028",
        "price": -5.0,
        "quantity": 4
    },
    "product_create_409_duplicate_productID": {
        "command": "create",
        "id": 2000,
        "name": "product2000-duplicate",
        "description": "This is a duplicate product 2000",
        "price": 5.0,
        "quantity": 9
    },
    "product_update_200": {
        "command": "update",
        "id": 2001,
        "name": "product2001-update",
        "description": "This is product 2001 version 2",
        "price": 199.99,
        "quantity": 100
    },
    "product_update_400_missing_id": {
        "command": "update",
        "name": "product 2002 invalid update"
    },
    "product_update_400_empty_description": {
        "command": "update",
        "id": 2003,
        "description": ""
    },
    "product_update_400_negative_price": {
        "command": "update",
        "id": 2004,
        "price": -6.0
    },
    "product_update_400_negative_quantity": {
        "command": "update",
        "id": 2005,
        "quantity": -6
    },
    "product_update_400_invalid_quantity_type": {
        "command": "update",
        "id": 2006,
        "quantity": 0.6
    },
    "product_update_404_invalid_id": {
        "command": "update",
        "id": 10000,
        "description": "this product doesn't exist ;)"
    },
    "product_delete_200": {
        "command": "delete",
        "id": 2007,
        "name": "product2007",
        "price": 79.18,
        "quantity": 89
    },
    "product_delete_400_missing_name_field": {
        "command": "delete",
        "id": 2008,
        "price": 77.68,
        "quantity": 79
    },
    "product_delete_404_invalid_id": {
        "command": "delete",
        "id": 10000,
        "name": "product10000",
        "price": 2.3,
        "quantity": 4
    },
    "product_delete_404_401_fields_dont_match": {
        "command": "delete",
        "id": 2009,
        "name": "product2009",
        "price": 280.0,
        "quantity": 71
    },
    "product_get_200_product2001": {
        "id": 2001
    },
    "product_get_200_product2010": {
        "id": 2010
    },
    "product_get_400_invalid_type": {
        "id": "abcd"
    },
    "product_get_404_invalid_id_product2007": {
        "id": 2007
    },
    "product_get_404_invalid_id": {
        "id": 10000
    }
}

# Expected responses
expected_responses = {
    "product_create_200_2000": {
        "id": 2000,
        "name": "product2000",
        "description": "This is product 2000",
        "price": 162.58,
        "quantity": 90
    },
    "product_create_200_2001": {
        "id": 2001,
        "name": "product2001",
        "description": "This is product 2001",
        "price": 202.66,
        "quantity": 60
    },
    "product_create_200_2002": {
        "id": 2002,
        "name": "product2002",
        "description": "This is product 2002",
        "price": 98.47,
        "quantity": 96
    },
    "product_create_200_2003": {
        "id": 2003,
        "name": "product2003",
        "description": "This is product 2003",
        "price": 197.51,
        "quantity": 3
    },
    "product_create_200_2004": {
        "id": 2004,
        "name": "product2004",
        "description": "This is product 2004",
        "price": 98.61,
        "quantity": 26
    },
    "product_create_200_2005": {
        "id": 2005,
        "name": "product2005",
        "description": "This is product 2005",
        "price": 152.70,
        "quantity": 34
    },
    "product_create_200_2006": {
        "id": 2006,
        "name": "product2006",
        "description": "This is product 2006",
        "price": 269.23,
        "quantity": 89
    },
    "product_create_200_2007": {
        "id": 2007,
        "name": "product2007",
        "description": "This is product 2007",
        "price": 79.18,
        "quantity": 89
    },
    "product_create_200_2008": {
        "id": 2008,
        "name": "product2008",
        "description": "This is product 2008",
        "price": 77.68,
        "quantity": 79
    },
    "product_create_200_2009": {
        "id": 2009,
        "name": "product2009",
        "description": "This is product 2009",
        "price": 283.03,
        "quantity": 71
    },
    "product_create_200_2010": {
        "id": 2010,
        "name": "product2010",
        "description": "This is product 2010",
        "price": 37.32,
        "quantity": 75
    },
    "product_create_200_2011": {
        "id": 2011,
        "name": "product2011",
        "description": "This is product 2011",
        "price": 8.50,
        "quantity": 28
    },
    "product_create_200_2012": {
        "id": 2012,
        "name": "product2012",
        "description": "This is product 2012",
        "price": 173.31,
        "quantity": 74
    },
    "product_create_200_2013": {
        "id": 2013,
        "name": "product2013",
        "description": "This is product 2013",
        "price": 139.85,
        "quantity": 97
    },
    "product_create_200_2014": {
        "id": 2014,
        "name": "product2014",
        "description": "This is product 2014",
        "price": 157.00,
        "quantity": 35
    },
    "product_create_200_2015": {
        "id": 2015,
        "name": "product2015",
        "description": "This is product 2015",
        "price": 175.73,
        "quantity": 35
    },
    "product_create_200_2016": {
        "id": 2016,
        "name": "product2016",
        "description": "This is product 2016",
        "price": 52.38,
        "quantity": 19
    },
    "product_create_200_2017": {
        "id": 2017,
        "name": "product2017",
        "description": "This is product 2017",
        "price": 221.85,
        "quantity": 82
    },
    "product_create_200_2018": {
        "id": 2018,
        "name": "product2018",
        "description": "This is product 2018",
        "price": 111.86,
        "quantity": 14
    },
    "product_create_200_2019": {
        "id": 2019,
        "name": "product2019",
        "description": "This is product 2019",
        "price": 209.20,
        "quantity": 51
    },
    "product_create_200_2020": {
        "id": 2020,
        "name": "product2020",
        "description": "This is product 2020",
        "price": 37.31,
        "quantity": 82
    },
    "product_create_200_2021": {
        "id": 2021,
        "name": "product2021",
        "description": "This is product 2021",
        "price": 150.56,
        "quantity": 68
    },
    "product_create_200_2022": {
        "id": 2022,
        "name": "product2022",
        "description": "This is product 2022",
        "price": 22.43,
        "quantity": 54
    },
    "product_create_200_2023": {
        "id": 2023,
        "name": "product2023",
        "description": "This is product 2023",
        "price": 160.78,
        "quantity": 27
    },
    "product_create_400_empty_name": {},
    "product_create_400_missing_description": {},
    "product_create_400_invalid_quantity_type": {},
    "product_create_400_negative_quantity": {},
    "product_create_400_negative_price": {},
    "product_create_409_duplicate_productID": {},
    "product_update_200": {
        "id": 2001,
        "name": "product2001-update",
        "description": "This is product 2001 version 2",
        "price": 199.99,
        "quantity": 100
    },
    "product_update_400_missing_id": {},
    "product_update_400_empty_description": {},
    "product_update_400_negative_price": {},
    "product_update_400_negative_quantity": {},
    "product_update_400_invalid_quantity_type": {},
    "product_update_404_invalid_id": {},
    "product_delete_200": {},
    "product_delete_400_missing_name_field": {},
    "product_delete_404_invalid_id": {},
    "product_delete_404_401_fields_dont_match": {},
    "product_get_200_product2001": {
        "id": 2001,
        "name": "product2001-update",
        "description": "This is product 2001 version 2",
        "price": 199.99,
        "quantity": 100
    },
    "product_get_200_product2010": {
        "id": 2010,
        "name": "product2010",
        "description": "This is product 2010",
        "price": 37.32,
        "quantity": 75
    },
    "product_get_400_invalid_type": {},
    "product_get_404_invalid_id_product2007": {},
    "product_get_404_invalid_id": {}
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
