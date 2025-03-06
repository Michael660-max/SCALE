import requests
import json

# User service URL
USER_SERVICE_URL = "http://localhost:14001/user"

# JSON request payloads (tests to send)
request_payloads = {
    "user_create_200_1000": {
        "command": "create",
        "id": 1000,
        "username": "tester1000",
        "email": "test1000@test.com",
        "password": "password1000"
    },
    "user_create_200_1001": {
        "command": "create",
        "id": 1001,
        "username": "tester1001",
        "email": "test1001@test.com",
        "password": "password1001"
    },

    "user_create_200_1002": {
        "command": "create",
        "id": 1002,
        "username": "tester1002",
        "email": "test1002@test.com",
        "password": "password1002"
    },

    "user_create_200_1003": {
        "command": "create",
        "id": 1003,
        "username": "tester1003",
        "email": "test1003@test.com",
        "password": "password1003"
    },

    "user_create_200_1004": {
        "command": "create",
        "id": 1004,
        "username": "tester1004",
        "email": "test1004@test.com",
        "password": "password1004"
    },

    "user_create_200_1005": {
        "command": "create",
        "id": 1005,
        "username": "tester1005",
        "email": "test1005@test.com",
        "password": "password1005"
    },

    "user_create_200_1006": {
        "command": "create",
        "id": 1006,
        "username": "tester1006",
        "email": "test1006@test.com",
        "password": "password1006"
    },
    "user_create_200_1007": {
        "command": "create",
        "id": 1007,
        "username": "tester1007",
        "email": "test1007@test.com",
        "password": "password1007"
    },
    "user_create_200_1008": {
        "command": "create",
        "id": 1008,
        "username": "tester1008",
        "email": "test1008@test.com",
        "password": "password1008"
    },
    "user_create_200_1009": {
        "command": "create",
        "id": 1009,
        "username": "tester1009",
        "email": "test1009@test.com",
        "password": "password1009"
    },
    "user_create_400_empty_username_1010": {
        "command": "create",
        "id": 1010,
        "username": "",
        "email": "test1010@test.com",
        "password": "password1010"
    },
    "user_create_400_missing_password_1011": {
        "command": "create",
        "id": 1011,
        "username": "tester1011",
        "email": "test1011@test.com"
    },
    "user_create_400_invalid_email_type_1012": {
        "command": "create",
        "id": 1012,
        "username": "tester1012",
        "email": 1012,
        "password": "password1012"
    },
    "user_create_409_duplicate_userID_1000": {
        "command": "create",
        "id": 1000,
        "username": "tester1000-duplicate",
        "email": "test1000-duplicate@test.com",
        "password": "password1000-duplicate"
    },
    "user_update_200_email_password_1001": {
        "command": "update",
        "id": 1001,
        "username": "tester1001-update",
        "email": "testupdate1001@test.com",
        "password": "password1001-update"
    },
    "user_update_200_only_required_fields_1002": {
        "command": "update",
        "id": 1002
    },
    "user_update_400_missing_id": {
        "command": "update",
        "username": "tester-update",
        "email": "testupdate@test.com"
    },
    "user_update_400_invalid_email_type_1003": {
        "command": "update",
        "id": 1003,
        "email": 1003
    },
    "user_update_400_empty_email_password_1004": {
        "command": "update",
        "id": 1004,
        "username": "tester1004-update",
        "email": "",
        "password": ""
    },    
    "user_update_404_invalid_id": {
        "command": "update",
        "id": 10001,
        "username": "tester10001-update",
        "email": "testupdate10001@test.com",
        "password": "password10001"
    },
    "user_delete_200_1005": {
        "command": "delete",
        "id": 1005,
        "username": "tester1005",
        "email": "test1005@test.com",
        "password": "password1005"
    },
    "user_delete_400_missing_email_field_1006": {
        "command": "delete",
        "id": 1006,
        "username": "tester1006",
        "password": "password1006"
    },
    "user_delete_404_invalid_id": {
        "command": "delete",
        "id": 10002,
        "username": "tester10002",
        "email": "test10002@test.com",
        "password": "password10002"
    },
    "user_delete_404,401_fields_dont_match_1007": {
        "command": "delete",
        "id": 1007,
        "username": "tester1007",
        "email": "test1007@test.com",
        "password": "WrongPassword"
    },
    "user_get_200_1000": {
        "id": 1000
    },
    "user_get_200_1001": {
        "id": 1001
    },
    "user_get_400_invalid_type": {
        "id": "abcd"
    },
    "user_get_404,400_invalid_id": {
        "id": 10000
    }
}

# Expected responses
expected_responses = {
    "user_create_200_1000": {
        "id": 1000,
        "username": "tester1000",
        "email": "test1000@test.com",
        "password": "FBEC9326096D714E466E80A9F6CD917C72D976C4014588EA679769008360DDF0"
    },
    "user_create_200_1001": {
        "id": 1001,
        "username": "tester1001",
        "email": "test1001@test.com",
        "password": "551ACDAF1F6CEB312384854BBFFDA99324CD0927DD611A8868F7CA928689447E"
    },
    "user_create_200_1002": {
        "id": 1002,
        "username": "tester1002",
        "email": "test1002@test.com",
        "password": "21C2B2791F532C49862E751D37F1FA15939CA50953F454F82D55A13FE6604C73"
    },
    "user_create_200_1003": {
        "id": 1003,
        "username": "tester1003",
        "email": "test1003@test.com",
        "password": "8AD54FC27D1FDCCEF25323CCFBD6E8B05BFAB215F7A48F691137A7BF26A36D6F"
    },
    "user_create_200_1004": {
        "id": 1004,
        "username": "tester1004",
        "email": "test1004@test.com",
        "password": "9EE2D09628AAFD102E502079BEAFAC9FBF139A110574FD19AAB359FF9F5E816E"
    },
    "user_create_200_1005": {
        "id": 1005,
        "username": "tester1005",
        "email": "test1005@test.com",
        "password": "8079A043AA757F81F77A99447D96919F0ACB2B8ABCC666BA82218F194A587C9F"
    },
    "user_create_200_1006": {
        "id": 1006,
        "username": "tester1006",
        "email": "test1006@test.com",
        "password": "F250998AB071245263BEFC8DDE67EC16565E03B9C33C080F0CDFD004822E5F13"
    },
    "user_create_200_1007": {
        "id": 1007,
        "username": "tester1007",
        "email": "test1007@test.com",
        "password": "AA2114A93B38168CA4D15F1CDDD9E8139D696A0F492743078486D86B72CAEC42"
    },
    "user_create_200_1008": {
        "id": 1008,
        "username": "tester1008",
        "email": "test1008@test.com",
        "password": "FF0F47B3023390612B94AFBDEE6CBD8A7E4306E972F4A0F512FC48546117C644"
    },
    "user_create_200_1009": {
        "id": 1009,
        "username": "tester1009",
        "email": "test1009@test.com",
        "password": "7D5FE1E549DD8EAD62C6E99F5EA73BDB55A9FD317DB5BCF6E03CD014C0742B40"
    },
    "user_create_400_empty_username_1010": {},
    "user_create_400_missing_password_1011": {},
    "user_create_400_invalid_email_type_1012": {},
    "user_create_409_duplicate_userID_1000": {},
    "user_update_200_email_password_1001": {
        "id": 1001,
        "username": "tester1001-update",
        "email": "testupdate1001@test.com",
        "password": "8A72452C6B555347259530BB601A23B69BE85ECBDF5D3539FF51FD8A8E00C8BD"
    },
    "user_update_200_only_required_fields_1002": {
        "id": 1002,
        "username": "tester1002",
        "email": "test1002@test.com",
        "password": "21C2B2791F532C49862E751D37F1FA15939CA50953F454F82D55A13FE6604C73"
    },
    "user_update_400_missing_id": {},
    "user_update_400_invalid_email_type_1003": {},
    "user_update_400_empty_email_password_1004": {},    
    "user_update_404_invalid_id": {},
    "user_delete_200_1005": {},
    "user_delete_400_missing_email_field_1006": {},
    "user_delete_404_invalid_id": {},
    "user_delete_404,401_fields_dont_match_1007": {},
    "user_get_200_1000": {
        "id": 1000,
        "username": "tester1000",
        "email": "test1000@test.com",
        "password": "FBEC9326096D714E466E80A9F6CD917C72D976C4014588EA679769008360DDF0"
    },
    "user_get_200_1001": {
        "id": 1001,
        "username": "tester1001-update",
        "email": "testupdate1001@test.com",
        "password": "8A72452C6B555347259530BB601A23B69BE85ECBDF5D3539FF51FD8A8E00C8BD"
    },
    "user_get_400_invalid_type": {},
    "user_get_404,400_invalid_id": {}
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
        response_json["password"] = response_json["password"].upper()
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
