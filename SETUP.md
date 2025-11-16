# ARIA Setup Guide

## Quick Start

### 1. Prerequisites
- Java 17 or later
- Maven 3.6+
- PostgreSQL 15+
- Python 3.8+ (for Telethon scripts)
- Docker & Docker Compose (optional)

### 2. Database Setup

#### Option A: Using Docker (Recommended)
```bash
docker-compose up -d postgres
```

#### Option B: Manual PostgreSQL Setup
```bash
# Install PostgreSQL
# Create database
createdb aria

# Or using psql
psql -U postgres
CREATE DATABASE aria;
\q
```

### 3. Configuration

Edit `src/main/resources/config.properties`:

```properties
# Database Configuration
database.url=jdbc:postgresql://localhost:5432/aria
database.user=postgres
database.password=Ezekiel(23)

# OpenAI Configuration
openai.api.key=your_openai_api_key_here
openai.model=gpt-3.5-turbo
openai.max_tokens=300
openai.temperature=0.7

# Undetectable.ai Configuration (Optional)
undetectable.ai.api.key=your_undetectable_ai_api_key_here

# Telegram Configuration
telegram.api.id=your_telegram_api_id
telegram.api.hash=your_telegram_api_hash
telegram.phone=+1234567890
```

### 4. Python Dependencies

Install Telethon dependencies:
```bash
cd scripts/telethon
pip install -r requirements.txt
```

### 5. Build Application

```bash
mvn clean package
```

### 6. Run Application

#### Using Maven:
```bash
mvn javafx:run
```

#### Using JAR:
```bash
java -jar target/aria-core-1.0-SNAPSHOT.jar
```

#### Using Docker (Full Stack):
```bash
# Set environment variables
export OPENAI_API_KEY=your_key
export UNDETECTABLE_AI_API_KEY=your_key

# Start all services
docker-compose up
```

## First Run

1. **Database Schema Initialization**
   - The database schema will be automatically created on first run
   - Check logs for "Database schema initialized successfully"

2. **User Registration**
   - Register your user profile
   - Add personal information

3. **Platform Connection**
   - Go to Platform Registration
   - Enter Telegram API credentials
   - Complete authentication

4. **Import Chat History**
   - ARIA will automatically ingest your Telegram chats
   - Wait for initial categorization to complete (categorizes all chats with success scores)
   - On subsequent runs, only new messages are categorized (incremental updates)
   - Check logs for progress

5. **Create Target & Goal**
   - Create a target user
   - Define conversation goal
   - Provide meeting context

6. **Start Conversation**
   - ARIA will analyze historical chats
   - Generate personalized responses
   - Monitor engagement and timing

## Troubleshooting

### Database Connection Issues
- Verify PostgreSQL is running
- Check database credentials in config.properties
- Ensure database exists

### API Key Issues
- Verify OpenAI API key is valid
- Check Undetectable.ai API key (optional)
- Ensure API keys have sufficient credits

### Telegram Connection Issues
- Verify API credentials from https://my.telegram.org/apps
- Check phone number format (+country_code)
- Ensure Telethon scripts have correct permissions

### Build Issues
- Ensure Java 17+ is installed: `java -version`
- Verify Maven is installed: `mvn -version`
- Clean and rebuild: `mvn clean install`

## Environment Variables

You can also set configuration via environment variables:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/aria
export DATABASE_USER=postgres
export DATABASE_PASSWORD=your_password
export OPENAI_API_KEY=your_openai_key
export UNDETECTABLE_AI_API_KEY=your_undetectable_key
```

## Docker Commands

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Rebuild application
docker-compose build

# Access PostgreSQL
docker-compose exec postgres psql -U postgres -d aria
```

## Database Schema

The schema is automatically initialized. Key tables:
- `users` - User profiles
- `dialogs` - Chat conversations  
- `messages` - Individual messages
- `goals` - Conversation goals
- `chat_goals` - Chat categorization with success scores and outcome types
- `conversation_states` - Active conversations
- `style_profiles` - Communication styles
- `disinterest_logs` - Disinterest detection
- `conversation_summaries` - Summaries
- `quiz_questions` - Quiz questions

## Support

For issues:
1. Check application logs
2. Verify configuration
3. Ensure all dependencies are installed
4. Check database connectivity
5. Review API key validity

