import requests
import time
from threading import Thread
import sys

# Function to send POST requests
def send_post_request(url, data):
    try:
        response = requests.post(url, json=data, headers={"Content-Type": "application/json"})
    except Exception as e:
        print("Error sending POST request:", e)

# Function to send GET requests
def send_get_request(url, count):
    try:
        response = requests.get(f"{url}/{count}", headers={"Content-Type": "application/json"})
        
        if response.status_code != 200:
            print("GET Request Status Code:", response.status_code)
            # Exit the thread if the GET request fails
    except Exception as e:
        print("Error sending GET request:", e)

# Function to send requests at a specified rate
def send_requests(url, requests_per_second, thread_id):
    count = 0
    start_time = time.time()
    while True:
        count += 1
        # Send POST request with slightly different data each time
        # product
        post_data = {
            "command": "create",
            "id": count,
            "name": "product2000",
            "description": f"This is product {count}",
            "price": 1,
            "quantity": 90
        }

        # user
        # post_data = {"command": "create",
        # "id": count,
        # "username": "user1000",
        # "email": "user1000@user.com",
        # "password": "password1000"}


        # Uncomment to send POST requests
        send_post_request(url, post_data)

        # Send GET request
        # send_get_request(url, count)

        # Calculate time taken for this iteration
        iteration_time = time.time() - start_time

        # Calculate time to sleep before next iteration to achieve desired requests per second
        time_to_sleep = max(0, 1 / requests_per_second - iteration_time)
        time.sleep(time_to_sleep)

        # Calculate requests per second
        elapsed_time = time.time() - start_time
        if count % requests_per_second == 0:
            actual_requests_per_second = requests_per_second / elapsed_time
            print(f"Thread-{thread_id} Requests per second: {actual_requests_per_second:.2f}, sleep time: {time_to_sleep:.4f}")
            start_time = time.time()

# Main function
if __name__ == "__main__":
    url = "http://localhost:15000/product"  # Replace with your URL
    requests_per_second = 1000  # Replace with desired requests per second
    num_threads = 5  # Set number of threads (increase this value to add more threads)

    # Create and start multiple threads
    threads = []
    for i in range(num_threads):
        t = Thread(target=send_requests, args=(url, requests_per_second, i + 1))
        t.daemon = True  # Daemonize thread to exit when the main program exits
        t.start()
        threads.append(t)

    # Keep the main thread alive to allow the child threads to run
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("Script terminated by user.")
