# message_sender.py
import asyncio
from telethon import TelegramClient
from telethon.tl.types import InputPeerUser
import os
from dotenv import load_dotenv

load_dotenv()

api_id = os.getenv('TELEGRAM_API_ID')
api_hash = os.getenv('TELEGRAM_API_HASH')
phone = os.getenv('TELEGRAM_PHONE')


async def send_message(target_username, message):
    client = TelegramClient('aria_sender_session', api_id, api_hash)

    await client.start(phone=phone)

    try:
        # Find the user by username
        entity = await client.get_entity(target_username)
        await client.send_message(entity, message)
        print(f"Message sent to {target_username}")
        return True
    except Exception as e:
        print(f"Error sending message: {e}")
        return False
    finally:
        await client.disconnect()


if __name__ == '__main__':
    # For testing
    import sys

    if len(sys.argv) == 3:
        target = sys.argv[1]
        msg = sys.argv[2]
        asyncio.run(send_message(target, msg))
    else:
        print("Usage: python message_sender.py <username> <message>")
