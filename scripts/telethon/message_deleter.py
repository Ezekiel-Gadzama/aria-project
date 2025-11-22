import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import asyncio as _asyncio
import pathlib
import sys
import json

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')


async def delete_message(target_username: str, message_id: int, revoke: bool = True):
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
        connect_attempts = 10
        for i in range(connect_attempts):
            try:
                await client.connect()
                # If already authorized, we're done - don't call start() which would trigger OTP
                if await client.is_user_authorized():
                    break
                # Session exists but not authorized - this shouldn't happen if session is valid
                # Don't call start() as it will trigger OTP - just return an error
                await client.disconnect()
                print(json.dumps({"success": False, "revoked": False, "error": "Session file exists but user is not authorized. Please re-register the platform."}))
                return False
            except Exception as e:
                if "database is locked" in str(e).lower() and i < connect_attempts - 1:
                    await _asyncio.sleep(0.05)
                    continue
                # If connect fails repeatedly, the session might be corrupted
                if i == connect_attempts - 1:
                    # Last attempt failed
                    try:
                        await client.disconnect()
                    except:
                        pass
                    print(json.dumps({"success": False, "revoked": False, "error": f"Failed to connect to session after {connect_attempts} attempts: {str(e)}"}))
                    return False
    else:
        # No session file exists - cannot delete without a valid session
        print(json.dumps({"success": False, "revoked": False, "error": "No session file found. Please register the platform first."}))
        return False

    try:
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname

        entity = await client.get_entity(uname)

        for i in range(10):
            try:
                await client.delete_messages(entity, [message_id], revoke=revoke)
                break
            except Exception as e:
                error_msg = str(e).lower()
                if "database is locked" in error_msg and i < 9:
                    await _asyncio.sleep(0.05)
                    continue
                # Check if Telegram doesn't allow revoke (e.g., message too old)
                if revoke and ("revoke" in error_msg or "too old" in error_msg or "not allowed" in error_msg):
                    # Try without revoke (delete only for me)
                    try:
                        await client.delete_messages(entity, [message_id], revoke=False)
                        print(json.dumps({"success": True, "revoked": False, "message": "Message too old, deleted only for me"}))
                        return True
                    except Exception as e2:
                        print(json.dumps({"success": False, "error": str(e2)}))
                        return False
                raise

        print(json.dumps({"success": True, "revoked": revoke}))
        return True
    except Exception as e:
        error_msg = str(e)
        print(json.dumps({"success": False, "revoked": False, "error": error_msg}))
        return False
    finally:
        await client.disconnect()
        try:
            if lock_path.exists():
                lock_path.unlink()
        except Exception:
            pass


if __name__ == '__main__':
    import json
    if len(sys.argv) >= 3:
        target = sys.argv[1]
        msg_id = int(sys.argv[2])
        revoke = True
        if len(sys.argv) >= 4:
            revoke = sys.argv[3].lower() == "true"
        ok = asyncio.run(delete_message(target, msg_id, revoke))
        sys.exit(0 if ok else 1)
    else:
        print(json.dumps({"success": False, "error": "Usage: python message_deleter.py <username> <message_id> [revoke]"}))
        sys.exit(1)

