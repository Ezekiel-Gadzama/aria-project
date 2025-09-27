import re
import asyncio
from telethon import TelegramClient
import os
from dotenv import load_dotenv
import time
from datetime import datetime
import psycopg2
import json

load_dotenv()

# Telegram credentials
api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')
tg_password = os.getenv("TELEGRAM_PASSWORD")

# PostgreSQL connection
DB_CONFIG = {
    "dbname": "aria",
    "user": "postgres",
    "password": "Ezekiel(23)",
    "host": "localhost",
    "port": 5432
}

# Media directory
MEDIA_DIR = 'media/telegram_media'
os.makedirs(MEDIA_DIR, exist_ok=True)


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


def save_user(username, phone, api_id, api_hash, tg_password):
    sql = """
        INSERT INTO users (username, phone, api_id, api_hash, tg_password)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (username) DO UPDATE SET phone = EXCLUDED.phone
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (username, phone, api_id, api_hash, tg_password))
        return cur.fetchone()[0]


def save_dialog(user_id, dialog_id, name, type_, message_count, media_count):
    sql = """
        INSERT INTO dialogs (user_id, dialog_id, name, type, message_count, media_count, last_synced)
        VALUES (%s, %s, %s, %s, %s, %s, NOW())
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (user_id, dialog_id, name, type_, message_count, media_count))
        return cur.fetchone()[0]


def save_message(dialog_id, message_id, sender, text, timestamp, has_media, raw_json):
    sql = """
        INSERT INTO messages (dialog_id, message_id, sender, text, timestamp, has_media, raw_json)
        VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb)
        RETURNING id
    """
    with get_connection() as conn, conn.cursor() as cur:
        cur.execute(sql, (dialog_id, message_id, sender, text, timestamp, has_media, raw_json))
        return cur.fetchone()[0]


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

        filename = f"{safe_chat_name}_{message_id}_{media_type}{file_ext}"
        filepath = os.path.join(MEDIA_DIR, filename)
        await client.download_media(message, file=filepath)
        file_size = os.path.getsize(filepath) if os.path.exists(filepath) else 0

        return {
            "type": media_type,
            "file_path": filepath,
            "file_name": filename,
            "file_size": file_size,
            "mime_type": getattr(message.media.document, 'mime_type', None) if hasattr(message.media, 'document') else None
        }
    except Exception as e:
        print(f"Error downloading media for message {message_id}: {e}")
        return None


# ========================
# Main ingestion
# ========================
async def ingest_chat_history():
    client = TelegramClient('aria_session', api_id, api_hash)
    await client.start(phone=phone)

    safe_print("Connected. Fetching dialogs...")
    start_time = time.time()

    # Save user (your account)
    user_id = save_user("me", phone, str(api_id), api_hash, tg_password)

    dialogs = await client.get_dialogs()
    safe_print(f"Found {len(dialogs)} dialogs.")

    total_messages, total_media_downloaded = 0, 0

    for dialog in dialogs[:20]:  # limit for testing
        if dialog.is_user:
            safe_chat_name = remove_emojis(dialog.name)
            safe_print(f"\nProcessing chat: {safe_chat_name}")
            message_count, media_count = 0, 0

            dialog_id = save_dialog(user_id, dialog.entity.id, safe_chat_name, "user", 0, 0)

            async for message in client.iter_messages(dialog.entity, limit=None):
                sender = "me" if message.out else safe_chat_name
                has_media = message.media is not None
                raw_json = json.dumps(message.to_dict(), default=str)

                msg_id = save_message(
                    dialog_id, message.id, sender, message.text,
                    message.date, has_media, raw_json
                )
                message_count += 1

                if has_media:
                    media_info = await download_media(client, message, dialog.name, message.id)
                    if media_info:
                        save_media(
                            msg_id, media_info["type"], media_info["file_path"],
                            media_info["file_name"], media_info["file_size"], media_info["mime_type"]
                        )
                        media_count += 1
                        total_media_downloaded += 1

            total_messages += message_count
            safe_print(f"Saved {message_count} messages, {media_count} media for {safe_chat_name}")

    total_duration = time.time() - start_time
    safe_print(f"\nIngestion complete in {total_duration:.2f}s")
    safe_print(f"Total messages: {total_messages}, total media: {total_media_downloaded}")

    await client.disconnect()


if __name__ == "__main__":
    asyncio.run(ingest_chat_history())
