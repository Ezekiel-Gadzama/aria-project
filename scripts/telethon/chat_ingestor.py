import asyncio
from telethon import TelegramClient
from telethon.tl.types import MessageMediaPhoto, MessageMediaDocument
import json
import os
from dotenv import load_dotenv

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')

async def ingest_chat_history():
    client = TelegramClient('aria_session', api_id, api_hash)

    await client.start(phone=phone)

    print("Connected. Fetching dialogs...")
    dialogs = await client.get_dialogs()

    chat_data = []

    for dialog in dialogs:
        if dialog.is_user:  # Only individual chats
            print(f"Processing chat with: {dialog.name}")
            messages = []

            async for message in client.iter_messages(dialog.entity, limit=1000):
                msg_data = {
                    'id': message.id,
                    'sender': 'me' if message.out else dialog.name,
                    'text': message.text,
                    'date': message.date.isoformat(),
                    'media': bool(message.media)
                }
                messages.append(msg_data)

            chat_data.append({
                'contact': dialog.name,
                'messages': messages
            })

    with open('chats_export.json', 'w', encoding='utf-8') as f:
        json.dump(chat_data, f, ensure_ascii=False, indent=2)

    print("Chat ingestion completed!")
    await client.disconnect()

if __name__ == '__main__':
    asyncio.run(ingest_chat_history())