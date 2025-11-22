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

def save_message(dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id=None, is_edited_on_telegram=False):
    """
    Save message to database. If message already exists:
    - If edited on Telegram (is_edited_on_telegram=True): Update text and timestamp
    - If not edited on Telegram: Preserve text to keep edits made through the app
    """
    if is_edited_on_telegram:
        # Message was edited on Telegram - update text and timestamp
        sql = """
            INSERT INTO messages (dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id)
            VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb, %s)
            ON CONFLICT (dialog_id, message_id) DO UPDATE SET
                text = EXCLUDED.text,
                timestamp = EXCLUDED.timestamp,
                has_media = EXCLUDED.has_media,
                raw_json = EXCLUDED.raw_json,
                reference_id = EXCLUDED.reference_id,
                last_updated = NOW()
            RETURNING id
        """
    else:
        # Message not edited on Telegram - preserve text to keep app edits
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
    Uses a priority lock to pause main ingestion during execution.
    """
    # Priority lock to pause main ingestion
    lock_env = os.getenv("TELETHON_LOCK_PATH", "/app/telethon_send.lock")
    lock_path = pathlib.Path(lock_env)
    lock_created = False
    
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
    
    # Create priority lock to pause main ingestion
    try:
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        lock_path.write_text("priority_ingestion", encoding="utf-8")
        lock_created = True
    except Exception as e:
        safe_print(f"Warning: Could not create priority lock: {e}")

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

        # STEP 1: Get message IDs and timestamps from database for last 80 messages
        # This will help us detect edits and deletions
        db_messages_info = {}  # {message_id: (db_text, db_timestamp)}
        try:
            db_sql = """
                SELECT message_id, text, timestamp FROM messages 
                WHERE dialog_id = %s 
                ORDER BY message_id DESC 
                LIMIT 100
            """
            with get_connection() as conn, conn.cursor() as cur:
                cur.execute(db_sql, (dialog_id,))
                for row in cur.fetchall():
                    msg_id_db, text_db, timestamp_db = row
                    db_messages_info[msg_id_db] = (text_db, timestamp_db)
        except Exception as e:
            safe_print(f"Warning: Failed to fetch existing messages from DB: {e}")

        # STEP 2: Always re-ingest last 80 messages (even if already ingested)
        # This ensures we get new messages, edits, and can check for deletions
        safe_print(f"Priority ingestion: Re-ingesting last 80 messages for {safe_chat_name}")
        telegram_message_ids = set()
        telegram_message_info = {}  # {message_id: (text, timestamp, edit_date)}
        messages_saved = 0
        messages_updated = 0

        try:
            async for message in client.iter_messages(entity, limit=80, offset_id=0):
                telegram_message_ids.add(message.id)
                
                sender = "me" if message.out else safe_chat_name
                has_media = message.media is not None
                message_dict = message.to_dict()
                raw_json = json.dumps(message_dict, default=str)
                
                # Check if message was edited on Telegram
                # Telegram messages have an 'edit_date' field if they were edited
                edit_date = message_dict.get('edit_date')
                is_edited_on_telegram = False
                
                # If message exists in DB, check if it was edited on Telegram
                # If edit_date exists, it was definitely edited on Telegram
                # Also check if text or timestamp changed (but preserve app edits)
                if message.id in db_messages_info:
                    db_text, db_timestamp = db_messages_info[message.id]
                    # Check if message was edited by comparing edit_date, text, or timestamp
                    if edit_date:
                        # Definitely edited on Telegram (has edit_date)
                        is_edited_on_telegram = True
                        messages_updated += 1
                    elif db_text and message.text and db_text.strip() != message.text.strip():
                        # Text changed - could be edited on Telegram or via app
                        # If timestamp is newer than DB timestamp, it was likely edited on Telegram
                        if db_timestamp and message.date.timestamp() > db_timestamp.timestamp():
                            is_edited_on_telegram = True
                            messages_updated += 1
                    elif db_timestamp and message.date.timestamp() > db_timestamp.timestamp():
                        # Timestamp changed (newer) - might have been edited on Telegram
                        # Check if text also changed or if edit_date exists in raw_json
                        if db_text and message.text and db_text.strip() != message.text.strip():
                            # Text changed and timestamp is newer - likely edited on Telegram
                            is_edited_on_telegram = True
                            messages_updated += 1
                        else:
                            # Timestamp changed but text same - check raw_json for edit_date
                            try:
                                raw_json_dict = json.loads(raw_json) if raw_json else {}
                                if raw_json_dict.get('edit_date'):
                                    # Has edit_date in raw_json - definitely edited on Telegram
                                    is_edited_on_telegram = True
                                    messages_updated += 1
                            except:
                                pass
                elif edit_date:
                    # New message with edit_date (shouldn't happen, but handle it)
                    is_edited_on_telegram = True
                
                # Extract reference_id from reply_to if present
                reference_id = None
                if hasattr(message, 'reply_to') and message.reply_to:
                    if hasattr(message.reply_to, 'reply_to_msg_id') and message.reply_to.reply_to_msg_id:
                        reference_id = message.reply_to.reply_to_msg_id

                msg_id = save_message(
                    dialog_id, message.id, sender, message.text,
                    message.date, has_media, raw_json, reference_id,
                    is_edited_on_telegram=is_edited_on_telegram
                )
                if msg_id:
                    messages_saved += 1
        except Exception as e:
            safe_print(f"Error ingesting messages: {e}")

        if messages_updated > 0:
            safe_print(f"Priority ingestion: Saved/updated {messages_saved} messages ({messages_updated} edited on Telegram)")
        else:
            safe_print(f"Priority ingestion: Saved/updated {messages_saved} messages")

        # STEP 3: Check for deleted messages - compare DB with Telegram
        # Get all message IDs from database that should be in the last 80 messages
        try:
            # Get message IDs from DB that are in the last 80 range
            # We check messages that are likely in the last 80 messages range
            # Get all message IDs from DB for this dialog (we'll filter by what exists in Telegram)
            db_sql = """
                SELECT message_id FROM messages 
                WHERE dialog_id = %s 
                ORDER BY message_id DESC 
                LIMIT 200
            """
            with get_connection() as conn, conn.cursor() as cur:
                cur.execute(db_sql, (dialog_id,))
                db_message_ids = {row[0] for row in cur.fetchall()}
            
            # Find messages in database that don't exist in Telegram (were deleted)
            # Check ALL messages in DB that are in the range of the last 80 messages we fetched from Telegram
            # We need to check messages that are >= lowest_telegram_id (inclusive) to account for all recent messages
            if telegram_message_ids:
                highest_telegram_id = max(telegram_message_ids)
                lowest_telegram_id = min(telegram_message_ids)
                # Check ALL DB messages that are in the range [lowest_telegram_id, highest_telegram_id + buffer]
                # We add a buffer of 100 to account for message ID gaps
                # This ensures we catch all messages that should exist in the last 80 but don't
                db_message_ids_in_range = {
                    msg_id for msg_id in db_message_ids 
                    if msg_id >= lowest_telegram_id and msg_id <= highest_telegram_id + 100
                }
                deleted_message_ids = db_message_ids_in_range - telegram_message_ids
            else:
                # No messages in Telegram - skip deletion check
                deleted_message_ids = set()
            
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
        if lock_created:
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
        if lock_created:
            try:
                if lock_path.exists():
                    lock_path.unlink()
            except Exception as e:
                safe_print(f"Warning: Could not remove priority lock: {e}")
        return False

if __name__ == '__main__':
    if len(sys.argv) >= 2:
        target = sys.argv[1]
        result = asyncio.run(ingest_priority_target(target))
        sys.exit(0 if result else 1)
    else:
        print(json.dumps({"success": False, "error": "Usage: python priority_ingestor.py <target_username>"}))
        sys.exit(1)

