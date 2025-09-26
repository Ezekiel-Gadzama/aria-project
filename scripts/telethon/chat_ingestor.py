import re
import asyncio
from telethon import TelegramClient
import json
import os
from dotenv import load_dotenv
import time
from datetime import datetime

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')
tg_password = os.getenv("TELEGRAM_PASSWORD")

# Create media directory if it doesn't exist
MEDIA_DIR = 'media/telegram_media'
os.makedirs(MEDIA_DIR, exist_ok=True)


def remove_emojis(text):
    """
    Remove emojis by removing all patterns that start with \U000 until a space or end of string.

    Args:
        text (str): Input string that may contain emojis

    Returns:
        str: Cleaned string with emojis removed
    """
    if text is None:
        return ""

    # Remove all \U000 patterns and everything until the next space or end of string
    # This pattern matches \U000 followed by any characters until a space or end of string
    emoji_pattern = re.compile(r'\\U000[^\\s]*', re.UNICODE)

    cleaned_text = emoji_pattern.sub('', text)

    # Also remove any extra spaces that might result from the removal
    cleaned_text = re.sub(r'\s+', ' ', cleaned_text).strip()

    return cleaned_text


def safe_print(text, max_length=100):
    """
    Safely print text by removing emojis and truncating if necessary.

    Args:
        text (str): Text to print
        max_length (int): Maximum length to display (optional)
    """
    if text is None:
        text = ""

    # Remove emojis and clean the text
    cleaned_text = remove_emojis(str(text))

    # Truncate if too long for display
    if len(cleaned_text) > max_length:
        cleaned_text = cleaned_text[:max_length] + "..."

    print(cleaned_text)


def create_safe_filename(name):
    """
    Create a safe filename by removing emojis and problematic characters.

    Args:
        name (str): Original name

    Returns:
        str: Safe filename
    """
    if name is None:
        return "unknown"

    # Remove emojis first
    safe_name = remove_emojis(name)

    # Remove any other problematic characters for filenames
    safe_name = re.sub(r'[<>:"/\\|?*]', '', safe_name)

    # Replace multiple spaces with single space and trim
    safe_name = re.sub(r'\s+', ' ', safe_name).strip()

    # If the name becomes empty after cleaning, use a default
    if not safe_name:
        safe_name = "unknown_chat"

    return safe_name


def load_existing_chats():
    """Load existing chat data from JSON file if it exists"""
    if os.path.exists('chats_export.json'):
        try:
            with open('chats_export.json', 'r', encoding='utf-8') as f:
                return json.load(f)
        except (json.JSONDecodeError, Exception) as e:
            print(f"Warning: Could not load existing chats file: {e}")
            return None
    return None


async def download_media(client, message, chat_name, message_id):
    """Download media from a message and return media info"""
    if not message.media:
        return None

    try:
        # Create safe filename using emoji-free name
        safe_chat_name = create_safe_filename(chat_name)
        file_ext = '.jpg'  # default extension

        # Determine file type and extension
        if hasattr(message.media, 'photo'):
            file_ext = '.jpg'
            media_type = 'photo'
        elif hasattr(message.media, 'document'):
            doc = message.media.document
            if doc.mime_type:
                if 'image' in doc.mime_type:
                    file_ext = '.jpg'
                    media_type = 'image'
                elif 'video' in doc.mime_type:
                    file_ext = '.mp4'
                    media_type = 'video'
                elif 'audio' in doc.mime_type:
                    file_ext = '.mp3'
                    media_type = 'audio'
                else:
                    # Get the original file extension from attributes
                    for attr in doc.attributes:
                        if hasattr(attr, 'file_name'):
                            original_name = attr.file_name or ''
                            file_ext = os.path.splitext(original_name)[1] or '.bin'
                            break
                    media_type = 'document'
            else:
                media_type = 'document'
                file_ext = '.bin'
        else:
            media_type = 'unknown'
            file_ext = '.bin'

        # Create filename: chat name_messageID_type.ext
        filename = f"{safe_chat_name}_{message_id}_{media_type}{file_ext}"
        filepath = os.path.join(MEDIA_DIR, filename)

        # Download the media file
        await client.download_media(message, file=filepath)

        # Get file size
        file_size = os.path.getsize(filepath) if os.path.exists(filepath) else 0

        return {
            'type': media_type,
            'file_path': filepath,
            'file_name': filename,
            'file_size': file_size,
            'mime_type': getattr(message.media.document, 'mime_type', None) if hasattr(message.media,
                                                                                       'document') else None
        }

    except Exception as e:
        print(f"Error downloading media for message {message_id}: {e}")
        return None


def save_chats_with_timing(chat_data):
    """Save chat data with metadata"""
    data_to_save = {
        'export_metadata': {
            'export_timestamp': datetime.now().isoformat(),
            'export_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'total_chats': len(chat_data),
            'total_messages': sum(len(chat['messages']) for chat in chat_data),
            'media_directory': MEDIA_DIR
        },
        'chats': chat_data
    }

    with open('chats_export.json', 'w', encoding='utf-8') as f:
        json.dump(data_to_save, f, ensure_ascii=False, indent=2)


async def ingest_chat_history():
    # Check if we can use cached data
    existing_data = load_existing_chats()

    if existing_data and 'chats' in existing_data:
        safe_print("Using existing chat data from chats_export.json")
        metadata = existing_data.get('export_metadata', {})
        safe_print(f"Previously exported: {metadata.get('export_date', 'Unknown')}")
        safe_print(f"Total chats: {metadata.get('total_chats', 0)}")
        safe_print(f"Total messages: {metadata.get('total_messages', 0)}")
        return

    client = TelegramClient('aria_session', api_id, api_hash)
    await client.start(phone=phone)

    safe_print("Connected. Fetching dialogs...")
    start_time = time.time()

    dialogs = await client.get_dialogs()
    safe_print(f"Found {len(dialogs)} dialogs in total.")

    chat_data = []
    total_messages = 0
    total_media_downloaded = 0

    for dialog in dialogs:
        if dialog.is_user:  # Only individual chats
            chat_start_time = time.time()
            safe_chat_name = remove_emojis(dialog.name)
            safe_print(f"\nProcessing chat with: {safe_chat_name}")
            messages = []
            media_count = 0

            async for message in client.iter_messages(dialog.entity, limit=None):
                sender = "me" if message.out else safe_chat_name
                text_preview = (message.text[:50] + "...") if message.text and len(message.text) > 50 else (
                            message.text or "")

                # Download media if present
                media_info = None
                if message.media:
                    safe_print(f"Downloading media for message {message.id}...")
                    media_info = await download_media(client, message, dialog.name, message.id)
                    if media_info:
                        media_count += 1
                        total_media_downloaded += 1

                msg_data = {
                    'id': message.id,
                    'sender': sender,
                    'text': message.text,
                    'date': message.date.isoformat(),
                    'timestamp': message.date.timestamp(),
                    'media': media_info
                }
                messages.append(msg_data)

            chat_duration = time.time() - chat_start_time
            safe_print(f"Finished {len(messages)} messages from {safe_chat_name}")
            safe_print(f"Downloaded {media_count} media files (took {chat_duration:.2f}s)")
            total_messages += len(messages)

            chat_data.append({
                'contact': safe_chat_name,  # Use emoji-free name
                'contact_id': dialog.entity.id,
                'message_count': len(messages),
                'media_count': media_count,
                'processing_time_seconds': round(chat_duration, 2),
                'messages': messages
            })

    total_duration = time.time() - start_time

    save_chats_with_timing(chat_data)

    safe_print(f"\nChat ingestion completed!")
    safe_print(f"Total time: {total_duration:.2f} seconds")
    safe_print(f"Total chats processed: {len(chat_data)}")
    safe_print(f"Total messages saved: {total_messages}")
    safe_print(f"Total media files downloaded: {total_media_downloaded}")
    safe_print(f"Data saved to chats_export.json")
    safe_print(f"Media files saved to {MEDIA_DIR}/ directory")

    await client.disconnect()


if __name__ == '__main__':
    asyncio.run(ingest_chat_history())
