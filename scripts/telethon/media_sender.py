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

async def send_media(target_username, file_path, caption=None):
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    client = TelegramClient(session_path, api_id, api_hash)
    
    # Check if session file exists - if it does, connect without starting (won't trigger OTP)
    session_file = pathlib.Path(session_path + '.session')
    if session_file.exists():
        try:
            await client.connect()
            # If already authorized, we're done - don't call start() which would trigger OTP
            if not await client.is_user_authorized():
                await client.start(phone=phone)
        except Exception:
            # If connect fails, fall back to start
            await client.start(phone=phone)
    else:
        # No session file exists - need to start (will trigger OTP)
        await client.start(phone=phone)
    try:
        # Normalize username: ensure leading '@' for public usernames
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname
        
        entity = await client.get_entity(uname)
        # Send media with optional caption
        sent_msg = await client.send_file(entity, file_path, caption=caption if caption else None)
        
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
            print(f"Media sent to {uname}")
        return True
    except Exception as e:
        print(f"Error sending media: {e}")
        return False
    finally:
        await client.disconnect()

if __name__ == '__main__':
    if len(sys.argv) >= 3:
        target = sys.argv[1]
        path = sys.argv[2]
        caption = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] else None
        if not os.path.exists(path):
            print("File not found:", path)
            sys.exit(1)
        asyncio.run(send_media(target, path, caption))
    else:
        print("Usage: python media_sender.py <username> <file_path> [caption]")
        sys.exit(1)


