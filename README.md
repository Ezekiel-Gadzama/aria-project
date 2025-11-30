# ARIA - Automated Relationship & Interaction Assistant

ARIA is a full-stack web application that automates and optimizes user-initiated conversations on social platforms (starting with Telegram) with the goal of achieving user-defined outcomes (e.g., dates, investments, sponsorships). The core innovation is an AI that personalizes its communication strategy by prioritizing the user's own successful historical chats.

## Features

### Core Functionality
- **Hierarchical Target Management**: Create Target Users (platform-agnostic person info) with multiple SubTarget Users (platform-specific instances)
- **Cross-Platform Context**: Toggle to aggregate chat history across all platforms for a Target User, enabling AI to understand the same person across different platforms
- **Goal-Based Conversation Management**: Define conversation goals (dating, investment, sponsorship, etc.) and ARIA adapts accordingly
- **Historical Chat Analysis**: Analyzes years of chat history to learn your successful communication patterns
- **Weighted Response Synthesis (70%/15%/15%)**:
  - 70% weight on successful conversations (Category A) - dialogs with success score >= 0.7
  - 15% weight on failed/attempted conversations (Category B) - dialogs with success score < 0.3
  - 15% weight on AI improvement examples (Category C) - enhanced successful examples
- **AI Suggestion System**: Generate contextual reply suggestions using 70/15/15 strategy with OpenAI Responses API
  - Uses ALL conversation history (not just last 50 messages)
  - Learns from similar conversations in same categories
  - Maintains conversation state across requests
  - Gradually progresses toward goals
- **AI Categorization**: Uses OpenAI to categorize chats into multiple goal-relevant categories with success scores
- **Incremental Categorization**: Only processes new messages on subsequent runs (optimizes API usage)
- **Smart Score Merging**: Intelligently merges old and new scores based on message count ratio and engagement metrics
- **Contextual Success Scoring**: Understands circumstantial rejections vs. approach failures (e.g., "I have a boyfriend" = high score)
- **Humanized Responses**: Integrates with Undetectable.ai to make AI responses undetectable
- **Target Groups & Channels**: Manage group chats and channels with hierarchical structure (Target Group/Channel → SubTarget Group/Channel)
- **Message Pinning**: Pin important messages in conversations (syncs with Telegram)
- **Media Support**: Send, receive, edit, and delete media messages (images, videos, files, audio)
- **Real-time Conversation UI**: Modern React-based conversation interface with message polling, optimistic updates, and media previews

### Advanced Features
- **Disinterest Detection**: Monitors engagement indicators and alerts when conversation shows signs of disinterest
- **Response Timing Analysis**: Analyzes optimal response delays based on engagement and historical patterns
- **Automated Conversation Management**: Handles sending/receiving messages with intelligent timing
- **Conversation Summarization**: Generates comprehensive summaries after conversations
- **Quiz System**: Tests user's memory of key conversation details before meetings/dates
- **Analysis Dashboard**: View conversation statistics, engagement metrics, and top targets with platform filtering

## Architecture

### Technology Stack
- **Backend**: Java 17+ with Spring Boot 3.2.0
- **Frontend**: React 18+ with React Router
- **Build Tool**: Maven (backend), npm (frontend)
- **Database**: PostgreSQL 15+
- **AI/NLP**: OpenAI API
- **Humanization**: Undetectable.ai API
- **Platform Integration**: Telethon (Python) for Telegram
- **Deployment**: Docker & Docker Compose

### Project Structure
```
Aria/
├── src/main/java/com/aria/
│   ├── api/                  # REST API controllers
│   │   ├── controller/      # API endpoints (TargetController, ConversationController, etc.)
│   │   └── dto/             # Data Transfer Objects
│   ├── ai/                   # AI clients (OpenAI, Undetectable.ai)
│   ├── analysis/             # Chat analysis (categorization, disinterest detection)
│   ├── core/                 # Core orchestration and models
│   │   ├── model/            # Data models (TargetUser, SubTargetUser, etc.)
│   │   └── strategy/         # Response strategies (70/15/15 synthesis)
│   ├── platform/             # Platform connectors (Telegram, WhatsApp, Instagram)
│   ├── service/              # Business logic services
│   └── storage/              # Database management
├── frontend/
│   ├── src/
│   │   ├── components/       # React components
│   │   │   ├── TargetManagement.js
│   │   │   ├── EditTargetUser.js
│   │   │   ├── SubTargetUsersView.js
│   │   │   ├── ConversationView.js
│   │   │   ├── AnalysisDashboard.js
│   │   │   └── ...
│   │   └── services/
│   │       └── api.js        # API client
│   └── package.json
└── scripts/telethon/         # Python scripts for Telegram integration
```

## Setup

### Prerequisites
- Java 17 or later
- Maven 3.6+
- Node.js 18+ and npm (for frontend development)
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
   Create a `.env` file or set environment variables:
   ```env
   OPENAI_API_KEY=your_openai_api_key
   UNDETECTABLE_AI_API_KEY=your_undetectable_ai_api_key
   DATABASE_URL=jdbc:postgresql://postgres:5432/aria
   DATABASE_USER=postgres
   DATABASE_PASSWORD=your_password
   ```

3. **Start services**
   ```bash
   docker-compose up -d --build
   ```

4. **Access the application**
   - Frontend UI: http://localhost:8080
   - API: http://localhost:8080/api

### Manual Setup

#### Backend Setup

1. **Install PostgreSQL**
   ```bash
   # Create database
   createdb aria
   ```

2. **Update configuration**
   Edit `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/aria
   spring.datasource.username=postgres
   spring.datasource.password=your_password
   
   openai.api.key=your_openai_api_key
   undetectable.ai.api.key=your_undetectable_ai_api_key
   ```

3. **Install Python dependencies**
   ```bash
   cd scripts/telethon
   pip install -r requirements.txt
   ```

4. **Build and run the backend**
   ```bash
   mvn clean package
   mvn spring-boot:run
   ```

#### Frontend Setup

1. **Install dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Run the frontend**
   ```bash
   npm start
   ```
   The frontend will run on http://localhost:3000 (with hot-reload)

### Development Setup (Recommended)

For development, run frontend and backend separately for hot-reload:

**Terminal 1 - Backend:**
```bash
mvn spring-boot:run
```

**Terminal 2 - Frontend:**
```bash
cd frontend
npm start
```

Access:
- Frontend: http://localhost:3000 (with hot-reload)
- API: http://localhost:8080/api

## Configuration

### Database Connection
Update `src/main/resources/application.properties` or use environment variables:
- `DATABASE_URL`
- `DATABASE_USER`
- `DATABASE_PASSWORD`

### API Keys
Set in `application.properties` or environment variables:
- `OPENAI_API_KEY`: Your OpenAI API key
- `UNDETECTABLE_AI_API_KEY`: Your Undetectable.ai API key (optional)

### Telegram Configuration
1. Get API credentials from [https://my.telegram.org/apps](https://my.telegram.org/apps)
2. Register platform account through the web UI (Platform Registration page)

## Usage

### 1. User Registration
- Register your user profile through the web UI
- Add your phone number and personal information

### 2. Connect Platform Account
- Go to Platform Registration page
- Enter your Telegram API credentials
- Complete authentication (OTP verification)

### 3. Import Chat History
- ARIA will automatically ingest your Telegram chat history
- The system categorizes chats based on goals using OpenAI
- Chats are stored in the database with proper indexing

### 4. Create Target User
- Create a Target User with basic information:
  - Name, Bio, Desired Outcome, Where/How You Met, Important Details
- Add one or more SubTarget Users (platform-specific instances):
  - Name (platform-specific), Username, Platform, Account, Advanced Communication Settings
- Enable Cross-Platform Context toggle to aggregate chat history across all platforms

### 5. Start Conversation
- Click on a SubTarget User to start a conversation
- ARIA analyzes your historical chats relevant to the goal
- Synthesizes a communication profile using 70/15/15 weighting
- Generates and sends personalized responses
- Monitors engagement and adjusts timing

### 6. Manage Conversations
- Send, edit, delete, and pin messages
- Send media (images, videos, files, audio)
- Reply to messages
- View conversation history with cross-platform context (if enabled)
- View analysis dashboard with platform filtering

## Database Schema

### Key Tables
- `users`: User profiles
- `target_users`: Parent Target Users (platform-agnostic person info)
- `subtarget_users`: Child SubTarget Users (platform-specific instances)
- `target_groups`: Parent Target Groups
- `subtarget_groups`: Child SubTarget Groups
- `target_channels`: Parent Target Channels
- `subtarget_channels`: Child SubTarget Channels
- `dialogs`: Chat conversations
- `messages`: Individual messages (with `pinned` and `message_link` fields)
- `conversations`: Active conversations (with `subtarget_user_id`)
- `goals`: Conversation goals
- `chat_goals`: Maps chats to goals/categories
- `conversation_states`: Active conversation states
- `style_profiles`: Analyzed communication styles
- `disinterest_logs`: Disinterest detection records
- `conversation_summaries`: Conversation summaries
- `quiz_questions`: Generated quiz questions
- `target_user_responses`: OpenAI Responses API state storage (response IDs per target/subtarget)

All tables have optimal indexes for fast queries.

## REST API

The application exposes a REST API at `/api`:

### Main Endpoints
- `GET /api/targets` - Get all target users
- `POST /api/targets` - Create target user
- `PUT /api/targets/{id}` - Update target user
- `DELETE /api/targets/{id}` - Delete target user
- `POST /api/targets/{id}/toggle-cross-platform-context` - Toggle cross-platform context
- `GET /api/conversations/messages` - Get messages for a conversation
- `POST /api/conversations/initialize` - Initialize conversation
- `POST /api/conversations/respond` - Send message
- `POST /api/conversations/edit` - Edit message
- `POST /api/conversations/editLast` - Edit last message
- `DELETE /api/conversations/message` - Delete message
- `POST /api/conversations/pin` - Pin/unpin message
- `POST /api/conversations/sendMedia` - Send media
- `GET /api/conversations/suggest` - Get AI suggestion for reply (70/15/15 strategy)
- `GET /api/targets/analysis` - Get analysis data

See the API documentation in `documentation/` for complete endpoint details.

## How It Works

### Hierarchical Target Model

**Target User (Parent)**:
- Platform-agnostic person information
- Fields: Name, Bio, Desired Outcome, Where/How You Met, Important Details
- Can have multiple SubTarget Users

**SubTarget User (Child)**:
- Platform-specific instance of a Target User
- Fields: Name (platform-specific), Username, Platform, Account, Advanced Communication Settings
- Conversations are started at the SubTarget User level

### Cross-Platform Context

When enabled for a Target User:
- AI aggregates chat history from ALL SubTarget Users of that Target User
- AI understands it's the same person across different platforms
- Generates more informed and context-aware suggestions

When disabled:
- AI uses only the current SubTarget User's chat history

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

### AI Suggestion Generation Flow
1. User clicks "AI" button in conversation
2. Check if response ID exists in database (first request vs subsequent)
3. **First Request**:
   - Load ALL conversation messages (not just last 50)
   - Get categories for current conversation
   - Get reference dialogs in same categories
   - Separate into 70% successful, 15% failed, 15% AI improvement
   - Build comprehensive context with:
     - Target user info (name, bio, desired outcome, meeting context, important details)
     - SubTarget user info and communication style profile
     - Full conversation history
     - Reference examples (70/15/15)
     - AI instructions for goal progression
   - Send full context to OpenAI Responses API with `store=true`
   - Save response ID to database
4. **Subsequent Requests**:
   - Load response ID from database
   - Get last message from target user
   - Send only new message to OpenAI Responses API with `previous_response_id`
   - OpenAI remembers all previous context
   - Update response ID in database
5. Return generated suggestion to frontend
6. User can copy suggestion to message field and edit before sending

### Automated Response Generation Flow
1. Receive incoming message from target
2. Check cross-platform context toggle
3. Aggregate chat history (all SubTarget Users if enabled, or just current if disabled)
4. Analyze conversation for disinterest indicators
5. Fetch relevant historical chat examples
6. Build enhanced prompt with:
   - Goal and context
   - Successful examples
   - Synthesized style profile
   - Conversation history (cross-platform if enabled)
7. Generate response using OpenAI
8. Humanize response using Undetectable.ai
9. Calculate optimal response delay
10. Schedule response sending

### Target Groups & Channels

- **Target Groups/Channels**: Parent entities for communal spaces
- **SubTarget Groups/Channels**: Platform-specific instances
- Only explicitly defined groups/channels are ingested
- Messages from groups/channels include `message_link` for reference
- Group/channel data is used for trend analysis, admin oversight, and knowledge base
- **Never used for 1-on-1 reply generation**

### Performance Optimizations
- **Incremental Ingestion**: Only processes new messages on subsequent runs (checks last message ID)
- **Incremental Categorization**: Only categorizes new messages (checks last categorization timestamp)
- **Database-Based Scoring**: Success scores stored in database (no re-scoring needed)
- **Smart Score Merging**: Balances message count ratio (60%) and engagement metrics (40%)
- **Smart Filtering**: Progressive AND filtering manages token limits automatically
- **Efficient Queries**: SQL-level filtering and deduplication
- **Message Polling**: Efficient polling for new messages
- **Optimistic UI Updates**: Immediate UI feedback with backend confirmation

## Development

### Building

**Backend:**
```bash
mvn clean package
```

**Frontend:**
```bash
cd frontend
npm run build
```

### Running Tests
```bash
mvn test
```

### Code Style
The project follows standard Java and JavaScript/React conventions.

## Documentation

Comprehensive documentation is available in the `documentation/` folder:
- [Architecture Overview](documentation/ARCHITECTURE.md)
- [Core Models](documentation/CORE_MODELS.md)
- [AI Services](documentation/AI_SERVICES.md)
- [Response Strategy](documentation/RESPONSE_STRATEGY.md)
- [Categorization Logic](documentation/CATEGORIZATION_LOGIC.md)
- [Setup Guide](SETUP.md)

## License

[Your License Here]

## Contributing

[Contributing Guidelines]

## Support

For issues and questions, please open an issue on the repository.

## Disclaimer

ARIA is designed to assist with authentic communication. Users are responsible for ensuring their use complies with platform terms of service and applicable laws. The tool should enhance, not replace, genuine human connection.
