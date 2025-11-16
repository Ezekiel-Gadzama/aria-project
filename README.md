# ARIA - Automated Relationship & Interaction Assistant

ARIA is a Java-based desktop application that automates and optimizes user-initiated conversations on social platforms (starting with Telegram) with the goal of achieving user-defined outcomes (e.g., dates, investments, sponsorships). The core innovation is an AI that personalizes its communication strategy by prioritizing the user's own successful historical chats.

## Features

### Core Functionality
- **Goal-Based Conversation Management**: Define conversation goals (dating, investment, sponsorship, etc.) and ARIA adapts accordingly
- **Historical Chat Analysis**: Analyzes years of chat history to learn your successful communication patterns
- **Weighted Response Synthesis (70%/15%/15%)**:
  - 70% weight on successful conversations (Category A)
  - 15% weight on failed/attempted conversations (Category B)
  - 15% weight on base AI personality (Category C)
- **AI Categorization**: Uses OpenAI to categorize chats into multiple goal-relevant categories with success scores
- **Incremental Categorization**: Only processes new messages on subsequent runs (optimizes API usage)
- **Smart Score Merging**: Intelligently merges old and new scores based on message count ratio and engagement metrics
- **Contextual Success Scoring**: Understands circumstantial rejections vs. approach failures (e.g., "I have a boyfriend" = high score)
- **Humanized Responses**: Integrates with Undetectable.ai to make AI responses undetectable

### Advanced Features (Phase 3)
- **Disinterest Detection**: Monitors engagement indicators and alerts when conversation shows signs of disinterest
- **Response Timing Analysis**: Analyzes optimal response delays based on engagement and historical patterns
- **Automated Conversation Management**: Handles sending/receiving messages with intelligent timing
- **Conversation Summarization**: Generates comprehensive summaries after conversations
- **Quiz System**: Tests user's memory of key conversation details before meetings/dates

## Architecture

### Technology Stack
- **Language**: Java 17+
- **Build Tool**: Maven
- **UI**: JavaFX
- **Database**: PostgreSQL
- **AI/NLP**: OpenAI API
- **Humanization**: Undetectable.ai API
- **Platform Integration**: Telethon (Python) for Telegram

### Project Structure
```
src/main/java/com/aria/
├── ai/                    # AI clients (OpenAI, Undetectable.ai)
├── analysis/              # Chat analysis (categorization, disinterest detection)
├── core/                  # Core orchestration and models
│   ├── model/            # Data models
│   └── strategy/         # Response strategies (70/15/15 synthesis)
├── platform/             # Platform connectors (Telegram, WhatsApp, Instagram)
├── service/              # Business logic services
├── storage/              # Database management
└── ui/                   # JavaFX UI controllers
```

## Setup

### Prerequisites
- Java 17 or later
- Maven 3.6+
- PostgreSQL 15+
- Python 3.8+ (for Telethon scripts)
- Docker & Docker Compose (optional, for easy deployment)

### Using Docker (Recommended)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Aria
   ```

2. **Set environment variables**
   Create a `.env` file:
   ```env
   OPENAI_API_KEY=your_openai_api_key
   UNDETECTABLE_AI_API_KEY=your_undetectable_ai_api_key
   ```

3. **Start services**
   ```bash
   docker-compose up -d
   ```

4. **Initialize database schema**
   The database schema will be automatically initialized when the application starts.

### Manual Setup

1. **Install PostgreSQL**
   ```bash
   # Create database
   createdb aria
   ```

2. **Update configuration**
   Edit `src/main/resources/config.properties`:
   ```properties
   database.url=jdbc:postgresql://localhost:5432/aria
   database.user=postgres
   database.password=your_password
   
   openai.api.key=your_openai_api_key
   undetectable.ai.api.key=your_undetectable_ai_api_key
   ```

3. **Install Python dependencies**
   ```bash
   cd scripts/telethon
   pip install -r requirements.txt
   ```

4. **Build the project**
   ```bash
   mvn clean package
   ```

5. **Run the application**
   ```bash
   mvn javafx:run
   ```

## Configuration

### Database Connection
Update `src/main/resources/config.properties` or use environment variables:
- `DATABASE_URL`
- `DATABASE_USER`
- `DATABASE_PASSWORD`

### API Keys
Set in `config.properties` or environment variables:
- `OPENAI_API_KEY`: Your OpenAI API key
- `UNDETECTABLE_AI_API_KEY`: Your Undetectable.ai API key (optional)

### Telegram Configuration
1. Get API credentials from [https://my.telegram.org/apps](https://my.telegram.org/apps)
2. Update `config.properties`:
   ```properties
   telegram.api.id=your_api_id
   telegram.api.hash=your_api_hash
   telegram.phone=+1234567890
   ```

## Usage

### 1. User Registration
- Launch the application
- Register your user profile
- Add your phone number and personal information

### 2. Connect Platform Account
- Go to Platform Registration
- Enter your Telegram API credentials
- Complete authentication

### 3. Import Chat History
- ARIA will automatically ingest your Telegram chat history
- The system categorizes chats based on goals using OpenAI
- Chats are stored in the database with proper indexing

### 4. Define Target & Goal
- Create a new target user (the person you want to chat with)
- Define the conversation goal (e.g., "Arrange a romantic date")
- Provide meeting context (e.g., "Met at tech conference, she works in VC")

### 5. Start Automated Conversation
- ARIA analyzes your historical chats relevant to the goal
- Synthesizes a communication profile using 70/15/15 weighting
- Generates and sends personalized responses
- Monitors engagement and adjusts timing
- Alerts you if disinterest is detected

### 6. Review Summary & Quiz
- After conversation ends, view the summary
- Take the quiz to ensure you remember key details
- Review next steps before your meeting/date

## Database Schema

### Key Tables
- `users`: User profiles
- `dialogs`: Chat conversations
- `messages`: Individual messages
- `goals`: Conversation goals
- `chat_goals`: Maps chats to goals/categories
- `conversation_states`: Active conversation states
- `style_profiles`: Analyzed communication styles
- `disinterest_logs`: Disinterest detection records
- `conversation_summaries`: Conversation summaries
- `quiz_questions`: Generated quiz questions

All tables have optimal indexes for fast queries.

## How It Works

### Chat Categorization

#### Initial Categorization
1. User defines goal and meeting context
2. ARIA sends description to OpenAI with available categories
3. OpenAI returns relevant categories with success scores (0-100%) and outcome types
4. Categories and scores are stored in database per chat
5. ARIA fetches all chats in those categories using stored scores (no re-scoring needed)

#### Incremental Categorization
1. On subsequent runs, ARIA checks last categorization timestamp per chat
2. Only new messages (after last timestamp) are sent to OpenAI
3. New categories are added to database
4. Existing categories are updated using smart score merging:
   - Considers message count ratio (old vs new)
   - Considers engagement metrics (reply ratio, timing, engagement level)
   - Balances data volume (60%) vs data quality (40%)
5. Historical chats are retrieved using stored scores (optimized - no OpenAI calls)

### Weighted Response Synthesis
1. **Category A (70%)**: Successful conversations where goal was achieved
2. **Category B (15%)**: Failed/attempted conversations with similar goals
3. **Category C (15%)**: Base AI personality with effective communication patterns
4. Communication profiles are blended with these weights
5. Response generator uses synthesized profile

### Response Generation Flow
1. Receive incoming message from target
2. Analyze conversation for disinterest indicators
3. Fetch relevant historical chat examples
4. Build enhanced prompt with:
   - Goal and context
   - Successful examples
   - Synthesized style profile
   - Conversation history
5. Generate response using OpenAI
6. Humanize response using Undetectable.ai
7. Calculate optimal response delay
8. Schedule response sending

### Disinterest Detection
Monitors:
- Response length (short responses = disinterest)
- Response timing (long delays = disinterest)
- Question rate (low questions = disinterest)
- One-word response rate
- Engagement decline over time

### Performance Optimizations
- **Incremental Ingestion**: Only processes new messages on subsequent runs (checks last message ID)
- **Incremental Categorization**: Only categorizes new messages (checks last categorization timestamp)
- **Database-Based Scoring**: Success scores stored in database (no re-scoring needed)
- **Smart Score Merging**: Balances message count ratio (60%) and engagement metrics (40%)
- **Smart Filtering**: Progressive AND filtering manages token limits automatically
- **Efficient Queries**: SQL-level filtering and deduplication

## Development

### Building
```bash
mvn clean package
```

### Running Tests
```bash
mvn test
```

### Code Style
The project follows standard Java conventions.

## License

[Your License Here]

## Contributing

[Contributing Guidelines]

## Support

For issues and questions, please open an issue on the repository.

## Disclaimer

ARIA is designed to assist with authentic communication. Users are responsible for ensuring their use complies with platform terms of service and applicable laws. The tool should enhance, not replace, genuine human connection.

