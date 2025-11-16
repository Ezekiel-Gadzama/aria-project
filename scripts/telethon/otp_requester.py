import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import pathlib

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')

async def request_otp():
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    client = TelegramClient(session_path, api_id, api_hash)
    try:
        await client.connect()
        if await client.is_user_authorized():
            print("Already authorized, no OTP needed")
            return True
        # Sending code to phone; do not sign in here
        await client.send_code_request(phone)
        print("OTP sent")
        return True
    except Exception as e:
        print(f"Error requesting OTP: {e}")
        return False
    finally:
        await client.disconnect()

if __name__ == '__main__':
    ok = asyncio.run(request_otp())
    import sys
    sys.exit(0 if ok else 1)


