import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import asyncio as _asyncio
import pathlib
import sys

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')


async def delete_message(target_username: str, message_id: int):
    # Priority lock to pause ingestion
    lock_env = os.getenv("TELETHON_LOCK_PATH", "/app/telethon_send.lock")
    lock_path = pathlib.Path(lock_env)
    try:
        lock_path.write_text("deleting", encoding="utf-8")
    except Exception:
        pass

    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    client = TelegramClient(session_path, api_id, api_hash)

    # Check if session file exists - if it does, connect without starting (won't trigger OTP)
    session_file = pathlib.Path(session_path + '.session')
    if session_file.exists():
        # Session file exists - try to connect first (won't trigger OTP if authorized)
        for i in range(10):
            try:
                await client.connect()
                # If already authorized, we're done - don't call start() which would trigger OTP
                if await client.is_user_authorized():
                    break
                # Session exists but not authorized - need to start
                await client.start(phone=phone)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < 9:
                    await _asyncio.sleep(0.05)
                    continue
                # If connect fails, fall back to start
                await client.start(phone=phone)
                break
    else:
        # No session file exists - need to start (will trigger OTP)
        for i in range(10):
            try:
                await client.start(phone=phone)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < 9:
                    await _asyncio.sleep(0.05)
                    continue
                raise

    try:
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname

        entity = await client.get_entity(uname)

        for i in range(10):
            try:
                await client.delete_messages(entity, [message_id], revoke=True)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < 9:
                    await _asyncio.sleep(0.05)
                    continue
                raise

        print(f"Deleted message {message_id} in {uname}")
        return True
    except Exception as e:
        print(f"Error deleting message: {e}")
        return False
    finally:
        await client.disconnect()
        try:
            if lock_path.exists():
                lock_path.unlink()
        except Exception:
            pass


if __name__ == '__main__':
    if len(sys.argv) == 3:
        target = sys.argv[1]
        msg_id = int(sys.argv[2])
        ok = asyncio.run(delete_message(target, msg_id))
        sys.exit(0 if ok else 1)
    else:
        print("Usage: python message_deleter.py <username> <message_id>")
        sys.exit(1)

