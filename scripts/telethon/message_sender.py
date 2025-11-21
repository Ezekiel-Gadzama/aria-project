# message_sender.py
import asyncio
from telethon import TelegramClient
from telethon.tl.types import InputPeerUser
import os
from dotenv import load_dotenv
import pathlib
import os
import asyncio as _asyncio

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')


async def send_message(target_username, message, reply_to_message_id=None):
    # Create priority lock so ingestion yields (shared absolute path)
    lock_env = os.getenv("TELETHON_LOCK_PATH", "/app/telethon_send.lock")
    lock_path = pathlib.Path(lock_env)
    try:
        lock_path.write_text("sending", encoding="utf-8")
    except Exception:
        pass

    # Use per-account session path
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    # Ensure parent directory exists
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
                # Session exists but not authorized - need to start
                await client.start(phone=phone)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < connect_attempts - 1:
                    await _asyncio.sleep(0.05)  # faster backoff for priority send
                    continue
                # If connect fails, fall back to start
                await client.start(phone=phone)
                break
    else:
        # No session file exists - need to start (will trigger OTP)
        connect_attempts = 10
        for i in range(connect_attempts):
            try:
                await client.start(phone=phone)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < connect_attempts - 1:
                    await _asyncio.sleep(0.05)  # faster backoff for priority send
                    continue
                raise

    try:
        # Normalize username: ensure leading '@' for public usernames
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname

        # Resolve entity with retry if the session DB is locked at resolution time
        resolve_attempts = 10
        entity = None
        for i in range(resolve_attempts):
            try:
                entity = await client.get_entity(uname)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < resolve_attempts - 1:
                    await _asyncio.sleep(0.05)
                    continue
                raise

        # Get the message to reply to if reply_to_message_id is provided
        reply_to = None
        if reply_to_message_id is not None:
            try:
                # Get the message history to find the message we're replying to
                messages = await client.get_messages(entity, limit=100)
                for msg in messages:
                    if msg.id == reply_to_message_id:
                        reply_to = msg
                        break
            except Exception as e:
                # If we can't find the message, continue without reply
                print(f"Warning: Could not find message {reply_to_message_id} to reply to: {e}")

        # Send with retry if DB lock happens during send
        send_attempts = 10
        sent_msg = None
        for i in range(send_attempts):
            try:
                if reply_to:
                    sent_msg = await client.send_message(entity, message, reply_to=reply_to)
                else:
                    sent_msg = await client.send_message(entity, message)
                break
            except Exception as e:
                if "database is locked" in str(e).lower() and i < send_attempts - 1:
                    await _asyncio.sleep(0.05)
                    continue
                raise
        # Print message ID and peer ID for Java to parse and save to database
        if sent_msg and hasattr(sent_msg, 'id'):
            import json
            peer_id = None
            if entity and hasattr(entity, 'id'):
                peer_id = entity.id
            print(json.dumps({
                "success": True,
                "messageId": sent_msg.id,
                "target": uname,
                "peerId": peer_id
            }))
        else:
            print(f"Message sent to {uname}")
        return True
    except Exception as e:
        print(f"Error sending message: {e}")
        return False
    finally:
        await client.disconnect()
        # Release priority lock
        try:
            if lock_path.exists():
                lock_path.unlink()
        except Exception:
            pass


if __name__ == '__main__':
    # For testing
    import sys

    if len(sys.argv) >= 3:
        target = sys.argv[1]
        msg = sys.argv[2]
        reply_to = int(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[3] else None
        ok = asyncio.run(send_message(target, msg, reply_to))
        import sys as _sys
        _sys.exit(0 if ok else 1)
    else:
        print("Usage: python message_sender.py <username> <message> [reply_to_message_id]")
        import sys as _sys
        _sys.exit(1)
