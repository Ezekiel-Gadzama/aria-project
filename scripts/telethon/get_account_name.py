import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import pathlib
import sys
import json

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')
password = os.getenv('TELEGRAM_PASSWORD')
session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')

async def get_account_name():
    """Get the account display name from Telegram session"""
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
        client = TelegramClient(session_path, api_id, api_hash)
        
        await client.connect()
        if not await client.is_user_authorized():
            print(json.dumps({"error": "Not authorized"}))
            await client.disconnect()
            return
        
        me = await client.get_me()
        # Get first_name and last_name, combine them
        first_name = getattr(me, 'first_name', '') or ''
        last_name = getattr(me, 'last_name', '') or ''
        account_name = (first_name + ' ' + last_name).strip()
        
        # If no name, fall back to username
        if not account_name:
            account_name = getattr(me, 'username', '') or ''
            if account_name:
                account_name = '@' + account_name
        
        print(json.dumps({"account_name": account_name or "Unknown"}))
        await client.disconnect()
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)

if __name__ == '__main__':
    asyncio.run(get_account_name())

