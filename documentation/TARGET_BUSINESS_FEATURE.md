# Target Business Feature

## Overview

The Target Business feature allows managers to create business contexts that aggregate conversations from multiple channels, groups, and private chats. This enables AI-powered insights and suggested replies based on all conversations within a business context.

## Key Features

1. **Target Business Management**
   - Create and manage Target Businesses (e.g., "Company Projects", "Team Communications")
   - Each business can have multiple sub-targets (channels, groups, private chats)

2. **Business Sub-Targets**
   - **Channels**: Public or private channels (e.g., Telegram channels)
   - **Groups**: Group chats (e.g., "PILOT - WIREPAS - VELAVU")
   - **Private Chats**: Direct messages with employees

3. **Context-Aware AI Suggestions**
   - When in conversation with a business sub-target, AI uses messages from ALL sub-targets in that business
   - Suggested replies are informed by patterns across channels, groups, and private chats

4. **Business Bot Chat**
   - Built-in bot within each Target Business
   - Managers can ask questions about tasks, projects, and conversations
   - Bot summarizes information from all linked sub-targets
   - Example: "What has been done in the velavu task?" → Bot responds with summary from "PILOT - WIREPAS - VELAVU" group chat

5. **Future Extensibility**
   - Ready for integration with Jira, company repositories, and other tools
   - Bot can answer questions across multiple platforms and tools

## Database Schema

### `target_businesses`
- `id`: Primary key
- `user_id`: Owner of the business
- `name`: Business name
- `description`: Optional description
- `created_at`, `updated_at`: Timestamps

### `business_subtargets`
- `id`: Primary key
- `business_id`: Parent business ID
- `name`: Sub-target name
- `type`: CHANNEL, GROUP, or PRIVATE_CHAT
- `platform`: Platform enum (TELEGRAM, etc.)
- `platform_account_id`: Reference to platform_accounts
- `dialog_id`: Reference to dialogs table
- `platform_id`: Platform-specific ID
- `username`: Platform username
- `description`: Optional description

### `business_bot_conversations`
- `id`: Primary key
- `business_id`: Target business ID
- `user_id`: User having conversation with bot
- `openai_response_id`: OpenAI Responses API state
- `last_message_id`: Last processed message ID
- `created_at`, `updated_at`: Timestamps

## API Endpoints

### Business Management
- `GET /api/businesses?userId=...` - List all businesses for a user
- `GET /api/businesses/{id}?userId=...` - Get business details
- `POST /api/businesses?userId=...` - Create new business
- `PUT /api/businesses/{id}?userId=...` - Update business
- `DELETE /api/businesses/{id}?userId=...` - Delete business

### Business Sub-Targets
- `GET /api/businesses/{businessId}/subtargets?userId=...` - List sub-targets
- `GET /api/businesses/{businessId}/subtargets/{id}?userId=...` - Get sub-target details
- `POST /api/businesses/{businessId}/subtargets?userId=...` - Add sub-target
- `PUT /api/businesses/{businessId}/subtargets/{id}?userId=...` - Update sub-target
- `DELETE /api/businesses/{businessId}/subtargets/{id}?userId=...` - Remove sub-target

### Business Bot
- `POST /api/businesses/{businessId}/bot/chat?userId=...` - Send message to bot
- `GET /api/businesses/{businessId}/bot/history?userId=...` - Get bot conversation history

## Context Building

When generating AI suggestions for a business sub-target conversation:

1. **Aggregate Messages**: Collect all messages from ALL sub-targets in the business
2. **Categorize**: Use existing categorization logic to identify relevant conversations
3. **Prioritize**: 
   - Messages from the same category
   - Messages from the same business sub-target
   - All other business messages
4. **Build Context**: Similar to 70/15/15 strategy but scoped to business context

## Bot Chat Functionality

The business bot uses a specialized context builder that:

1. **Understands Business Context**: Knows all sub-targets and their conversations
2. **Answers Questions**: Can answer questions like:
   - "What has been done in the velavu task?"
   - "What did the team discuss about the project?"
   - "Who is working on task X?"
3. **Summarizes**: Provides summaries of conversations from specific sub-targets
4. **Cross-Reference**: Can reference information across multiple sub-targets

## UI Components

1. **TargetManagement.js** - Updated to show tabs: "Target Users" and "Target Business"
2. **BusinessManagement.js** - New component for managing Target Businesses
3. **BusinessBotChat.js** - New component for bot chat interface

## Future Enhancements

- Integration with Jira for task tracking
- Integration with company repositories (GitHub, GitLab)
- Integration with other business tools (Slack, Microsoft Teams)
- Advanced analytics across business contexts
- Automated task tracking and reporting

