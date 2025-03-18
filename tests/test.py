import aiohttp
import asyncio
import time

URL = "http://127.0.0.1:14003/user"
REQUESTS_PER_SECOND = 3000  # Set desired RPS
DURATION = 10  # Test duration in seconds

async def send_post(session, product_id):
    post_data = {
        "command": "create",
        "id": product_id,
        "username": "username",
        "email": "123@gnail.com",
        "password": "password",
        "time": str(time.time()),
    }
    async with session.post(URL, json=post_data) as response:
        return response.status

async def send_get(session, product_id):
    async with session.get(f"{URL}/{product_id}") as response:
        return response.status

async def stress_test(rps, duration):
    start_time = time.time()
    total_requests = 0

    async with aiohttp.ClientSession() as session:
        while time.time() - start_time < duration:
            tasks = []
            for i in range(rps):  # Send RPS requests in parallel
                product_id = 2093 + i
                tasks.append(send_post(session, product_id))
                tasks.append(send_get(session, product_id))

            responses = await asyncio.gather(*tasks)
            total_requests += len(responses)

            # Sleep for 1 second to match the RPS rate
            await asyncio.sleep(1)

    elapsed_time = time.time() - start_time
    actual_rps = total_requests / elapsed_time
    print(f"\n🔥 Actual RPS: {actual_rps:.2f} | Total Requests: {total_requests} | Elapsed Time: {elapsed_time:.2f}s\n")

asyncio.run(stress_test(REQUESTS_PER_SECOND, DURATION))