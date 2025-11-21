import asyncio
from telethon import TelegramClient
from telethon.tl.types import UserStatusOnline, UserStatusRecently, UserStatusOffline
import os
from dotenv import load_dotenv
import pathlib
import sys

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')

async def check_user_online(target_username):
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
        
        # Check if user is online by checking status
        # The entity already contains status information
        if hasattr(entity, 'status'):
            status = entity.status
            if isinstance(status, UserStatusOnline):
                print("true")
                return True
            elif isinstance(status, UserStatusRecently):
                # Recently online (within last few minutes) - consider as online
                print("true")
                return True
            else:
                # Offline or other status
                print("false")
                return False
        else:
            # Try to get full entity for more detailed status
            try:
                full = await client.get_entity(entity)
                if hasattr(full, 'status'):
                    status = full.status
                    if isinstance(status, UserStatusOnline):
                        print("true")
                        return True
                    elif isinstance(status, UserStatusRecently):
                        print("true")
                        return True
            except Exception:
                pass
            # No status available - assume offline
            print("false")
            return False
    except Exception as e:
        print(f"Error checking online status: {e}")
        print("false")
        return False
    finally:
        await client.disconnect()

if __name__ == '__main__':
    if len(sys.argv) >= 2:
        target = sys.argv[1]
        result = asyncio.run(check_user_online(target))
        sys.exit(0 if result else 1)
    else:
        print("Usage: python online_checker.py <username>")
        sys.exit(1)

