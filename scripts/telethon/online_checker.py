import asyncio
from telethon import TelegramClient
from telethon.tl.types import UserStatusOnline, UserStatusRecently, UserStatusOffline, UserStatusLastWeek, UserStatusLastMonth
import os
from dotenv import load_dotenv
import pathlib
import sys
import time
import json
from datetime import datetime, timezone

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')

def format_last_active(status):
    """Format last active time as human-readable string"""
    if isinstance(status, UserStatusOnline):
        return "online"
    elif isinstance(status, UserStatusRecently):
        return "recently"
    elif isinstance(status, UserStatusOffline):
        # Calculate time difference
        if hasattr(status, 'was_online') and status.was_online:
            was_online = status.was_online
            now = datetime.now(timezone.utc)
            if isinstance(was_online, datetime):
                diff = now - was_online.replace(tzinfo=timezone.utc) if was_online.tzinfo is None else now - was_online
                seconds = int(diff.total_seconds())
                
                if seconds < 60:
                    return f"{seconds} seconds ago"
                elif seconds < 3600:
                    minutes = seconds // 60
                    return f"{minutes} minute{'s' if minutes != 1 else ''} ago"
                elif seconds < 86400:
                    hours = seconds // 3600
                    return f"{hours} hour{'s' if hours != 1 else ''} ago"
                else:
                    days = seconds // 86400
                    return f"{days} day{'s' if days != 1 else ''} ago"
        return "offline"
    elif isinstance(status, UserStatusLastWeek):
        return "last week"
    elif isinstance(status, UserStatusLastMonth):
        return "last month"
    else:
        return "offline"

async def _retry_on_db_lock(coro, max_retries=10, delay=0.1):
    """Retry a coroutine on database lock errors with exponential backoff"""
    for attempt in range(max_retries):
        try:
            return await coro
        except Exception as e:
            if "database is locked" in str(e).lower() and attempt < max_retries - 1:
                wait_time = delay * (2 ** attempt)
                await asyncio.sleep(wait_time)
                continue
            raise

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
            # Use retry logic for database locks
            await _retry_on_db_lock(client.connect())
            # If already authorized, we're done - don't call start() which would trigger OTP
            if not await _retry_on_db_lock(client.is_user_authorized()):
                await _retry_on_db_lock(client.start(phone=phone))
        except Exception:
            # If connect fails, fall back to start
            try:
                await _retry_on_db_lock(client.start(phone=phone))
            except Exception as e:
                # If still fails, return error
                print(f"Error connecting: {e}")
                result = {
                    "online": False,
                    "lastActive": "error"
                }
                print(json.dumps(result))
                await client.disconnect()
                return False
    else:
        # No session file exists - need to start (will trigger OTP)
        try:
            await _retry_on_db_lock(client.start(phone=phone))
        except Exception as e:
            print(f"Error starting: {e}")
            result = {
                "online": False,
                "lastActive": "error"
            }
            print(json.dumps(result))
            await client.disconnect()
            return False
    try:
        # Normalize username: ensure leading '@' for public usernames
        uname = target_username.strip() if isinstance(target_username, str) else str(target_username)
        if uname and not uname.startswith('@') and not uname.replace('+', '').isdigit():
            uname = '@' + uname
        
        # Use retry logic for database locks
        entity = await _retry_on_db_lock(client.get_entity(uname))
        
        # Check if user is online by checking status
        # The entity already contains status information
        is_online = False
        last_active = "offline"
        
        if hasattr(entity, 'status'):
            status = entity.status
            if isinstance(status, UserStatusOnline):
                is_online = True
                last_active = "online"
            elif isinstance(status, UserStatusRecently):
                # Recently online (within last few minutes) - consider as online
                is_online = True
                last_active = format_last_active(status)
            else:
                last_active = format_last_active(status)
        else:
            # Try to get full entity for more detailed status
            try:
                full = await _retry_on_db_lock(client.get_entity(entity))
                if hasattr(full, 'status'):
                    status = full.status
                    if isinstance(status, UserStatusOnline):
                        is_online = True
                        last_active = "online"
                    elif isinstance(status, UserStatusRecently):
                        is_online = True
                        last_active = format_last_active(status)
                    else:
                        last_active = format_last_active(status)
            except Exception:
                pass
        
        # Return JSON with both online status and last active time
        result = {
            "online": is_online,
            "lastActive": last_active
        }
        print(json.dumps(result))
        return is_online
    except Exception as e:
        print(f"Error checking online status: {e}")
        result = {
            "online": False,
            "lastActive": "unknown"
        }
        print(json.dumps(result))
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

