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

async def send_media(target_username, file_path, caption=None, reply_to_message_id=None):
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    client = TelegramClient(session_path, api_id, api_hash)
    
    # Check if session file exists - if it does, connect without starting (won't trigger OTP)
    session_file = pathlib.Path(session_path + '.session')
    if session_file.exists():
        connect_attempts = 10
        for i in range(connect_attempts):
            try:
                await client.connect()
                # If already authorized, we're done - don't call start() which would trigger OTP
                if await client.is_user_authorized():
                    break
                # Session exists but not authorized - this shouldn't happen if session is valid
                # Don't call start() as it will trigger OTP - just raise an error
                await client.disconnect()
                print(f"Error: Session file exists but user is not authorized. Please re-register the platform.")
                return False
            except Exception as e:
                if "database is locked" in str(e).lower() and i < connect_attempts - 1:
                    await asyncio.sleep(0.05)
                    continue
                # If connect fails repeatedly and it's the last attempt, don't try start() - just fail
                if i == connect_attempts - 1:
                    print(f"Error: Failed to connect to session after {connect_attempts} attempts: {str(e)}")
                    return False
                continue
    else:
        # No session file exists - cannot send without a valid session
        print(f"Error: No session file found. Please register the platform first.")
        return False
    try:
        # Normalize username: ensure leading '@' for public usernames
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname
        
        entity = await client.get_entity(uname)
        
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
        
        # Send media with optional caption and reply
        sent_msg = await client.send_file(entity, file_path, caption=caption if caption else None, reply_to=reply_to)
        
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
        reply_to = int(sys.argv[4]) if len(sys.argv) > 4 and sys.argv[4] else None
        if not os.path.exists(path):
            print("File not found:", path)
            sys.exit(1)
        asyncio.run(send_media(target, path, caption, reply_to))
    else:
        print("Usage: python media_sender.py <username> <file_path> [caption] [reply_to_message_id]")
        sys.exit(1)


