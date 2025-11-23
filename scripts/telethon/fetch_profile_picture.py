#!/usr/bin/env python3
"""
Fetch profile picture from Telegram for a target user.
Usage: python fetch_profile_picture.py <platform_account_id> <target_username> <target_id> <user_id>
"""

import asyncio
import os
import sys
import json
import psycopg2
import urllib.parse
from telethon import TelegramClient
from telethon.tl.types import UserProfilePhoto
import re

# Database connection helper
def get_connection():
    db_url = os.getenv('DATABASE_URL', 'postgresql://postgres:Ezekiel(23)@localhost:5432/aria')
    return psycopg2.connect(db_url)

def get_platform_account(account_id):
    """Get platform account details from database."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT id, platform, username, number, api_id, api_hash, session_path, account_name
                FROM platform_accounts
                WHERE id = %s
            """, (account_id,))
            row = cur.fetchone()
            if not row:
                return None
            return {
                'id': row[0],
                'platform': row[1],
                'username': row[2],
                'number': row[3],
                'api_id': row[4],
                'api_hash': row[5],
                'session_path': row[6],
                'account_name': row[7]
            }

def get_user_id_by_platform_account(platform_account_id):
    """Get user_id from platform_account_id."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT user_id FROM platform_accounts WHERE id = %s", (platform_account_id,))
            row = cur.fetchone()
            if not row:
                raise RuntimeError(f"platform_account id {platform_account_id} not found")
            return row[0]

def create_safe_filename(name):
    """Create a safe filename by removing/replacing invalid characters."""
    if not name:
        return "unknown"
    # Remove or replace invalid characters for filesystem
    safe = re.sub(r'[<>:"/\\|?*]', '_', name)
    safe = safe.strip('. ')
    if not safe:
        return "unknown"
    return safe

async def fetch_profile_picture(platform_account_id, target_username, target_id, user_id):
    """Fetch profile picture from Telegram and save it."""
    try:
        # Get platform account
        acc = get_platform_account(platform_account_id)
        if not acc:
            print(json.dumps({"success": False, "error": "Platform account not found"}))
            sys.exit(1)
        
        if acc['platform'].upper() != 'TELEGRAM':
            print(json.dumps({"success": False, "error": "Only Telegram is supported"}))
            sys.exit(1)
        
        # Connect to Telegram
        session_path = acc['session_path'] or f"sessions/{acc['username'] or acc['number']}.session"
        client = TelegramClient(session_path, acc['api_id'], acc['api_hash'])
        
        try:
            await client.connect()
            if not await client.is_user_authorized():
                print(json.dumps({"success": False, "error": "Not authorized"}))
                sys.exit(1)
        except Exception as e:
            print(json.dumps({"success": False, "error": f"Failed to connect: {str(e)}"}))
            sys.exit(1)
        
        # Normalize target username
        uname = target_username.strip().lstrip('@').lower()
        if uname and not uname.replace('+', '').isdigit():
            uname = '@' + uname
        else:
            uname = target_username  # Use as-is if it's a phone number
        
        # Get entity
        try:
            entity = await client.get_entity(uname)
        except Exception as e:
            print(json.dumps({"success": False, "error": f"Could not find user {target_username}: {str(e)}"}))
            await client.disconnect()
            sys.exit(1)
        
        # Check if user has profile photo
        if not hasattr(entity, 'photo') or entity.photo is None:
            print(json.dumps({"success": False, "error": "User has no profile picture"}))
            await client.disconnect()
            sys.exit(1)
        
        # Get chat name for folder structure
        safe_chat_name = re.sub(r'[<>:"/\\|?*]', '', (entity.first_name or '') + ((' ' + entity.last_name) if hasattr(entity, 'last_name') and entity.last_name else '')).strip()
        if not safe_chat_name:
            safe_chat_name = uname.lstrip('@')
        
        # Get user directory
        username = acc['username'] or 'unknown'
        USER_DIR = f"user_{username}"
        BASE_MEDIA_ROOT = os.getenv('BASE_MEDIA_ROOT', 'media/telegramConnector')
        
        # Create directory structure
        chat_dir = os.path.join(BASE_MEDIA_ROOT, USER_DIR, f"{safe_chat_name}_chat")
        os.makedirs(chat_dir, exist_ok=True)
        
        # Use fixed filename: profile_picture.jpg
        fileName = "profile_picture.jpg"
        filepath = os.path.join(chat_dir, fileName)
        
        # Check if profile picture already exists
        if os.path.exists(filepath) and os.path.getsize(filepath) > 0:
            # Profile picture already exists, just update database URL if needed
            relative_path = f"media/telegramConnector/user_{username}/{safe_chat_name}_chat/{fileName}"
            encoded_path = urllib.parse.quote(relative_path, safe='')
            profile_picture_url = f"/api/targets/{target_id}/profile-picture?path={encoded_path}"
            
            # Check if database already has this URL
            with get_connection() as conn:
                with conn.cursor() as cur:
                    cur.execute("""
                        SELECT profile_picture_url FROM target_users 
                        WHERE id = %s AND user_id = %s
                    """, (target_id, user_id))
                    row = cur.fetchone()
                    if row and row[0] == profile_picture_url:
                        # Already in database, no need to update
                        print(json.dumps({
                            "success": True,
                            "profilePictureUrl": profile_picture_url,
                            "filePath": filepath,
                            "message": "Profile picture already exists"
                        }))
                        await client.disconnect()
                        sys.exit(0)
                    else:
                        # File exists but URL not in database, update it
                        cur.execute("""
                            UPDATE target_users 
                            SET profile_picture_url = %s 
                            WHERE id = %s AND user_id = %s
                        """, (profile_picture_url, target_id, user_id))
                        conn.commit()
                        print(json.dumps({
                            "success": True,
                            "profilePictureUrl": profile_picture_url,
                            "filePath": filepath,
                            "message": "Profile picture URL updated in database"
                        }))
                        await client.disconnect()
                        sys.exit(0)
        else:
            # Profile picture doesn't exist, download it
            try:
                await client.download_profile_photo(entity, file=filepath)
                
                # Verify file was downloaded
                if not os.path.exists(filepath) or os.path.getsize(filepath) == 0:
                    print(json.dumps({"success": False, "error": "Failed to download profile picture"}))
                    await client.disconnect()
                    sys.exit(1)
            except Exception as e:
                print(json.dumps({"success": False, "error": f"Failed to download profile picture: {str(e)}"}))
                await client.disconnect()
                sys.exit(1)
        
        # Save to database
        # Use URL encoding for the path
        relative_path = f"media/telegramConnector/user_{username}/{safe_chat_name}_chat/{fileName}"
        encoded_path = urllib.parse.quote(relative_path, safe='')
        profile_picture_url = f"/api/targets/{target_id}/profile-picture?path={encoded_path}"
            
            with get_connection() as conn:
                with conn.cursor() as cur:
                    cur.execute("""
                        UPDATE target_users 
                        SET profile_picture_url = %s 
                        WHERE id = %s AND user_id = %s
                    """, (profile_picture_url, target_id, user_id))
                    conn.commit()
            
            print(json.dumps({
                "success": True,
                "profilePictureUrl": profile_picture_url,
                "filePath": filepath
            }))
            
        except Exception as e:
            print(json.dumps({"success": False, "error": f"Failed to download profile picture: {str(e)}"}))
            await client.disconnect()
            sys.exit(1)
        
        await client.disconnect()
        
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}))
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) != 5:
        print(json.dumps({"success": False, "error": "Usage: python fetch_profile_picture.py <platform_account_id> <target_username> <target_id> <user_id>"}))
        sys.exit(1)
    
    platform_account_id = int(sys.argv[1])
    target_username = sys.argv[2]
    target_id = int(sys.argv[3])
    user_id = int(sys.argv[4])
    
    asyncio.run(fetch_profile_picture(platform_account_id, target_username, target_id, user_id))

