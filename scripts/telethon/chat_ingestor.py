import re
import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import requests
import time
from datetime import datetime
import psycopg2
import json
import pathlib
from typing import Optional
import os

load_dotenv()

# Telegram credentials
api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')
tg_password = os.getenv("TELEGRAM_PASSWORD")
platform_account_id = int(os.getenv("PLATFORM_ACCOUNT_ID", "0"))

# PostgreSQL connection (use container host, not localhost)
DB_CONFIG = {
    "dbname": os.getenv("DATABASE_NAME", "aria"),
    "user": os.getenv("DATABASE_USER", "postgres"),
    "password": os.getenv("DATABASE_PASSWORD", "Ezekiel(23)"),
    # In Docker, postgres service is reachable by its service name/hostname
    "host": os.getenv("DB_HOST", "aria-postgres"),
    "port": int(os.getenv("DB_PORT", "5432")),
}

# Media directory: connector/user/chat hierarchy
tg_username = os.getenv('TELEGRAM_USERNAME', '').lstrip('@')
BASE_MEDIA_ROOT = os.path.join('media', 'telegramConnector')
# Always prefer username for folder naming to avoid duplicates; fall back only if truly unknown.
user_label = tg_username if tg_username else (phone or 'unknown_user')
USER_DIR = f"user_{user_label}"
os.makedirs(os.path.join(BASE_MEDIA_ROOT, USER_DIR), exist_ok=True)


# ========================
# Utility functions
# ========================
def remove_emojis(text):
    if text is None:
        return ""
    emoji_pattern = re.compile(r'\\U000[^\\s]*', re.UNICODE)
    cleaned_text = emoji_pattern.sub('', text)
    cleaned_text = re.sub(r'\s+', ' ', cleaned_text).strip()
    return cleaned_text


def safe_print(text, max_length=100):
    if text is None:
        text = ""
    cleaned_text = remove_emojis(str(text))
    if len(cleaned_text) > max_length:
        cleaned_text = cleaned_text[:max_length] + "..."
    print(cleaned_text)


def create_safe_filename(name):
    if name is None:
        return "unknown"
    safe_name = remove_emojis(name)
    safe_name = re.sub(r'[<>:"/\\|?*]', '', safe_name)
    safe_name = re.sub(r'\s+', ' ', safe_name).strip()
    if not safe_name:
        safe_name = "unknown_chat"
    return safe_name


# ========================
# Database helpers
# ========================
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


def get_last_message_id(dialog_id):
    """Get the last (highest) message ID for a dialog (for incremental ingestion)"""
    sql = "SELECT MAX(message_id) FROM messages WHERE dialog_id = %s"
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (dialog_id,))
        result = cur.fetchone()[0]
        return result if result is not None else None


def save_dialog(user_id, dialog_id, name, type_, message_count, media_count):
    sql = """
        INSERT INTO dialogs (user_id, platform_account_id, dialog_id, name, type, message_count, media_count, last_synced)
        VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
        ON CONFLICT (user_id, platform_account_id, dialog_id) DO UPDATE
        SET name = EXCLUDED.name,
            type = EXCLUDED.type,
            message_count = EXCLUDED.message_count,
            media_count = EXCLUDED.media_count,
            last_synced = EXCLUDED.last_synced
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (user_id, platform_account_id, dialog_id, name, type_, message_count, media_count))
        result = cur.fetchone()
        return result[0] if result else None


def save_message(dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id=None):
    sql = """
        INSERT INTO messages (dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb, %s)
        ON CONFLICT (dialog_id, message_id) DO NOTHING
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (dialog_id, message_id, sender, text, timestamp, has_media, raw_json, reference_id))
        result = cur.fetchone()
        return result[0] if result else None


def save_media(message_id, type_, file_path, file_name, file_size, mime_type):
    sql = """
        INSERT INTO media (message_id, type, file_path, file_name, file_size, mime_type)
        VALUES (%s, %s, %s, %s, %s, %s)
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (message_id, type_, file_path, file_name, file_size, mime_type))


# ========================
# Media downloader
# ========================
async def download_media(client, message, chat_name, message_id):
    if not message.media:
        return None
    try:
        safe_chat_name = create_safe_filename(chat_name)
        chat_dir = os.path.join(BASE_MEDIA_ROOT, USER_DIR, f"{safe_chat_name}_chat")
        os.makedirs(chat_dir, exist_ok=True)
        file_ext = ".bin"
        media_type = "unknown"

        if hasattr(message.media, 'photo'):
            file_ext = ".jpg"
            media_type = "photo"
        elif hasattr(message.media, 'document'):
            doc = message.media.document
            if doc.mime_type:
                if 'image' in doc.mime_type:
                    file_ext = ".jpg"
                    media_type = "image"
                elif 'video' in doc.mime_type:
                    file_ext = ".mp4"
                    media_type = "video"
                elif 'audio' in doc.mime_type:
                    file_ext = ".mp3"
                    media_type = "audio"
                else:
                    for attr in doc.attributes:
                        if hasattr(attr, 'file_name'):
                            original_name = attr.file_name or ''
                            file_ext = os.path.splitext(original_name)[1] or ".bin"
                            break
                    media_type = "document"
            else:
                media_type = "document"
                file_ext = ".bin"

        # Try to get original filename from document attributes
        original_filename = None
        if hasattr(message.media, 'document'):
            doc = message.media.document
            if hasattr(doc, 'attributes'):
                for attr in doc.attributes:
                    if hasattr(attr, 'file_name') and attr.file_name:
                        original_filename = attr.file_name
                        break
        
        # Generate a safe filename for storage
        # Use original filename if available, otherwise generate one based on media type
        if original_filename:
            # Use original filename but make it safe for filesystem
            safe_original = create_safe_filename(original_filename)
            filename = f"{safe_chat_name}_{message_id}_{safe_original}"
        else:
            filename = f"{safe_chat_name}_{message_id}_{media_type}{file_ext}"
        
        filepath = os.path.join(chat_dir, filename)
        await client.download_media(message, file=filepath)
        file_size = os.path.getsize(filepath) if os.path.exists(filepath) else 0

        # For display, prefer original filename, otherwise use the generated one
        display_filename = original_filename if original_filename else filename

        return {
            "type": media_type,
            "file_path": filepath,
            "file_name": display_filename,  # Original filename for display, or generated one
            "file_size": file_size,
            "mime_type": getattr(message.media.document, 'mime_type', None) if hasattr(message.media, 'document') else None
        }
    except Exception as e:
        print(f"Error downloading media for message {message_id}: {e}")
        return None


# ========================
# Main ingestion
# ========================
LOCK_PATH = pathlib.Path(os.getenv("TELETHON_LOCK_PATH", "/app/telethon_send.lock"))


async def _wait_for_sender_priority(max_wait_ms: Optional[int] = None):
    """
    Yield immediately when a sender lock exists to give priority
    to the message-sending path. Checks every 50ms.
    If max_wait_ms is provided, stop waiting when exceeded.
    """
    waited = 0
    while LOCK_PATH.exists():
        # Keep the wait fine-grained for near-instant preemption
        await asyncio.sleep(0.05)
        if max_wait_ms is not None:
            waited += 50
            if waited >= max_wait_ms:
                break


async def _disconnect_if_sending(client: TelegramClient):
    """
    If a sender lock exists and the client is connected, disconnect to release
    the Telethon SQLite session immediately. Reconnect responsibility is left
    to the caller after the lock is gone.
    """
    try:
        if LOCK_PATH.exists():
            try:
                if client is not None and client.is_connected():
                    await client.disconnect()
            except Exception:
                pass
            # Wait until sender finishes
            await _wait_for_sender_priority()
    except Exception:
        pass


async def _ensure_connected(client: TelegramClient):
    """
    Ensure the client is connected and authorized. Reconnects if needed.
    """
    try:
        if not client.is_connected():
            await client.connect()
        if not await client.is_user_authorized():
            await client.start(phone=phone)
    except Exception:
        # As a fallback, try a full start
        try:
            await client.start(phone=phone)
        except Exception:
            pass


async def _retry_on_db_lock(coro, max_retries=10, delay=0.1):
    """
    Retry an async operation that might hit SQLite database locks.
    Common in Telethon when multiple processes access the session file.
    
    Usage: await _retry_on_db_lock(client.get_me())
    """
    import sqlite3
    for attempt in range(max_retries):
        try:
            return await coro
        except sqlite3.OperationalError as e:
            if "database is locked" in str(e).lower() and attempt < max_retries - 1:
                await asyncio.sleep(delay * (attempt + 1))  # Exponential backoff
                continue
            raise
        except Exception as e:
            # For other Telethon errors that might wrap SQLite errors
            if "database is locked" in str(e).lower() and attempt < max_retries - 1:
                await asyncio.sleep(delay * (attempt + 1))
                continue
            raise
    raise Exception("Operation failed after max retries")


async def ingest_chat_history():
    # Per-account session path
    session_path = os.getenv('TELETHON_SESSION_PATH', 'aria_session')
    try:
        pathlib.Path(session_path).parent.mkdir(parents=True, exist_ok=True)
    except Exception:
        pass
    client = TelegramClient(session_path, api_id, api_hash)

    # If sender lock exists, wait (up to 10s) to give priority to sending
    await _wait_for_sender_priority(max_wait_ms=10_000)

    # Check if session file exists - if it does, connect without starting (won't trigger OTP)
    session_file = pathlib.Path(session_path + '.session')
    if session_file.exists():
        try:
            await client.connect()
            # If already authorized, we're done - don't call start() which would trigger OTP
            if await client.is_user_authorized():
                safe_print("Using existing session file")
            else:
                # Session file exists but not authorized - need to start
                await client.start(phone=phone)
        except Exception as e:
            # If connect fails, fall back to start
            safe_print(f"Failed to connect to existing session: {e}")
            await client.start(phone=phone)
    else:
        # No session file exists - need to start (will trigger OTP)
        await client.start(phone=phone)

    safe_print("Connected. Fetching dialogs...")
    start_time = time.time()

    # Resolve user_id via platform_accounts
    user_id = get_user_id_by_platform_account(platform_account_id)

    # Yield to sender before heavy calls and ensure we are connected again
    await _disconnect_if_sending(client)
    await _ensure_connected(client)

    # Determine current (self) user id to skip self-chats (with retry for database locks)
    try:
        me = await _retry_on_db_lock(client.get_me())
        my_user_id = getattr(me, 'id', None)
    except Exception as e:
        safe_print(f"Warning: Could not get_me after retries: {e}")
        my_user_id = None

    # Get dialogs with retry for database locks
    try:
        dialogs = await _retry_on_db_lock(client.get_dialogs())
        safe_print(f"Found {len(dialogs)} dialogs.")
    except Exception as e:
        safe_print(f"Error fetching dialogs: {e}")
        dialogs = []

    total_messages, total_media_downloaded = 0, 0

    # Check for priority target username (for immediate ingestion of target user's messages)
    priority_target_username = os.getenv('PRIORITY_TARGET_USERNAME', '').lstrip('@').lower()
    priority_dialog = None
    other_dialogs = []
    
    # Separate priority dialog from others
    for dialog in dialogs:
        if dialog.is_user:
            entity = dialog.entity
            # Check if this is the priority target
            if priority_target_username:
                dialog_username = getattr(entity, 'username', '').lower() if hasattr(entity, 'username') else ''
                dialog_name_lower = dialog.name.lower()
                if dialog_username == priority_target_username or dialog_name_lower == priority_target_username:
                    priority_dialog = dialog
                    safe_print(f"Found priority target dialog: {dialog.name}")
                    continue
            other_dialogs.append(dialog)
        else:
            other_dialogs.append(dialog)
    
    # Process priority dialog first if it exists
    dialogs_to_process = []
    if priority_dialog:
        dialogs_to_process.append(priority_dialog)
    dialogs_to_process.extend(other_dialogs)

    for dialog in dialogs_to_process:
        # Yield to sender if lock appears while processing each dialog
        await _disconnect_if_sending(client)
        if not await client.is_user_authorized():
            await client.start(phone=phone)
        # Filter: Only process private user chats (exclude groups, channels, supergroups, bots)
        if dialog.is_user:
            entity = dialog.entity
            
            # Skip self-chat (user chatting with own account)
            if hasattr(entity, 'id') and my_user_id is not None and entity.id == my_user_id:
                safe_print(f"Skipping self-chat: {dialog.name}")
                continue

            # Skip bots
            if hasattr(entity, 'bot') and entity.bot:
                safe_print(f"Skipping bot: {dialog.name}")
                continue
            
            # Determine dialog type
            if hasattr(entity, 'bot') and entity.bot:
                dialog_type = "bot"
            elif dialog.is_group:
                dialog_type = "group"
            elif dialog.is_channel:
                dialog_type = "channel"
            else:
                dialog_type = "private"  # One-on-one chat
            
            # Skip groups, channels, and bots
            if dialog_type in ["group", "channel", "bot", "supergroup"]:
                safe_print(f"Skipping {dialog_type}: {dialog.name}")
                continue
            
            safe_chat_name = remove_emojis(dialog.name)
            safe_print(f"\nProcessing chat: {safe_chat_name} (type: {dialog_type})")
            message_count, media_count = 0, 0

            is_bot = False
            dialog_id = save_dialog(user_id, dialog.entity.id, safe_chat_name, dialog_type, 0, 0)

            # Get last message ID for incremental ingestion (only process new messages)
            last_message_id = get_last_message_id(dialog_id)
            has_new_messages = False
            
            # Check if this is the priority target dialog (for first-time ingestion, limit to 80 messages)
            # This ensures we have enough messages even if some are deleted (UI displays last 50)
            is_priority_target = (priority_dialog and dialog == priority_dialog)
            
            if last_message_id:
                safe_print(f"  Resuming from message ID {last_message_id} (incremental update)")
                # Robust incremental: loop with reconnect on disconnect and database lock retry
                resume_from_id = last_message_id
                while True:
                    try:
                        async for message in client.iter_messages(dialog.entity, limit=200, offset_id=resume_from_id):
                            # Yield to sender; if we disconnect, outer except will handle
                            await _disconnect_if_sending(client)
                            await _ensure_connected(client)

                            # Stop if we reach messages older than or equal to last_message_id
                            if message.id <= last_message_id:
                                break

                            has_new_messages = True
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
                            if msg_id:  # Only count if message was actually saved (not duplicate)
                                message_count += 1

                            if has_media and msg_id:
                                media_info = await download_media(client, message, dialog.name, message.id)
                                if media_info:
                                    save_media(
                                        msg_id, media_info["type"], media_info["file_path"],
                                        media_info["file_name"], media_info["file_size"], media_info["mime_type"]
                                    )
                                    media_count += 1
                                    total_media_downloaded += 1

                            # Advance resume point to the newest processed id
                            if message.id > resume_from_id:
                                resume_from_id = message.id
                        # Completed iteration without disconnect
                        break
                    except Exception as e:
                        # If disconnected mid-iteration, reconnect and continue
                        if "Cannot send requests while disconnected" in str(e) or "disconnected" in str(e).lower():
                            await _ensure_connected(client)
                            # Loop continues with same resume_from_id
                            continue
                        # If database locked, wait and retry
                        if "database is locked" in str(e).lower():
                            await asyncio.sleep(0.2)
                            continue
                        # Not a disconnect/db lock issue; re-raise
                        raise
            else:
                # For priority target on first ingestion:
                # Phase 1: Get last 80 messages immediately (for UI display)
                # Phase 2: Continue with full ingestion from beginning
                # For other dialogs, get all messages (limit=200 per batch)
                if is_priority_target:
                    safe_print(f"  Priority target: First getting last 80 messages for immediate display")
                    # Phase 1: Get last 80 messages
                    messages_processed_phase1 = 0
                    oldest_message_id_phase1 = None
                    while True:
                        try:
                            async for message in client.iter_messages(dialog.entity, limit=80, offset_id=0):
                                if messages_processed_phase1 >= 80:
                                    break
                                await _disconnect_if_sending(client)
                                await _ensure_connected(client)

                                has_new_messages = True
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
                                    message_count += 1
                                    messages_processed_phase1 += 1
                                    if oldest_message_id_phase1 is None or message.id < oldest_message_id_phase1:
                                        oldest_message_id_phase1 = message.id

                                if has_media and msg_id:
                                    media_info = await download_media(client, message, dialog.name, message.id)
                                    if media_info:
                                        save_media(
                                            msg_id, media_info["type"], media_info["file_path"],
                                            media_info["file_name"], media_info["file_size"], media_info["mime_type"]
                                        )
                                        media_count += 1
                                        total_media_downloaded += 1

                                if messages_processed_phase1 >= 80:
                                    break
                            break
                        except Exception as e:
                            if "Cannot send requests while disconnected" in str(e) or "disconnected" in str(e).lower():
                                await _ensure_connected(client)
                                continue
                            if "database is locked" in str(e).lower():
                                await asyncio.sleep(0.2)
                                continue
                            raise
                    
                    safe_print(f"  Priority target: Phase 1 complete (80 messages). Now continuing with full history from beginning")
                    # Phase 2: Continue with full ingestion from the oldest message we got
                    # This ensures we get the complete history
                    resume_from_id = oldest_message_id_phase1 if oldest_message_id_phase1 else 0
                    while True:
                        try:
                            async for message in client.iter_messages(dialog.entity, limit=200, offset_id=resume_from_id):
                                await _disconnect_if_sending(client)
                                await _ensure_connected(client)

                                # Skip messages we already processed in phase 1
                                if oldest_message_id_phase1 and message.id >= oldest_message_id_phase1:
                                    continue

                                has_new_messages = True
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
                                    message_count += 1

                                if has_media and msg_id:
                                    media_info = await download_media(client, message, dialog.name, message.id)
                                    if media_info:
                                        save_media(
                                            msg_id, media_info["type"], media_info["file_path"],
                                            media_info["file_name"], media_info["file_size"], media_info["mime_type"]
                                        )
                                        media_count += 1
                                        total_media_downloaded += 1

                                if message.id > resume_from_id:
                                    resume_from_id = message.id
                            break
                        except Exception as e:
                            if "Cannot send requests while disconnected" in str(e) or "disconnected" in str(e).lower():
                                await _ensure_connected(client)
                                continue
                            if "database is locked" in str(e).lower():
                                await asyncio.sleep(0.2)
                                continue
                            raise
                    safe_print(f"  Priority target: Full ingestion complete")
                else:
                    # For non-priority dialogs, get all messages normally
                    safe_print(f"  First time ingestion (all messages)")
                    # Robust full ingestion with reconnect on disconnect and database lock retry
                    resume_from_id = 0
                    while True:
                        try:
                            async for message in client.iter_messages(dialog.entity, limit=200, offset_id=resume_from_id):
                                await _disconnect_if_sending(client)
                                await _ensure_connected(client)

                                has_new_messages = True
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
                                    message_count += 1

                                if has_media and msg_id:
                                    media_info = await download_media(client, message, dialog.name, message.id)
                                    if media_info:
                                        save_media(
                                            msg_id, media_info["type"], media_info["file_path"],
                                            media_info["file_name"], media_info["file_size"], media_info["mime_type"]
                                        )
                                        media_count += 1
                                        total_media_downloaded += 1

                                if message.id > resume_from_id:
                                    resume_from_id = message.id
                            break
                        except Exception as e:
                            if "Cannot send requests while disconnected" in str(e) or "disconnected" in str(e).lower():
                                await _ensure_connected(client)
                                continue
                            if "database is locked" in str(e).lower():
                                await asyncio.sleep(0.2)
                                continue
                            raise
            
            # If this dialog has new messages, mark it for re-categorization
            # (This will be handled by Java code after ingestion completes)
            if has_new_messages:
                safe_print(f"  Dialog {dialog_id} has new messages - will be re-categorized")

            total_messages += message_count
            safe_print(f"Saved {message_count} messages, {media_count} media for {safe_chat_name}")

            # Trigger categorization pipeline for this user in background (non-blocking)
            try:
                requests.post(f"http://localhost:8080/api/analysis/categorize?userId={user_id}", timeout=1)
            except Exception:
                pass

    total_duration = time.time() - start_time
    safe_print(f"\nIngestion complete in {total_duration:.2f}s")
    safe_print(f"Total messages: {total_messages}, total media: {total_media_downloaded}")

    await client.disconnect()


if __name__ == "__main__":
    asyncio.run(ingest_chat_history())
