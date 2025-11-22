"""
Priority ingestion script for active target user conversation.
This script:
1. Ingests the last 80 messages (even if already ingested before)
2. Checks for deleted messages (messages in DB but not in Telegram)
3. Deletes messages from DB that no longer exist in Telegram
4. This runs every 5 seconds when a conversation is open
"""
import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import psycopg2
import json
import pathlib
import sys
import re
from typing import Optional

load_dotenv()

# Telegram credentials
api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')
platform_account_id = int(os.getenv("PLATFORM_ACCOUNT_ID", "0"))

# PostgreSQL connection
DB_CONFIG = {
    "dbname": os.getenv("DATABASE_NAME", "aria"),
    "user": os.getenv("DATABASE_USER", "postgres"),
    "password": os.getenv("DATABASE_PASSWORD", "Ezekiel(23)"),
    "host": os.getenv("DB_HOST", "aria-postgres"),
    "port": int(os.getenv("DB_PORT", "5432")),
}

def safe_print(text, max_length=100):
    if text is None:
        text = ""
    cleaned_text = re.sub(r'\\U000[^\\s]*', '', str(text))
    cleaned_text = re.sub(r'\s+', ' ', cleaned_text).strip()
    if len(cleaned_text) > max_length:
        cleaned_text = cleaned_text[:max_length] + "..."
    print(cleaned_text)

def get_connection():
    return psycopg2.connect(**DB_CONFIG)

def get_user_id_by_platform_account(account_id: int):
    sql = "SELECT user_id FROM platform_accounts WHERE id = %s"
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (account_id,))
        row = cur.fetchone()
        if not row:
            raise RuntimeError(f"platform_account id {account_id} not found")
        return row[0]

def save_dialog(user_id, dialog_id, name, type_):
    sql = """
        INSERT INTO dialogs (user_id, platform_account_id, dialog_id, name, type, message_count, media_count, last_synced)
        VALUES (%s, %s, %s, %s, %s, 0, 0, NOW())
        ON CONFLICT (user_id, platform_account_id, dialog_id) DO UPDATE
        SET name = EXCLUDED.name,
            type = EXCLUDED.type,
            last_synced = NOW()
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (user_id, platform_account_id, dialog_id, name, type_))
        result = cur.fetchone()
        return result[0] if result else None

def save_message(dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id=None):
    sql = """
        INSERT INTO messages (dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb, %s)
        ON CONFLICT (dialog_id, message_id) DO UPDATE SET
            timestamp = EXCLUDED.timestamp,
            has_media = EXCLUDED.has_media,
            raw_json = EXCLUDED.raw_json,
            reference_id = EXCLUDED.reference_id
            -- Note: We DON'T update text or sender here to preserve edits made through the app
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id))
        result = cur.fetchone()
        return result[0] if result else None

async def ingest_priority_target(target_username: str):
    """
    Priority ingestion: Always re-ingest last 80 messages and check for deletions.
    This runs every 5 seconds when a conversation is open.
    """
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    client = TelegramClient(session_path, api_id, api_hash)

    # Check if session file exists
    session_file = pathlib.Path(session_path + '.session')
    if not session_file.exists():
        safe_print("Error: No session file found. Please register the platform first.")
        return False

    # Retry connection with database lock handling
    connect_attempts = 10
    connected = False
    for i in range(connect_attempts):
        try:
            await client.connect()
            if await client.is_user_authorized():
                connected = True
                break
            else:
                await client.disconnect()
                safe_print("Error: Session file exists but user is not authorized. Please re-register the platform.")
                return False
        except Exception as e:
            error_msg = str(e).lower()
            if "database is locked" in error_msg and i < connect_attempts - 1:
                await asyncio.sleep(0.1 * (i + 1))
                continue
            if i == connect_attempts - 1:
                safe_print(f"Error: Failed to connect to session after {connect_attempts} attempts: {e}")
                return False
            continue

    if not connected:
        return False

    try:
        user_id = get_user_id_by_platform_account(platform_account_id)
        
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
            safe_print(f"Error: Could not find user {target_username}: {e}")
            await client.disconnect()
            return False

        # Get dialog info
        safe_chat_name = re.sub(r'[<>:"/\\|?*]', '', (entity.first_name or '') + ((' ' + entity.last_name) if hasattr(entity, 'last_name') and entity.last_name else '')).strip()
        if not safe_chat_name:
            safe_chat_name = uname.lstrip('@')

        # Save/update dialog
        dialog_id = save_dialog(user_id, entity.id, safe_chat_name, "private")

        # STEP 1: Always re-ingest last 80 messages (even if already ingested)
        # This ensures we get new messages and can check for deletions
        safe_print(f"Priority ingestion: Re-ingesting last 80 messages for {safe_chat_name}")
        telegram_message_ids = set()
        messages_saved = 0

        try:
            async for message in client.iter_messages(entity, limit=80, offset_id=0):
                telegram_message_ids.add(message.id)
                
                sender = "me" if message.out else safe_chat_name
                has_media = message.media is not None
                raw_json = json.dumps(message.to_dict(), default=str)
                
                # Extract reference_id from reply_to if present
                reference_id = None
                if hasattr(message, 'reply_to') and message.reply_to:
                    if hasattr(message.reply_to, 'reply_to_msg_id') and message.reply_to.reply_to_msg_id:
                        reference_id = message.reply_to.reply_to_msg_id

                msg_id = save_message(
                    dialog_id, message.id, sender, message.text,
                    message.date, has_media, raw_json, reference_id
                )
                if msg_id:
                    messages_saved += 1
        except Exception as e:
            safe_print(f"Error ingesting messages: {e}")

        safe_print(f"Priority ingestion: Saved/updated {messages_saved} messages")

        # STEP 2: Check for deleted messages - compare DB with Telegram
        # Get all message IDs from database for this dialog (in the last 80 messages range)
        try:
            # Get highest message ID from Telegram to determine range
            highest_msg_id = max(telegram_message_ids) if telegram_message_ids else 0
            
            # Get all message IDs from database for this dialog (only recent ones for efficiency)
            # We check messages that are likely in the last 80 messages range
            # To be safe, we check messages with ID >= (highest - 200) to account for deletions
            db_sql = """
                SELECT message_id FROM messages 
                WHERE dialog_id = %s 
                AND message_id >= %s
                ORDER BY message_id DESC
            """
            with get_connection() as conn, conn.cursor() as cur:
                min_id = max(0, highest_msg_id - 200) if highest_msg_id > 0 else 0
                cur.execute(db_sql, (dialog_id, min_id))
                db_message_ids = {row[0] for row in cur.fetchall()}
            
            # Find messages in database that don't exist in Telegram (were deleted)
            deleted_message_ids = db_message_ids - telegram_message_ids
            
            if deleted_message_ids:
                safe_print(f"Priority ingestion: Found {len(deleted_message_ids)} deleted message(s) in Telegram, removing from database...")
                # Delete messages and their associated media
                with get_connection() as conn, conn.cursor() as cur:
                    for deleted_msg_id in deleted_message_ids:
                        # Get internal message ID to delete media
                        internal_sql = "SELECT id FROM messages WHERE dialog_id = %s AND message_id = %s"
                        cur.execute(internal_sql, (dialog_id, deleted_msg_id))
                        internal_result = cur.fetchone()
                        if internal_result:
                            internal_msg_id = internal_result[0]
                            # Delete media first (foreign key constraint)
                            media_delete_sql = "DELETE FROM media WHERE message_id = %s"
                            cur.execute(media_delete_sql, (internal_msg_id,))
                            # Delete message
                            msg_delete_sql = "DELETE FROM messages WHERE dialog_id = %s AND message_id = %s"
                            cur.execute(msg_delete_sql, (dialog_id, deleted_msg_id))
                    conn.commit()
                safe_print(f"Priority ingestion: Removed {len(deleted_message_ids)} deleted message(s) from database")
            else:
                safe_print(f"Priority ingestion: No deleted messages found (all messages in DB exist in Telegram)")
        except Exception as e:
            safe_print(f"Warning: Failed to check for deleted messages: {e}")

        await client.disconnect()
        
        # Release priority lock
        try:
            if lock_path.exists():
                lock_path.unlink()
        except Exception as e:
            safe_print(f"Warning: Could not remove priority lock: {e}")
        
        return True

    except Exception as e:
        safe_print(f"Error in priority ingestion: {e}")
        try:
            await client.disconnect()
        except:
            pass
        # Release priority lock
        try:
            if lock_path.exists():
                lock_path.unlink()
        except:
            pass
        return False

if __name__ == '__main__':
    if len(sys.argv) >= 2:
        target = sys.argv[1]
        result = asyncio.run(ingest_priority_target(target))
        sys.exit(0 if result else 1)
    else:
        print(json.dumps({"success": False, "error": "Usage: python priority_ingestor.py <target_username>"}))
        sys.exit(1)

