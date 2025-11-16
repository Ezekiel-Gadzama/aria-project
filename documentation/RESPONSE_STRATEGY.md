# Response Strategy - Weighted Synthesis (70/15/15)

This document explains the weighted response synthesis algorithm used by ARIA to blend communication styles from successful, failed, and base AI personality sources.

**Related Documentation**: [Analysis Services](ANALYSIS_SERVICES.md) | [Automated Conversation Management](AUTOMATED_CONVERSATION.md)

## Table of Contents

1. [Overview](#overview)
2. [Weighted Synthesis Algorithm](#weighted-synthesis-algorithm)
3. [Category A: Successful Conversations (70%)](#category-a-successful-conversations-70)
4. [Category B: Failed Conversations (15%)](#category-b-failed-conversations-15)
5. [Category C: Base AI Personality (15%)](#category-c-base-ai-personality-15)
6. [Profile Blending](#profile-blending)
7. [Implementation](#implementation)

## Overview

ARIA uses a **70%/15%/15% weighted response synthesis** algorithm to create personalized communication profiles:

- **70%**: Successful conversations (Category A) - where goals were achieved
- **15%**: Failed/attempted conversations (Category B) - where goals weren't achieved
- **15%**: Base AI personality (Category C) - effective communication patterns

**Location**: `src/main/java/com/aria/core/strategy/WeightedResponseSynthesis.java` (lines 1-235)

## Weighted Synthesis Algorithm

### Core Principle

Blend communication profiles from three sources with different weights to create an optimal personalized profile.

### Weights

**Reference**: `WeightedResponseSynthesis.java` lines 17-19

```java
private static final double WEIGHT_SUCCESSFUL = 0.70;  // 70%
private static final double WEIGHT_FAILED = 0.15;      // 15%
private static final double WEIGHT_BASE = 0.15;        // 15%
```

### Success Thresholds

**Reference**: `WeightedResponseSynthesis.java` lines 40-41

```java
double successThreshold = 0.7;  // 70% threshold for successful conversations
double failedThreshold = 0.3;   // Below 30% for failed conversations
```

## Category A: Successful Conversations (70%)

### Definition

Conversations where the goal was achieved with a success score ≥ 70%.

### Selection

**Reference**: `WeightedResponseSynthesis.java` lines 44-53

```java
for (Map.Entry<String, List<Message>> entry : allChats.entrySet()) {
    double successScore = successScorer.calculateSuccessScore(entry.getValue(), goalType);
    
    if (successScore >= successThreshold) {  // ≥ 70%
        categoryA.put(entry.getKey(), entry.getValue());
    }
}
```

### Weight

**70%** - Highest weight, heavily influences final profile.

### Profile Extraction

**Reference**: `WeightedResponseSynthesis.java` lines 56-58

```java
List<ChatProfile> successfulProfiles = categoryA.values().stream()
    .map(messages -> styleExtractor.extractStyleProfile(messages))
    .collect(Collectors.toList());
```

## Category B: Failed Conversations (15%)

### Definition

Conversations where the goal wasn't achieved with a success score < 30%.

### Selection

**Reference**: `WeightedResponseSynthesis.java` lines 49-51

```java
else if (successScore < failedThreshold) {  // < 30%
    categoryB.put(entry.getKey(), entry.getValue());
}
```

### Weight

**15%** - Lower weight, used to avoid patterns that didn't work.

### Profile Extraction

**Reference**: `WeightedResponseSynthesis.java` lines 60-62

```java
List<ChatProfile> failedProfiles = categoryB.values().stream()
    .map(messages -> styleExtractor.extractStyleProfile(messages))
    .collect(Collectors.toList());
```

## Category C: Base AI Personality (15%)

### Definition

Base AI personality with effective communication patterns. Used when no historical data is available.

### Creation

**Reference**: `WeightedResponseSynthesis.java` lines 202-213

```java
private ChatProfile createBaseProfile() {
    ChatProfile base = new ChatProfile();
    base.setHumorLevel(0.4);          // Moderate humor
    base.setFormalityLevel(0.5);      // Balanced formality
    base.setEmpathyLevel(0.7);        // High empathy
    base.setResponseTimeAverage(120.0); // 2 minutes average
    base.setMessageLengthAverage(25.0); // Average message length
    base.setQuestionRate(0.3);        // 30% questions
    base.setEngagementLevel(0.6);     // Moderate-high engagement
    base.setPreferredOpening("Hey! How are you doing?");
    return base;
}
```

### Weight

**15%** - Always included to ensure balanced communication.

## Profile Blending

### Algorithm

**Reference**: `WeightedResponseSynthesis.java` lines 75-153

```java
private ChatProfile blendProfiles(List<ChatProfile> successful, 
                                 List<ChatProfile> failed, 
                                 ChatProfile base) {
    // 1. Average profiles in each category
    ChatProfile avgSuccessful = averageProfiles(successful);
    ChatProfile avgFailed = averageProfiles(failed);
    
    // 2. Weighted blending: 70% successful + 15% failed + 15% base
    ChatProfile blended = new ChatProfile();
    blended.setHumorLevel(
        avgSuccessful.getHumorLevel() * WEIGHT_SUCCESSFUL +
        avgFailed.getHumorLevel() * WEIGHT_FAILED +
        base.getHumorLevel() * WEIGHT_BASE
    );
    // ... repeat for all fields
}
```

### Averaging Profiles

**Reference**: `WeightedResponseSynthesis.java` lines 158-197

```java
private ChatProfile averageProfiles(List<ChatProfile> profiles) {
    // Calculate averages for numeric fields
    double humorSum = profiles.stream().mapToDouble(ChatProfile::getHumorLevel).sum();
    double formalitySum = profiles.stream().mapToDouble(ChatProfile::getFormalityLevel).sum();
    // ... repeat for all fields
    
    // Calculate average
    averaged.setHumorLevel(humorSum / count);
    // ... repeat for all fields
    
    // Use most common preferred opening
    String preferredOpening = profiles.stream()
        .map(ChatProfile::getPreferredOpening)
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
        .entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse("Hi there!");
    
    return averaged;
}
```

### Fallback Logic

**Reference**: `WeightedResponseSynthesis.java` lines 135-149

If no successful chats available:
- Blend failed + base (50/50)
- If no failed chats either, use base only

## Implementation

### Main Method

**Reference**: `WeightedResponseSynthesis.java` lines 35-70

```java
public ChatProfile synthesizeProfile(Map<String, List<Message>> allChats, String goalType) {
    // 1. Categorize chats into A, B, and prepare C
    // 2. Extract profiles from each category
    // 3. Blend profiles with weights
    // 4. Return synthesized profile
}
```

### Usage

**Reference**: `AutomatedConversationManager.java` lines 89-91

```java
ChatProfile synthesizedProfile = synthesisEngine.synthesizeProfile(
    categorizedChats, goal.getDesiredOutcome());
responseGenerator.setStyleProfile(synthesizedProfile);
```

## Example Calculation

### Input

- **Category A** (70%): 2 successful chats
  - Chat 1: humor=0.8, formality=0.3, empathy=0.9
  - Chat 2: humor=0.7, formality=0.4, empathy=0.8
- **Category B** (15%): 1 failed chat
  - Chat 3: humor=0.2, formality=0.9, empathy=0.3
- **Category C** (15%): Base profile
  - Base: humor=0.4, formality=0.5, empathy=0.7

### Calculation

1. **Average Category A**:
   - humor = (0.8 + 0.7) / 2 = 0.75
   - formality = (0.3 + 0.4) / 2 = 0.35
   - empathy = (0.9 + 0.8) / 2 = 0.85

2. **Blended**:
   - humor = 0.75 × 0.70 + 0.2 × 0.15 + 0.4 × 0.15 = **0.615**
   - formality = 0.35 × 0.70 + 0.9 × 0.15 + 0.5 × 0.15 = **0.47**
   - empathy = 0.85 × 0.70 + 0.3 × 0.15 + 0.7 × 0.15 = **0.73**

### Result

Final profile prioritizes successful patterns (70%) while avoiding failed patterns (15%) and maintaining base personality (15%).

## Integration Flow

```
Load historical chats
    ↓
Calculate success scores
    ↓
Categorize into A/B/C
    ↓
Extract style profiles
    ↓
Average profiles in each category
    ↓
Blend with 70/15/15 weights
    ↓
Synthesized profile ready
```

**Reference**: `AutomatedConversationManager.java` lines 70-95

---

**Next**: [Analysis Services](ANALYSIS_SERVICES.md) | [Automated Conversation Management](AUTOMATED_CONVERSATION.md)

