import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import pathlib
import sys

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')
password = os.getenv('TELEGRAM_PASSWORD')  # optional 2FA password

async def verify_otp(code: str):
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    client = TelegramClient(session_path, api_id, api_hash)
    try:
        await client.connect()
        if await client.is_user_authorized():
            print("Already authorized")
            return True
        # Send code request and then sign in with the provided code in the SAME process
        await client.send_code_request(phone)
        await client.sign_in(phone=phone, code=code)
        # If 2FA password is required
        if not await client.is_user_authorized() and password:
            await client.sign_in(password=password)
        print("OTP verified and session saved")
        return await client.is_user_authorized()
    except Exception as e:
        print(f"Error verifying OTP: {e}")
        return False
    finally:
        await client.disconnect()

if __name__ == '__main__':
    if len(sys.argv) >= 2:
        ok = asyncio.run(verify_otp(sys.argv[1]))
        sys.exit(0 if ok else 1)
    else:
        print("Usage: python otp_verifier.py <code>")
        sys.exit(1)


