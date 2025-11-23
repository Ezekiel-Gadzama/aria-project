#!/usr/bin/env python3
"""
Pin or unpin a message in Telegram using Telethon.
Usage: python3 message_pinner.py <target_username> <message_id> <pin>
where pin is "true" or "false"
"""

import asyncio
import json
import os
import sys
from pathlib import Path
from telethon import TelegramClient
from telethon.errors import SessionPasswordNeededError

# Priority lock to pause ingestion
lock_path = Path(os.getenv("TELETHON_LOCK_PATH", "/app/telethon_send.lock"))

async def pin_message(target_username: str, message_id: int, pin: bool):
    # Create priority lock so ingestion yields (shared absolute path)
    try:
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        lock_path.touch()
    except Exception as e:
        print(f"Warning: Could not create lock file: {e}", file=sys.stderr)

    api_id = os.getenv("TELEGRAM_API_ID")
    api_hash = os.getenv("TELEGRAM_API_HASH")
    phone = os.getenv("TELEGRAM_PHONE")
    username = os.getenv("TELEGRAM_USERNAME", "")
    session_path = os.getenv("TELETHON_SESSION_PATH", "Session/telethon/user.session")

    if not api_id or not api_hash or not phone:
        print(json.dumps({"success": False, "error": "Missing Telegram credentials"}))
        return False

    try:
        client = TelegramClient(session_path, int(api_id), api_hash)
        await client.connect()

        if not await client.is_user_authorized():
            print(json.dumps({"success": False, "error": "Not authorized. Please authenticate first."}))
            return False

        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname

        entity = await client.get_entity(uname)

        for i in range(10):
            try:
                if pin:
                    await client.pin_message(entity, message_id)
                else:
                    await client.unpin_message(entity, message_id)
                break
            except Exception as e:
                error_msg = str(e).lower()
                if "database is locked" in error_msg and i < 9:
                    await asyncio.sleep(0.05)
                    continue
                raise

        print(json.dumps({"success": True}))
        return True
    except Exception as e:
        error_msg = str(e)
        print(json.dumps({"success": False, "error": error_msg}))
        return False
    finally:
        await client.disconnect()
        try:
            if lock_path.exists():
                lock_path.unlink()
        except Exception:
            pass


if __name__ == '__main__':
    if len(sys.argv) < 4:
        print(json.dumps({"success": False, "error": "Usage: message_pinner.py <target_username> <message_id> <pin>"}))
        sys.exit(1)

    target_username = sys.argv[1]
    try:
        message_id = int(sys.argv[2])
    except ValueError:
        print(json.dumps({"success": False, "error": "Invalid message_id"}))
        sys.exit(1)
    
    pin_str = sys.argv[3].lower()
    pin = pin_str == "true" or pin_str == "1"

    result = asyncio.run(pin_message(target_username, message_id, pin))
    sys.exit(0 if result else 1)

