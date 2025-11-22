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

async def get_entity_id(target_username):
    """Get the Telegram entity ID (peer ID) for a username"""
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
        client = TelegramClient(session_path, api_id, api_hash)
        
        await client.connect()
        if not await client.is_user_authorized():
            # Session exists but not authorized - this shouldn't happen if session is valid
            # Don't call start() as it will trigger OTP - just return an error
            await client.disconnect()
            print(json.dumps({"success": False, "error": "Session file exists but user is not authorized. Please re-register the platform."}))
            return None
        
        # Normalize username
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname
        
        entity = await client.get_entity(uname)
        entity_id = None
        entity_name = None
        
        if entity and hasattr(entity, 'id'):
            entity_id = entity.id
        if entity and hasattr(entity, 'first_name'):
            entity_name = entity.first_name
            if hasattr(entity, 'last_name') and entity.last_name:
                entity_name = (entity_name + ' ' + entity.last_name).strip()
        if not entity_name and entity and hasattr(entity, 'username'):
            entity_name = '@' + entity.username
        
        result = {
            "success": True,
            "entityId": entity_id,
            "entityName": entity_name or uname
        }
        print(json.dumps(result))
        await client.disconnect()
        return entity_id
    except Exception as e:
        result = {
            "success": False,
            "error": str(e)
        }
        print(json.dumps(result))
        await client.disconnect()
        return None

if __name__ == '__main__':
    if len(sys.argv) >= 2:
        target = sys.argv[1]
        result = asyncio.run(get_entity_id(target))
        sys.exit(0 if result else 1)
    else:
        print(json.dumps({"success": False, "error": "Usage: python get_entity_id.py <username>"}))
        sys.exit(1)

