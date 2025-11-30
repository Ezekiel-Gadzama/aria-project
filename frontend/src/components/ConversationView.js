import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { targetApi, conversationApi, platformApi } from '../services/api';
import './ConversationView.css';

// Utility function to detect and render links in text
const renderTextWithLinks = (text) => {
  if (!text) return null;
  
  // URL regex pattern
  const urlPattern = /(https?:\/\/[^\s]+|www\.[^\s]+|[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:\/[^\s]*)?)/g;
  const parts = [];
  let lastIndex = 0;
  let match;
  
  while ((match = urlPattern.exec(text)) !== null) {
    // Add text before the URL
    if (match.index > lastIndex) {
      parts.push(text.substring(lastIndex, match.index));
    }
    
    // Add the URL as a link
    let url = match[0];
    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      url = 'https://' + url;
    }
    
    parts.push(
      <a
        key={match.index}
        href={url}
        target="_blank"
        rel="noopener noreferrer"
        style={{ color: 'inherit', textDecoration: 'underline' }}
        onClick={(e) => e.stopPropagation()}
      >
        {match[0]}
      </a>
    );
    
    lastIndex = urlPattern.lastIndex;
  }
  
  // Add remaining text
  if (lastIndex < text.length) {
    parts.push(text.substring(lastIndex));
  }
  
  return parts.length > 0 ? parts : text;
};

// Utility function to check if text is a URL (and should not be treated as media)
const isUrl = (text) => {
  if (!text) return false;
  // Check if it's an actual URL (http/https/www) and not a relative path or API endpoint
  const urlPattern = /^(https?:\/\/|www\.)[^\s]+/;
  const isAbsoluteUrl = urlPattern.test(text.trim());
  // Exclude API endpoints and relative paths
  const isApiEndpoint = text.includes('/api/') || text.startsWith('/');
  return isAbsoluteUrl && !isApiEndpoint;
};

function ConversationView({ userId = 1 }) {
  const { targetId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [subtargetUserId, setSubtargetUserId] = useState(null);
  
  // Get subtargetUserId from query params (re-read when location changes)
  // Also persist it in sessionStorage as a fallback
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const subtargetId = params.get('subtargetUserId');
    if (subtargetId) {
      const parsedId = parseInt(subtargetId);
      setSubtargetUserId(parsedId);
      // Persist in sessionStorage as fallback
      sessionStorage.setItem(`subtargetUserId_${targetId}`, parsedId.toString());
    } else {
      // Try to restore from sessionStorage if not in URL
      const storedId = sessionStorage.getItem(`subtargetUserId_${targetId}`);
      if (storedId) {
        setSubtargetUserId(parseInt(storedId));
        // Update URL to include it
        const newParams = new URLSearchParams(location.search);
        newParams.set('subtargetUserId', storedId);
        navigate(`/conversations/${targetId}?${newParams.toString()}`, { replace: true });
      }
    }
  }, [location.search, targetId, navigate]);
  const [target, setTarget] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [goalData, setGoalData] = useState({
    context: '',          // optional
    desiredOutcome: '',   // required
    meetingContext: '',   // optional
  });
  const [platformAccounts, setPlatformAccounts] = useState([]);
  const [selectedAccountIds, setSelectedAccountIds] = useState([]);
  const [conversationInitialized, setConversationInitialized] = useState(false);
  const [messages, setMessages] = useState([]);
  const [newMessage, setNewMessage] = useState('');
  const [pendingMedia, setPendingMedia] = useState(null); // { file, preview }
  const [targetOnline, setTargetOnline] = useState(false); // Online status of target user
  const [lastActive, setLastActive] = useState(null); // Last active time
  const [replyingTo, setReplyingTo] = useState(null); // Message being replied to { messageId, text, fromUser }
  const [deleteModal, setDeleteModal] = useState(null); // { messageId, revoke: true/false } for delete confirmation
  const [recentlyEditedMessages, setRecentlyEditedMessages] = useState(new Set()); // Track recently edited message IDs to prevent polling from overwriting
  const [isOperationInProgress, setIsOperationInProgress] = useState(false); // Track if delete/edit is in progress to pause polling
  const [deletedMessageIds, setDeletedMessageIds] = useState(new Set()); // Track deleted message IDs to prevent them from being re-added
  const operationInProgressRef = useRef(false); // Ref to track operation status synchronously
  const pollForNewMessagesRef = useRef(null); // Ref to store polling function so we can call it after operations complete
  const messageInputRef = useRef(null); // Ref for message input field to focus when replying
  const messagesContainerRef = useRef(null); // Ref for messages container for scroll tracking
  const [showNewMessageNotification, setShowNewMessageNotification] = useState(false); // Show new message notification when scrolled up
  const [showScrollToBottom, setShowScrollToBottom] = useState(false); // Show scroll to bottom button
  const [selectedMessages, setSelectedMessages] = useState(new Set()); // Track selected message IDs for multi-select
  const [isMultiSelectMode, setIsMultiSelectMode] = useState(false); // Track if in multi-select mode
  const [contextMenu, setContextMenu] = useState(null); // { x, y, messageId, fromUser } for right-click context menu
  const [lastHighestMessageId, setLastHighestMessageId] = useState(0); // Track highest message ID to detect new messages
  const [mediaViewer, setMediaViewer] = useState(null); // { type: 'image'|'video'|'audio', url, fileName } for media viewer modal
  const [pinnedMessages, setPinnedMessages] = useState([]); // Array of pinned messages to display at top
  const [pinNotifications, setPinNotifications] = useState([]); // Array of pin notification messages { messageId, text, timestamp, pinnedBy }
  const [previousPinnedState, setPreviousPinnedState] = useState(new Map()); // Track previous pinned state to detect changes
  const [currentPinnedIndex, setCurrentPinnedIndex] = useState(0); // Index of pinned message to show at top (first one above viewport)
  const [aiSuggestion, setAiSuggestion] = useState(null); // AI-generated suggestion (single)
  const [aiSuggestions, setAiSuggestions] = useState(null); // AI-generated suggestions (multiple)
  const [showMultipleSuggestions, setShowMultipleSuggestions] = useState(false); // Checkbox state
  const [loadingSuggestion, setLoadingSuggestion] = useState(false); // Loading state for AI suggestion

  // Update current pinned index when pinned messages change
  useEffect(() => {
    if (pinnedMessages.length > 0) {
      // Ensure current index is within bounds
      if (currentPinnedIndex >= pinnedMessages.length || currentPinnedIndex < 0) {
        setCurrentPinnedIndex(0);
      }
    } else {
      setCurrentPinnedIndex(0);
    }
  }, [pinnedMessages.length, currentPinnedIndex]);

  useEffect(() => {
    loadTarget();
    loadPlatformAccounts();
    // Check if there is an active conversation; if yes, skip goal page
    (async () => {
      try {
        const resp = await conversationApi.isActive(targetId, userId);
        if (resp.data?.success && resp.data?.data === true) {
          setConversationInitialized(true);
        }
        // Always try to load messages (even if conversation not initialized, messages might exist)
        // Wait a bit for subtargetUserId to be set from query params
        setTimeout(() => {
          loadMessages().catch(err => console.error('Failed to load messages:', err));
        }, 100);
      } catch (e) {
        // Try to load messages anyway
        console.error('Failed to check active conversation:', e);
        setTimeout(() => {
          loadMessages().catch(err => console.error('Failed to load messages:', err));
        }, 100);
      }
    })();
  }, [targetId]);

  // Auto-scroll to bottom when new messages are added (especially when sending)
  useEffect(() => {
    // Only scroll if we're near the bottom (user hasn't scrolled up)
    const messagesContainer = messagesContainerRef.current;
    if (messagesContainer && messages.length > 0) {
      const isNearBottom = messagesContainer.scrollHeight - messagesContainer.scrollTop - messagesContainer.clientHeight < 300;
      if (isNearBottom) {
        // Small delay to ensure DOM has updated
        setTimeout(() => {
          if (messagesContainerRef.current) {
            messagesContainerRef.current.scrollTop = messagesContainerRef.current.scrollHeight;
          }
        }, 50);
      }
    }
  }, [messages.length]); // Trigger when message count changes (new message added)

  // Poll for online status when conversation is initialized
  useEffect(() => {
    if (!conversationInitialized || !target) return;
    
    const checkOnlineStatus = async () => {
      try {
        const response = await targetApi.checkOnlineStatus(targetId, userId, subtargetUserId);
        if (response.data?.success) {
          const data = response.data.data;
          // Backend returns "isOnline" not "online"
          setTargetOnline(data.isOnline === true || data.online === true);
          setLastActive(data.lastActive || null);
        }
      } catch (err) {
        // Silently fail - online status is not critical
        console.error('Failed to check online status:', err);
      }
    };

    // Check immediately
    checkOnlineStatus();
    
    // Then check every 5 seconds
    const interval = setInterval(checkOnlineStatus, 5000);
    
    return () => clearInterval(interval);
  }, [conversationInitialized, target, targetId, userId, subtargetUserId]);

  // Poll for new messages and trigger priority ingestion every 5 seconds
  useEffect(() => {
    if (!conversationInitialized || !targetId) return; // Don't poll if conversation not initialized
    
    const pollForNewMessages = async () => {
      // IMMEDIATELY stop if an operation (delete/edit) is in progress (check ref for synchronous check)
      if (operationInProgressRef.current || isOperationInProgress) {
        return;
      }
      
      try {
        // Trigger priority ingestion every 5 seconds to sync with Telegram
        // This will check for new messages and delete messages that no longer exist in Telegram
        // Only trigger if not already running (backend will check and skip if already running)
        // BUT: Skip priority ingestion if operation is in progress
        // IMPORTANT: Trigger ingestion and fetch messages immediately - cache is invalidated by backend
        if (!operationInProgressRef.current && !isOperationInProgress) {
          // Trigger ingestion in background (non-blocking) - don't wait for it
          // Backend will invalidate cache after ingestion completes
          conversationApi.ingestTarget(targetId, userId, subtargetUserId).catch(err => {
            // Ignore ingestion errors - it's a background process
            // The backend will skip if ingestion is already running
            if (err.response?.status !== 200) {
              console.warn('Priority ingestion failed:', err);
            }
          });
          // Don't wait - fetch messages immediately
          // Cache will be invalidated by backend after ingestion, so next poll will get fresh data
        }
        
        // Final check before fetching messages (use ref for synchronous check)
        if (operationInProgressRef.current) {
          return; // Stop immediately if operation started
        }
        
        // Then fetch messages from database (cache will be invalidated by priority ingestion)
        const resp = await conversationApi.getMessages(targetId, userId, 50, subtargetUserId);
        
        // Check again after API call (use ref for synchronous check)
        if (operationInProgressRef.current) {
          return; // Stop immediately if operation started during API call
        }
        if (resp.data?.success) {
          const rows = resp.data.data || [];
          // Always update messages (database is the source of truth after priority ingestion)
          // The database has already been updated by priority ingestion with deletions and edits
          const loadedMessages = rows.map((r) => ({
            text: r.text || '',
            fromUser: !!r.fromUser,
            timestamp: r.timestamp ? new Date(r.timestamp) : new Date(),
            mediaUrl: r.mediaDownloadUrl || null,
            messageId: r.messageId,
            hasMedia: r.hasMedia || false,
            fileName: r.fileName || null,
            mimeType: r.mimeType || null,
            edited: r.edited || false,
            referenceId: r.referenceId || null,
            pinned: r.pinned || false, // Include pinned status in polling
          }));
          
          // Always sync messages to reflect deletions and updates from database
          // Database is the source of truth after priority ingestion has run
          setMessages(prev => {
            // CRITICAL: Capture deletedMessageIds at the start to use latest value
            // This ensures we always use the most current deleted message IDs
            const currentDeletedIds = deletedMessageIds;
            
            // CRITICAL: Filter out deleted messages from prev FIRST before any processing
            // This must happen BEFORE checking operationInProgress to ensure deleted messages are always removed
            const prevFiltered = prev.filter(msg => !currentDeletedIds.has(msg.messageId));
            
            // Check if operation started during message processing (use ref for synchronous check)
            // But still filter deleted messages even if operation is in progress
            if (operationInProgressRef.current) {
              // Return filtered prev (with deleted messages removed) even if operation is in progress
              // This ensures deleted messages don't reappear
              return prevFiltered;
            }
            
            // Create map for quick lookup (using filtered prev)
            const prevMapFiltered = new Map(prevFiltered.map(m => [m.messageId, m]));
            
            // Track if there are changes (using filtered prev)
            const prevIds = new Set(prevFiltered.map(m => m.messageId));
            
            // CRITICAL: Filter out deleted messages from loaded messages
            // This ensures deleted messages never appear in the UI, even if they come back from API
            // Use the captured currentDeletedIds to ensure we're using the latest deleted IDs
            const filteredMessages = loadedMessages.filter(newMsg => {
              // Always exclude deleted messages - use captured set
              if (currentDeletedIds.has(newMsg.messageId)) {
                return false;
              }
              return true;
            });
            
            const newIds = new Set(filteredMessages.map(m => m.messageId));
            const prevHighest = prevFiltered.length > 0 ? Math.max(...prevFiltered.map(m => m.messageId || 0)) : 0;
            const newHighest = filteredMessages.length > 0 ? Math.max(...filteredMessages.map(m => m.messageId || 0)) : 0;
            
            // Always use loaded messages from database (source of truth)
            // But preserve recently edited messages and pending messages that might not be committed yet
            // Note: deleted messages are already filtered out above
            // Use prevMapFiltered (which excludes deleted messages) for lookups
            const syncedMessages = filteredMessages
              .map(newMsg => {
                const prevMsg = prevMapFiltered.get(newMsg.messageId);
                const isRecentlyEdited = recentlyEditedMessages.has(newMsg.messageId);
                
                // CRITICAL: If message was recently edited in the app, ALWAYS preserve the optimistic version
                // This prevents polling from overwriting app edits before database is updated
                // Check this FIRST before any other logic - this is the most important check
                if (isRecentlyEdited && prevMsg) {
                  // Always preserve the optimistic edit, even if database has different text
                  // The database might not be updated yet, so we trust the optimistic version
                  // CRITICAL: Don't let polling overwrite app edits - preserve the edited text
                  return {
                    ...prevMsg,
                    // Keep the edited text from optimistic update
                    text: prevMsg.text,
                    edited: true,
                    isPending: prevMsg.isPending || false,
                  };
                }
                
                // If message is pending (being sent/edited), but now found in database, remove pending flag
                if (prevMsg && prevMsg.isPending) {
                  // Message was pending but now found in database - use database version and remove pending flag
                  return {
                    ...newMsg,
                    isPending: false, // No longer pending since it's in the database
                  };
                }
                
                // ALWAYS show Telegram edits immediately - don't preserve them
                // If message is marked as edited in DB, it was edited on Telegram
                // BUT: Only if it wasn't recently edited by the app (checked above)
                if (newMsg.edited && !isRecentlyEdited) {
                  // Message was edited on Telegram - always show the updated text
                  return newMsg;
                }
                
                // For all other messages, use what's in the database
                return newMsg;
              });
            
            // Also preserve any pending messages that aren't in the database yet (newly sent messages)
            // BUT: Filter out any pending messages that were deleted
            // Use prevFiltered which already excludes deleted messages, but double-check with currentDeletedIds
            const pendingMessages = prevFiltered.filter(msg => 
              msg.isPending && 
              !newIds.has(msg.messageId) &&
              !currentDeletedIds.has(msg.messageId) // Double-check with captured set
            );
            const finalMessages = pendingMessages.length > 0 
              ? [...syncedMessages, ...pendingMessages]
              : syncedMessages;
            
            // FINAL SAFETY CHECK: Remove any deleted messages that might have slipped through
            // This is a last line of defense to ensure deleted messages never appear
            // Use captured currentDeletedIds to ensure we use the latest value
            const finalFilteredMessages = finalMessages.filter(msg => !currentDeletedIds.has(msg.messageId));
            
            // Check if messages are different (added, removed, or edited)
            // Use prevFiltered for all comparisons to ensure deleted messages don't affect the logic
            const hasNewMessages = newHighest > prevHighest;
            const hasRemovedMessages = prevIds.size > newIds.size;
            const hasDifferentLength = prevFiltered.length !== filteredMessages.length;
            
            // Track new messages for notification when scrolled up
            if (hasNewMessages && newHighest > lastHighestMessageId) {
              setLastHighestMessageId(newHighest);
              // Check if user is scrolled up (not near bottom)
              setTimeout(() => {
                const messagesContainer = messagesContainerRef.current;
                if (messagesContainer) {
                  const isNearBottom = messagesContainer.scrollHeight - messagesContainer.scrollTop - messagesContainer.clientHeight < 200;
                  if (!isNearBottom) {
                    setShowNewMessageNotification(true);
                  }
                }
              }, 100);
            }
            
            // Check if any existing message was edited (text changed)
            // Also check if message is marked as edited in DB (Telegram edit)
            // Use filteredMessages (deleted messages already excluded) and prevMapFiltered
            let hasEditedMessages = false;
            for (const newMsg of filteredMessages) {
              const prevMsg = prevMapFiltered.get(newMsg.messageId);
              const isRecentlyEdited = recentlyEditedMessages.has(newMsg.messageId);
              // Detect edits if text changed OR message is marked as edited in DB
              if (prevMsg) {
                if (newMsg.edited || (prevMsg.text !== newMsg.text && !isRecentlyEdited)) {
                  hasEditedMessages = true;
                  break;
                }
              }
            }
            
            // CRITICAL: Separate pinned messages from regular messages for tracking
            // BUT: Pinned messages should appear in BOTH the pinned section AND regular chat
            const pinned = finalFilteredMessages.filter(msg => msg.pinned).sort((a, b) => b.messageId - a.messageId);
            const unpinned = finalFilteredMessages.filter(msg => !msg.pinned);
            // Keep all messages (including pinned) for display in chat
            const allMessages = finalFilteredMessages;
            
            // Update pinned messages state - preserve existing pinned messages and merge with new ones
            setPinnedMessages(prevPinned => {
              // Always merge with existing pinned messages to preserve ones not in current batch
              const existingIds = new Set(prevPinned.map(pm => pm.messageId));
              const newPinnedIds = new Set(pinned.map(pm => pm.messageId));
              
              // Check for unpinned messages (were pinned before but not in new pinned list)
              // Only remove if the message is in the current batch and explicitly not pinned
              const messagesInBatch = new Set(finalFilteredMessages.map(m => m.messageId));
              const unpinnedMessages = prevPinned.filter(pm => 
                messagesInBatch.has(pm.messageId) && !newPinnedIds.has(pm.messageId)
              );
              
              // Remove unpinned messages from preserved list
              const preserved = prevPinned.filter(pm => 
                !messagesInBatch.has(pm.messageId) || newPinnedIds.has(pm.messageId)
              );
              
              // Combine preserved and new pinned messages, avoiding duplicates
              const merged = [...pinned];
              preserved.forEach(pm => {
                if (!newPinnedIds.has(pm.messageId)) {
                  merged.push(pm);
                }
              });
              
              return merged.sort((a, b) => b.messageId - a.messageId);
            });
            
            // Detect newly pinned messages (for system notifications)
            setPreviousPinnedState(prev => {
              const newPinnedState = new Map();
              const newNotifications = [];
              
              pinned.forEach(msg => {
                newPinnedState.set(msg.messageId, true);
                // If message wasn't pinned before, it's a new pin
                if (!prev.has(msg.messageId) || !prev.get(msg.messageId)) {
                  const displayText = msg.hasMedia 
                    ? (msg.fileName || 'Media')
                    : (msg.text || 'Message');
                  newNotifications.push({
                    messageId: msg.messageId,
                    text: displayText,
                    timestamp: msg.timestamp,
                    pinnedBy: 'You' // Default to "You" since we don't track who pinned it
                  });
                }
              });
              
              // Update notifications (keep existing ones, add new ones)
              if (newNotifications.length > 0) {
                setPinNotifications(prevNotifs => {
                  // Merge with existing, avoiding duplicates
                  const existingIds = new Set(prevNotifs.map(n => n.messageId));
                  const uniqueNew = newNotifications.filter(n => !existingIds.has(n.messageId));
                  return [...prevNotifs, ...uniqueNew].sort((a, b) => a.timestamp - b.timestamp);
                });
              }
              
              return newPinnedState;
            });
            
            // ALWAYS update messages after priority ingestion to reflect deletions
            // Database is the source of truth after priority ingestion has run
            // Priority ingestion has deleted messages from DB, so we must sync
            const hasChanged = hasNewMessages || hasRemovedMessages || hasEditedMessages || 
              prevIds.size !== newIds.size || hasDifferentLength;
            
            // ALWAYS update if there are any differences (messages added, removed, or edited)
            // After priority ingestion runs, database is authoritative
            // Check if messages differ in any way
            const idsDiffer = prevIds.size !== newIds.size || 
              [...prevIds].some(id => !newIds.has(id)) || 
              [...newIds].some(id => !prevIds.has(id));
            const lengthDiffers = prevFiltered.length !== allMessages.length;
            
            // If IDs or length differ, definitely update (deletions detected)
            // Use prevFiltered for all comparisons
            if (idsDiffer || lengthDiffers || hasChanged) {
              // Auto-scroll to bottom if we're near the bottom and have new messages
              setTimeout(() => {
                const messagesContainer = messagesContainerRef.current;
                if (messagesContainer && hasNewMessages) {
                  const isNearBottom = messagesContainer.scrollHeight - messagesContainer.scrollTop - messagesContainer.clientHeight < 200;
                  if (isNearBottom) {
                    messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    setShowNewMessageNotification(false);
                  }
                }
              }, 100);
              
              // Return all messages (including pinned) - they appear in both pinned section and chat
              return allMessages;
            }
            
            // After priority ingestion runs, database is the source of truth
            // Always check for ID differences to catch deletions that might not change the count
            // Compare sets directly to detect any ID differences (more reliable)
            const hasIdDifference = prevIds.size !== newIds.size || 
              Array.from(prevIds).some(id => !newIds.has(id)) || 
              Array.from(newIds).some(id => !prevIds.has(id));
            
            // If IDs or length differ, always update (deletions or additions detected)
            // This ensures deletions are immediately reflected in UI
            // Return all messages (including pinned)
            if (hasIdDifference || prevFiltered.length !== allMessages.length) {
              return allMessages;
            }
            
            // If everything matches exactly, return all messages (including pinned)
            // Pinned messages appear in both pinned section and regular chat
            return allMessages;
          });
          
          // Also update if messages array is empty (no messages in database after deletion)
          // But preserve any pending messages that aren't deleted
          if (rows.length === 0) {
            setMessages(prev => {
              // Capture deletedMessageIds to use latest value
              const currentDeletedIds = deletedMessageIds;
              // Filter out ALL deleted messages first
              const filtered = prev.filter(msg => !currentDeletedIds.has(msg.messageId));
              // Only clear if we previously had messages and all were deleted (avoid clearing on initial load)
              if (prev.length > 0 && filtered.length === 0) {
                return [];
              }
              // Return filtered version to ensure deleted messages are removed
              return filtered;
            });
          }
        }
      } catch (err) {
        // Silently fail - polling errors are not critical
        console.error('Failed to poll for new messages:', err);
      }
    };

    // Store polling function in ref so we can call it after operations complete
    pollForNewMessagesRef.current = pollForNewMessages;
    
    // Poll immediately (to check for new messages) - but only if no operation in progress
    if (!operationInProgressRef.current && !isOperationInProgress) {
      pollForNewMessages();
    }
    
    // Then poll every 5 seconds for new messages (only if no operation in progress)
    // This also triggers priority ingestion to sync with Telegram
    const messageInterval = setInterval(() => {
      // Check flag at the start of each interval (use ref for synchronous check)
      if (!operationInProgressRef.current && !isOperationInProgress) {
        pollForNewMessages();
      }
    }, 5000);
    
    return () => clearInterval(messageInterval);
  }, [conversationInitialized, targetId, userId, isOperationInProgress, deletedMessageIds, recentlyEditedMessages]); // Include dependencies

  const loadTarget = async () => {
    try {
      setLoading(true);
      const response = await targetApi.getById(targetId, userId);
      if (response.data.success) {
        setTarget(response.data.data);
      } else {
        setError(response.data.error || 'Failed to load target');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load target');
    } finally {
      setLoading(false);
    }
  };

  const loadPlatformAccounts = async () => {
    try {
      const response = await platformApi.getAccounts(userId);
      if (response.data.success) {
        const accounts = response.data.data || [];
        setPlatformAccounts(accounts);
        // Pre-select the target's primary platform account if present
        if (target?.platformAccountId) {
          setSelectedAccountIds([target.platformAccountId]);
        }
      }
    } catch (err) {
      console.error('Failed to load platform accounts:', err);
    }
  };

  const handleGoalChange = (e) => {
    setGoalData({
      ...goalData,
      [e.target.name]: e.target.value,
    });
  };

  const handleInitializeConversation = async (e) => {
    e.preventDefault();
    try {
      const payload = {
        ...goalData,
        includedPlatformAccountIds: selectedAccountIds,
      };
      const response = await conversationApi.initialize(targetId, payload, userId, subtargetUserId);
      if (response.data.success) {
        setConversationInitialized(true);
        setError(null);
        await loadMessages();
      } else {
        setError(response.data.error || 'Failed to initialize conversation');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to initialize conversation');
    }
  };

  const handleSendMessage = async (e) => {
    if (e && typeof e.preventDefault === 'function') e.preventDefault();
    
    // If we have pending media, send media with text; otherwise send text only
    if (pendingMedia) {
      const messageText = newMessage.trim() || null;
      const file = pendingMedia.file;
      const preview = pendingMedia.preview;
      
      // INSTANT UI UPDATE: Add message optimistically immediately
      const tempMessageId = Date.now(); // Temporary ID until we get real one
      const optimisticMsg = {
        text: messageText || null,
        fromUser: true,
        timestamp: new Date(),
        mediaUrl: preview || null, // Use preview immediately
        messageId: tempMessageId,
        hasMedia: true,
        fileName: file.name || null,
        mimeType: file.type || null,
        fileSize: file.size || null,
        referenceId: replyingTo?.messageId || null,
        isPending: true, // Mark as pending until backend confirms
      };
      
      setMessages(prev => [...prev, optimisticMsg]);
      setNewMessage('');
      setPendingMedia(null);
      const replyToId = replyingTo?.messageId;
      setReplyingTo(null); // Clear reply immediately
      
      // INSTANT SCROLL: Scroll to bottom immediately when message is added
      setTimeout(() => {
        const messagesContainer = messagesContainerRef.current;
        if (messagesContainer) {
          messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
      }, 0); // Use 0ms timeout to scroll after React updates DOM

      // Send in background (don't block UI)
      conversationApi.sendMediaWithText(
        targetId, 
        userId, 
        file, 
        messageText,
        replyToId,
        subtargetUserId
      ).then(response => {
        if (response.data?.success && response.data?.data) {
          const messageData = response.data.data;
          // Replace optimistic message with real one
          setMessages(prev => prev.map(msg => 
            msg.messageId === tempMessageId 
              ? {
                  text: messageData.text || messageText || null,
                  fromUser: true,
                  timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
                  mediaUrl: messageData.mediaDownloadUrl || preview,
                  messageId: messageData.messageId,
                  hasMedia: true,
                  fileName: messageData.fileName || file.name || null,
                  mimeType: messageData.mimeType || file.type || null,
                  fileSize: messageData.fileSize || file.size || null,
                  referenceId: replyToId || null,
                }
              : msg
          ));
        } else {
          // Remove optimistic message if send failed
          setMessages(prev => prev.filter(msg => msg.messageId !== tempMessageId));
          setError('Failed to send media');
          setTimeout(() => setError(null), 5000);
          // Restore state
          setPendingMedia({ file, preview });
          setNewMessage(messageText || '');
        }
      }).catch(err => {
        // Remove optimistic message on error
        setMessages(prev => prev.filter(msg => msg.messageId !== tempMessageId));
        setError(err.response?.data?.error || err.message || 'Failed to send media');
        setTimeout(() => setError(null), 5000);
        // Restore state
        setPendingMedia({ file, preview });
        setNewMessage(messageText || '');
      });
    } else {
      // Send text-only message
      if (!newMessage.trim()) return;

      const messageText = newMessage.trim();
      
      // INSTANT UI UPDATE: Add message optimistically immediately
      const tempMessageId = Date.now(); // Temporary ID until we get real one
      const optimisticMsg = {
        text: messageText,
        fromUser: true,
        timestamp: new Date(),
        mediaUrl: null,
        messageId: tempMessageId,
        referenceId: replyingTo?.messageId || null,
        isPending: true, // Mark as pending until backend confirms
      };
      
      setMessages(prev => [...prev, optimisticMsg]);
      setNewMessage(''); // Clear input immediately
      const replyToId = replyingTo?.messageId;
      setReplyingTo(null); // Clear reply immediately
      
      // INSTANT SCROLL: Scroll to bottom immediately when message is added
      setTimeout(() => {
        const messagesContainer = messagesContainerRef.current;
        if (messagesContainer) {
          messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
      }, 0); // Use 0ms timeout to scroll after React updates DOM

      // Send in background (don't block UI)
      conversationApi.respond(
        targetId, 
        messageText, 
        userId,
        replyToId,
        subtargetUserId
      ).then(response => {
        if (response.data?.success && response.data?.data) {
          const messageData = response.data.data;
          const realMessageId = messageData.messageId;
          // Replace optimistic message with real one, but keep it as pending until it appears in database
          setMessages(prev => prev.map(msg => 
            msg.messageId === tempMessageId 
              ? {
                  text: messageData.text || messageText,
                  fromUser: true,
                  timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
                  mediaUrl: messageData.mediaDownloadUrl || null,
                  messageId: realMessageId,
                  referenceId: replyToId || null,
                  isPending: true, // Keep as pending until it appears in database from polling
                }
              : msg
          ));
          // Mark this message ID so polling knows to preserve it even if not in database yet
          // The message will be marked as not pending once polling finds it in the database
        } else {
          // Remove optimistic message if send failed
          setMessages(prev => prev.filter(msg => msg.messageId !== tempMessageId));
          setError('Failed to send message');
          setTimeout(() => setError(null), 5000);
          setNewMessage(messageText); // Restore message text
        }
      }).catch(err => {
        // Remove optimistic message on error
        setMessages(prev => prev.filter(msg => msg.messageId !== tempMessageId));
        setError(err.response?.data?.error || err.message || 'Failed to send message');
        setTimeout(() => setError(null), 5000);
        setNewMessage(messageText); // Restore message text
      });
    }
  };

  const [editText, setEditText] = useState('');
  const [selectedMessageId, setSelectedMessageId] = useState(null);
  const [selectedMessageHasMedia, setSelectedMessageHasMedia] = useState(false);
  const [pendingMediaReplacement, setPendingMediaReplacement] = useState(null); // { file, preview }
  const handleEditLast = async () => {
    // If we have pending media replacement, replace the media instead of just editing text
    if (pendingMediaReplacement) {
      const messageText = editText.trim() || null;
      const file = pendingMediaReplacement.file;
      const msgId = selectedMessageId;
      
      setEditText('');
      setSelectedMessageId(null);
      setPendingMediaReplacement(null);
      
      try {
        const response = await conversationApi.replaceMedia(targetId, userId, msgId, file, messageText, subtargetUserId);
        if (response.data?.success && response.data?.data) {
          const messageData = response.data.data;
          const updatedMsg = {
            text: messageData.text || messageText || null,
            fromUser: messageData.fromUser !== undefined ? messageData.fromUser : true,
            timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
            mediaUrl: messageData.mediaDownloadUrl || null,
            messageId: messageData.messageId,
            hasMedia: messageData.hasMedia || true,
            fileName: messageData.fileName || null,
            mimeType: messageData.mimeType || null,
            edited: true,
          };
          // Update the message in place (it was edited, not replaced, so same messageId)
          setMessages(prev => prev.map(msg => {
            if (msg.messageId === msgId || msg.messageId === updatedMsg.messageId) {
              // Preserve media info if it exists
              return { 
                ...updatedMsg, 
                mediaUrl: updatedMsg.mediaUrl || msg.mediaUrl,
                hasMedia: true
              };
            }
            return msg;
          }));
        } else {
          await loadMessages();
        }
      } catch (err) {
        setError(err.response?.data?.error || err.message || 'Failed to replace media');
        // Restore state if replacement failed
        setPendingMediaReplacement({ file, preview: pendingMediaReplacement.preview });
        setSelectedMessageId(msgId);
        setEditText(messageText || '');
      }
      return;
    }
    
    // Regular text edit (no media replacement)
    // Allow empty text for media messages (to remove caption)
    if (!selectedMessageHasMedia && !editText.trim()) return;
    
    // For media messages, allow empty text to remove caption; for text messages, trim it
    const messageText = selectedMessageHasMedia ? editText : editText.trim();
    const msgId = selectedMessageId;
    
    // CRITICAL: Set operation in progress to pause polling
    operationInProgressRef.current = true;
    setIsOperationInProgress(true);
    
    // INSTANT UI UPDATE: Update message optimistically immediately
    setMessages(prev => {
      const updated = prev.map(msg => {
        if (msg.messageId === msgId) {
          // Preserve all existing properties, just update text and mark as edited
          return { 
            ...msg, 
            text: messageText || null,
            edited: true,
            isPending: true, // Mark as pending until backend confirms
          };
        }
        return msg;
      });
      return updated;
    });
    
    setEditText('');
    setSelectedMessageId(null);
    
    // Mark this message as recently edited to prevent polling from overwriting it
    // Use longer timeout to ensure database has time to update (15 seconds)
    setRecentlyEditedMessages(prev => new Set(prev).add(msgId));
    setTimeout(() => {
      setRecentlyEditedMessages(prev => {
        const next = new Set(prev);
        next.delete(msgId);
        return next;
      });
    }, 15000); // Increased to 15 seconds to ensure database is fully updated and polling doesn't revert
    
    // Edit in background (don't block UI)
    const editPromise = msgId 
      ? conversationApi.edit(targetId, userId, msgId, messageText, subtargetUserId)
      : conversationApi.editLast(targetId, userId, messageText, subtargetUserId);
    
    editPromise.then(response => {
      // Clear operation in progress flag
      operationInProgressRef.current = false;
      setIsOperationInProgress(false);
      
      if (response.data?.success && response.data?.data) {
        const messageData = response.data.data;
        // Update with real data from backend, but keep isPending until we're sure it's in DB
        setMessages(prev => prev.map(msg => {
          if (msg.messageId === messageData.messageId || msg.messageId === msgId) {
            return {
              ...msg,
              text: messageData.text || messageText || null,
              edited: true,
              timestamp: messageData.timestamp ? new Date(messageData.timestamp) : msg.timestamp,
              mediaUrl: messageData.mediaDownloadUrl || msg.mediaUrl,
              hasMedia: messageData.hasMedia !== undefined ? messageData.hasMedia : msg.hasMedia,
              fileName: messageData.fileName || msg.fileName,
              mimeType: messageData.mimeType || msg.mimeType,
              messageId: messageData.messageId, // Use real messageId if different
              isPending: false, // Clear pending flag after backend confirms
            };
          }
          return msg;
        }));
        // Keep in recentlyEditedMessages for a bit longer to ensure polling doesn't overwrite
        // The backend confirmed, but database might take a moment to update
        setTimeout(() => {
          setRecentlyEditedMessages(prev => {
            const next = new Set(prev);
            next.delete(msgId);
            next.delete(messageData.messageId);
            return next;
          });
        }, 5000); // Keep protection for 5 more seconds after backend confirms
      } else {
        // Edit failed - restore original text
        setMessages(prev => prev.map(msg => 
          msg.messageId === msgId ? { ...msg, isPending: false } : msg
        ));
        setError('Failed to edit message');
        setTimeout(() => setError(null), 5000);
        // Remove from recentlyEditedMessages since edit failed
        setRecentlyEditedMessages(prev => {
          const next = new Set(prev);
          next.delete(msgId);
          return next;
        });
        setRecentlyEditedMessages(prev => {
          const next = new Set(prev);
          next.delete(msgId);
          return next;
        });
        // Restore edit state
        setSelectedMessageId(msgId);
        setEditText(messageText);
      }
    }).catch(err => {
      // Edit failed - restore original text
      setMessages(prev => prev.map(msg => 
        msg.messageId === msgId ? { ...msg, isPending: false } : msg
      ));
      setError(err.response?.data?.error || err.message || 'Failed to edit message');
      setTimeout(() => setError(null), 5000);
      // Remove from recentlyEditedMessages since edit failed
      setRecentlyEditedMessages(prev => {
        const next = new Set(prev);
        next.delete(msgId);
        return next;
      });
      // Restore edit state
      setSelectedMessageId(msgId);
      setEditText(messageText);
    });
  };

  const loadMessages = async () => {
    try {
      // Auto-load last 50 messages
      const resp = await conversationApi.getMessages(targetId, userId, 50, subtargetUserId);
      if (resp.data?.success) {
        const rows = resp.data.data || [];
        const loadedMessages = rows.map((r) => {
          // Format mediaUrl as absolute URL if it's a relative path
          let mediaUrl = null;
          if (r.mediaDownloadUrl) {
            if (r.mediaDownloadUrl.startsWith('http')) {
              mediaUrl = r.mediaDownloadUrl;
            } else {
              // It's a relative path, convert to absolute using downloadMediaUrl function
              // This ensures it works for both user and target user media
              mediaUrl = conversationApi.downloadMediaUrl(targetId, userId, r.messageId);
            }
          }
          
          return {
            text: r.text || '',
            fromUser: !!r.fromUser,
            timestamp: r.timestamp ? new Date(r.timestamp) : new Date(),
            mediaUrl: mediaUrl,
            messageId: r.messageId,
            hasMedia: r.hasMedia || false,
            fileName: r.fileName || null,
            mimeType: r.mimeType || null,
            edited: r.edited || false, // Preserve edited flag from database or API
            referenceId: r.referenceId || null, // Include reference_id for replies
            fileSize: r.fileSize || null, // Include file size
            pinned: r.pinned || false, // Include pinned status
          };
        });
        // CRITICAL: Filter out deleted messages before setting state
        // This ensures deleted messages never appear even on initial load
        const filteredLoadedMessages = loadedMessages.filter(msg => !deletedMessageIds.has(msg.messageId));
        
        // Extract pinned messages and separate them
        const pinned = filteredLoadedMessages.filter(msg => msg.pinned).sort((a, b) => b.messageId - a.messageId);
        const unpinned = filteredLoadedMessages.filter(msg => !msg.pinned);
        
        // Detect newly pinned messages (for system notifications)
        setPreviousPinnedState(prev => {
          const newPinnedState = new Map();
          const newNotifications = [];
          
          pinned.forEach(msg => {
            newPinnedState.set(msg.messageId, true);
            // If message wasn't pinned before, it's a new pin
            if (!prev.has(msg.messageId) || !prev.get(msg.messageId)) {
              const displayText = msg.hasMedia 
                ? (msg.fileName || 'Media')
                : (msg.text || 'Message');
              newNotifications.push({
                messageId: msg.messageId,
                text: displayText,
                timestamp: msg.timestamp,
                pinnedBy: 'You' // Default to "You" since we don't track who pinned it
              });
            }
          });
          
          // Update notifications (keep existing ones, add new ones)
          if (newNotifications.length > 0) {
            setPinNotifications(prevNotifs => {
              // Merge with existing, avoiding duplicates
              const existingIds = new Set(prevNotifs.map(n => n.messageId));
              const uniqueNew = newNotifications.filter(n => !existingIds.has(n.messageId));
              return [...prevNotifs, ...uniqueNew].sort((a, b) => a.timestamp - b.timestamp);
            });
          }
          
          return newPinnedState;
        });
        
        setPinnedMessages(pinned);
        // Include pinned messages in regular chat too
        setMessages(filteredLoadedMessages);
        // Initialize current pinned index to 0 (first pinned message)
        if (pinned.length > 0) {
          setCurrentPinnedIndex(0);
        }
        console.log(`Loaded ${filteredLoadedMessages.length} messages for target ${targetId} (${pinned.length} pinned)`);
        
        // Track highest message ID for new message detection
        if (filteredLoadedMessages.length > 0) {
          const highestId = Math.max(...filteredLoadedMessages.map(m => m.messageId || 0));
          setLastHighestMessageId(highestId);
        }
        
        // Auto-scroll to bottom after messages are loaded
        setTimeout(() => {
          const messagesContainer = messagesContainerRef.current;
          if (messagesContainer) {
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
          }
        }, 100);
      } else {
        console.warn('Failed to load messages:', resp.data?.error);
        setMessages([]); // Clear messages if load fails
      }
    } catch (e) {
      console.error('Failed to load messages:', e);
      setMessages([]); // Clear messages on error
    }
  };

  const handleDelete = (messageId) => {
    // Show delete confirmation modal
    setDeleteModal({ messageId, revoke: true }); // Default to checked (revoke=true)
  };

  const performDelete = async () => {
    if (!deleteModal) return;
    const { messageId, revoke } = deleteModal;
    
    // Close modal FIRST before doing anything else
    const messageIdToDelete = messageId;
    setDeleteModal(null);
    
    // CRITICAL: Set operation in progress to pause polling
    operationInProgressRef.current = true;
    setIsOperationInProgress(true);
    
    // CRITICAL: Track this message as deleted FIRST (before removing from UI)
    // This ensures polling can't re-add it even if it runs during deletion
    // Use functional update to ensure we're working with latest state
    setDeletedMessageIds(prev => {
      const updated = new Set(prev);
      updated.add(messageIdToDelete);
      return updated;
    });
    
    // INSTANT UI UPDATE: Remove message optimistically immediately
    // Use functional update to ensure we're working with latest state
    setMessages(prev => {
      // Filter out the deleted message immediately
      return prev.filter(m => m.messageId !== messageIdToDelete);
    });
    
    // CRITICAL: Set deletedMessageIds again to ensure it's definitely in state
    // This is a safety measure to ensure polling can't re-add the message
    setDeletedMessageIds(prev => {
      const updated = new Set(prev);
      updated.add(messageIdToDelete);
      return updated;
    });
    
    // Delete in background (don't block UI)
    conversationApi.delete(targetId, userId, messageIdToDelete, revoke, subtargetUserId)
      .then(response => {
        // Clear operation in progress flag
        operationInProgressRef.current = false;
        setIsOperationInProgress(false);
        
        if (response.data?.success) {
          if (response.data?.data?.message) {
            // Show info if message was only deleted for user (not revoked)
            setError(response.data.data.message);
            setTimeout(() => setError(null), 5000);
          }
          // Message successfully deleted - keep it in deletedMessageIds to prevent re-adding
          // Remove from deletedMessageIds after priority ingestion has time to sync and record the deletion
          // Use longer timeout to ensure it's fully removed from database
          setTimeout(() => {
            setDeletedMessageIds(prev => {
              const updated = new Set(prev);
              updated.delete(messageIdToDelete);
              return updated;
            });
          }, 60000); // Remove from tracking after 60 seconds to ensure it's fully synced
        } else {
          // Show error if deletion failed - restore message
          setError(response.data?.error || 'Failed to delete message');
          setTimeout(() => setError(null), 5000);
          // Remove from deleted tracking since deletion failed
          setDeletedMessageIds(prev => {
            const updated = new Set(prev);
            updated.delete(messageIdToDelete);
            return updated;
          });
          // Reload messages to restore the deleted message if deletion failed
          setTimeout(() => loadMessages(), 1000);
        }
      })
      .catch(err => {
        // Clear operation in progress flag
        operationInProgressRef.current = false;
        setIsOperationInProgress(false);
        
        setError(err.response?.data?.error || err.message || 'Failed to delete message');
        setTimeout(() => setError(null), 5000);
        // Remove from deleted tracking since deletion failed
        setDeletedMessageIds(prev => {
          const updated = new Set(prev);
          updated.delete(messageIdToDelete);
          return updated;
        });
        // Reload messages to restore the deleted message if deletion failed
        setTimeout(() => loadMessages(), 1000);
      });
  };

  const cancelDelete = () => {
    setDeleteModal(null);
  };

  if (loading) {
    return <div className="spinner"></div>;
  }

  if (error && !target) {
    return (
      <div className="container">
        <div className="alert alert-error">{error}</div>
        <button className="btn btn-secondary" onClick={() => navigate('/targets')}>
          Back to Targets
        </button>
      </div>
    );
  }

  return (
    <div className="conversation-view">
      <div className="container">
        <div className="header">
          <button className="btn btn-secondary" onClick={() => navigate('/targets')}>
            ← Back
          </button>
          <h1>Conversation with {target?.name}</h1>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {!conversationInitialized ? (
          <div className="goal-form-container">
            <h2>Set Conversation Goal</h2>
            <form onSubmit={handleInitializeConversation} className="goal-form">
              <div className="form-group">
                <label htmlFor="context">Context (Optional)</label>
                <textarea
                  id="context"
                  name="context"
                  value={goalData.context}
                  onChange={handleGoalChange}
                  placeholder="Recent situation/detail to guide the tone (optional)"
                  rows="4"
                />
              </div>

              <div className="form-group">
                <label htmlFor="desiredOutcome">Desired Outcome *</label>
                <textarea
                  id="desiredOutcome"
                  name="desiredOutcome"
                  value={goalData.desiredOutcome}
                  onChange={handleGoalChange}
                  required
                  placeholder="What do you want to achieve? (e.g., 'Arrange a romantic date', 'Secure investment')"
                  rows="3"
                />
              </div>

              <div className="form-group">
                <label htmlFor="meetingContext">Meeting Context (Optional)</label>
                <textarea
                  id="meetingContext"
                  name="meetingContext"
                  value={goalData.meetingContext}
                  onChange={handleGoalChange}
                  placeholder="Additional context about how you met, where, when, etc."
                  rows="3"
                />
              </div>

              {/* Historical connector selection */}
              <div className="form-group">
                <label>Historical Connectors</label>
                <p className="hint">
                  By default ARIA will use chats from the target&apos;s primary connector.
                  You can also include historical chats from other connectors.
                </p>
                <div className="connector-selector">
                  <select
                    value={selectedAccountIds[0] || ''}
                    onChange={(e) => {
                      const id = parseInt(e.target.value, 10);
                      if (!isNaN(id)) {
                        setSelectedAccountIds((prev) =>
                          prev.includes(id) ? prev : [id, ...prev.filter((x) => x !== id)]
                        );
                      }
                    }}
                  >
                    <option value="">Select primary connector</option>
                    {platformAccounts.map((acc) => (
                      <option key={acc.id} value={acc.id}>
                        {acc.platform} {acc.username && `(@${acc.username})`} {acc.number && ` [${acc.number}]`}
                      </option>
                    ))}
                  </select>
                  <div className="connector-checkboxes">
                    {platformAccounts.map((acc) => (
                      <label key={acc.id} className="connector-checkbox">
                        <input
                          type="checkbox"
                          checked={selectedAccountIds.includes(acc.id)}
                          onChange={(e) => {
                            const checked = e.target.checked;
                            setSelectedAccountIds((prev) => {
                              if (checked) {
                                return prev.includes(acc.id) ? prev : [...prev, acc.id];
                              } else {
                                return prev.filter((x) => x !== acc.id);
                              }
                            });
                          }}
                        />
                        <span>
                          {acc.platform} {acc.username && `(@${acc.username})`} {acc.number && ` [${acc.number}]`}
                        </span>
                      </label>
                    ))}
                  </div>
                </div>
              </div>

              <button type="submit" className="btn btn-primary">
                Initialize Conversation
              </button>
            </form>
          </div>
        ) : (
          <div className="conversation-container" style={{ position: 'relative' }}>
            <div className="conversation-header">
              <h2 style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                {target?.profilePictureUrl ? (
                  <img 
                    src={target.profilePictureUrl} 
                    alt={target?.name}
                    style={{ 
                      width: '48px', 
                      height: '48px', 
                      borderRadius: '50%', 
                      objectFit: 'cover'
                    }}
                  />
                ) : (
                  <div 
                    style={{ 
                      width: '48px', 
                      height: '48px', 
                      borderRadius: '50%', 
                      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: 'white',
                      fontWeight: 'bold',
                      fontSize: '1.5rem'
                    }}
                  >
                    {target?.name?.charAt(0).toUpperCase() || '?'}
                  </div>
                )}
                <span>
                  {(() => {
                    // If we have a subtargetUserId, find the SubTarget User and use its name or username
                    if (subtargetUserId && target?.subTargetUsers) {
                      const subTarget = target.subTargetUsers.find(st => st.id === subtargetUserId);
                      if (subTarget) {
                        // Use name if available, otherwise use username with @ prefix (only if not already present)
                        if (subTarget.name) {
                          return `Conversation with ${subTarget.name}`;
                        } else {
                          const username = subTarget.username || '';
                          const displayUsername = username.startsWith('@') ? username : `@${username}`;
                          return `Conversation with ${displayUsername}`;
                        }
                      }
                    }
                    // Fallback to target name
                    return `Conversation with ${target?.name || 'Unknown'}`;
                  })()}
                </span>
                {(() => {
                  // Show platform and account info if we have a SubTarget User
                  if (subtargetUserId && target?.subTargetUsers) {
                    const subTarget = target.subTargetUsers.find(st => st.id === subtargetUserId);
                    if (subTarget && subTarget.platform) {
                      // Get platform account info
                      const platformAccount = platformAccounts.find(acc => acc.id === subTarget.platformAccountId);
                      const accountLabel = platformAccount?.username || platformAccount?.number || 'Unknown';
                      return (
                        <span style={{ fontSize: '0.75rem', color: '#666', marginLeft: '0.5rem' }}>
                          Platform: {subTarget.platform} (@{accountLabel})
                        </span>
                      );
                    }
                  }
                  return null;
                })()}
                {targetOnline ? (
                  <>
                    <span className="online-indicator" title="Online" style={{ backgroundColor: '#4caf50' }}></span>
                    <span style={{ fontSize: '0.8rem', color: '#4caf50', marginLeft: '0.25rem', fontWeight: 500 }}>Online</span>
                  </>
                ) : (
                  <>
                    <span className="offline-indicator" title={lastActive || "Offline"}></span>
                    {lastActive && (
                      <span style={{ fontSize: '0.8rem', color: '#666', marginLeft: '0.5rem' }}>
                        {lastActive}
                      </span>
                    )}
                  </>
                )}
              </h2>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  className="btn btn-primary btn-sm"
                  onClick={() => {
                    // Get platform account ID from current SubTarget User if available
                    let platformAccountId = null;
                    if (subtargetUserId && target?.subTargetUsers) {
                      const subTarget = target.subTargetUsers.find(st => st.id === subtargetUserId);
                      if (subTarget?.platformAccountId) {
                        platformAccountId = subTarget.platformAccountId;
                      }
                    }
                    
                    // Navigate with platform account ID and subtargetUserId in URL params
                    // This preserves the subtargetUserId so we can navigate back correctly
                    const params = new URLSearchParams();
                    if (platformAccountId) {
                      params.append('platformAccountId', platformAccountId);
                    }
                    if (subtargetUserId) {
                      params.append('subtargetUserId', subtargetUserId);
                    }
                    const queryString = params.toString();
                    navigate(`/analysis/${targetId}${queryString ? '?' + queryString : ''}`);
                  }}
                >
                  View Analysis
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={async () => {
                    try {
                      await conversationApi.end(targetId, userId);
                      setConversationInitialized(false);
                      navigate('/targets');
                  } catch (err) {
                    setError(err.response?.data?.error || err.message || 'Failed to end conversation');
                  }
                }}
              >
                End Conversation
              </button>
            </div>
          </div>
          
          {/* Multi-select Mode Controls - positioned just below header */}
          {isMultiSelectMode && (
            <div style={{
              background: '#667eea',
              color: 'white',
              padding: '8px 16px',
              borderRadius: '8px',
              boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
              marginBottom: '8px',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '12px',
              width: 'auto'
            }}>
              <span style={{ fontSize: '0.9rem', fontWeight: 500 }}>{selectedMessages.size} selected</span>
              {selectedMessages.size > 0 && (
                <>
                  <button
                    style={{
                      background: 'rgba(255,255,255,0.2)',
                      border: 'none',
                      color: 'white',
                      padding: '4px 10px',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontSize: '0.85rem'
                    }}
                    onClick={() => {
                      // Copy selected messages
                      const selectedTexts = Array.from(selectedMessages)
                        .map(id => {
                          const msg = messages.find(m => m.messageId === id);
                          return msg ? (msg.text || (msg.hasMedia ? 'Media' : 'Message')) : '';
                        })
                        .filter(t => t)
                        .join('\n');
                      navigator.clipboard.writeText(selectedTexts).then(() => {
                        setError('Selected messages copied to clipboard');
                        setTimeout(() => setError(null), 2000);
                      });
                    }}
                  >
                    📋 Copy
                  </button>
                  <button
                    style={{
                      background: 'rgba(255,255,255,0.2)',
                      border: 'none',
                      color: 'white',
                      padding: '4px 10px',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontSize: '0.85rem'
                    }}
                    onClick={async () => {
                      // Delete selected messages - track all deleted IDs
                      const selectedArray = Array.from(selectedMessages);
                      
                      // Track all selected messages as deleted
                      setDeletedMessageIds(prev => new Set([...prev, ...selectedArray]));
                      
                      // Remove from UI immediately
                      setMessages(prev => prev.filter(m => !selectedArray.includes(m.messageId)));
                      
                      // Clear selection and exit multi-select mode
                      setSelectedMessages(new Set());
                      setIsMultiSelectMode(false);
                      
                      // Pause polling while deletions are in progress
                      setIsOperationInProgress(true);
                      
                      // Delete each message from Telegram
                      try {
                        await Promise.all(selectedArray.map(async (messageId) => {
                          try {
                            await conversationApi.delete(targetId, userId, messageId, true, subtargetUserId);
                          } catch (err) {
                            console.error(`Failed to delete message ${messageId}:`, err);
                          }
                        }));
                        
                        // Wait for deletions to complete
                        await new Promise(resolve => setTimeout(resolve, 2000));
                        
                        // Keep deleted messages tracked for 30 seconds
                        setTimeout(() => {
                          setDeletedMessageIds(prev => {
                            const updated = new Set(prev);
                            selectedArray.forEach(id => updated.delete(id));
                            return updated;
                          });
                        }, 30000);
                      } catch (err) {
                        console.error('Error deleting selected messages:', err);
                        // If deletion fails, reload messages to restore
                        setTimeout(() => loadMessages(), 1000);
                        // Remove from tracking since deletion failed
                        setDeletedMessageIds(prev => {
                          const updated = new Set(prev);
                          selectedArray.forEach(id => updated.delete(id));
                          return updated;
                        });
                      } finally {
                        // Resume polling after deletions complete
                        setTimeout(() => {
                          setIsOperationInProgress(false);
                        }, 2500);
                      }
                    }}
                  >
                    🗑️ Delete
                  </button>
                  <button
                    style={{
                      background: 'rgba(255,255,255,0.2)',
                      border: 'none',
                      color: 'white',
                      padding: '4px 10px',
                      borderRadius: '6px',
                      cursor: 'pointer',
                      fontSize: '0.85rem'
                    }}
                    onClick={() => {
                      setSelectedMessages(new Set());
                      setIsMultiSelectMode(false);
                    }}
                  >
                    ✕ Cancel
                  </button>
                </>
              )}
            </div>
          )}
          
            <div 
              ref={messagesContainerRef}
              className="messages-container"
              onScroll={(e) => {
                const container = e.target;
                const isNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 200;
                setShowScrollToBottom(!isNearBottom);
                // Hide new message notification if scrolled to bottom
                if (isNearBottom) {
                  setShowNewMessageNotification(false);
                }
                
                // Update which pinned message to show based on scroll position
                // Find the first pinned message that's above the current viewport
                if (pinnedMessages.length > 0) {
                  const scrollTop = container.scrollTop;
                  const viewportTop = scrollTop;
                  const viewportBottom = scrollTop + container.clientHeight;
                  
                  // Sort pinned messages by messageId (descending - newest first)
                  const sortedPinned = [...pinnedMessages].sort((a, b) => b.messageId - a.messageId);
                  
                  // Always check the last pinned message first
                  const lastPinnedIndex = sortedPinned.length - 1;
                  const lastPinned = sortedPinned[lastPinnedIndex];
                  const lastPinnedEl = container.querySelector(`[data-message-id="${lastPinned.messageId}"]`);
                  
                  if (lastPinnedEl) {
                    const containerRect = container.getBoundingClientRect();
                    const messageRect = lastPinnedEl.getBoundingClientRect();
                    const lastPinnedTop = messageRect.top - containerRect.top + scrollTop;
                    const lastPinnedBottom = lastPinnedTop + messageRect.height;
                    
                    // If we're at or past the last pinned message (scrolled to it or past it), always show it
                    // Never change to a previous pinned message when we're at or past the last one
                    if (viewportTop >= lastPinnedTop - 100) { // 100px buffer above
                      // We're at or past the last pinned message - always show it
                      if (currentPinnedIndex !== lastPinnedIndex) {
                        setCurrentPinnedIndex(lastPinnedIndex);
                      }
                      return; // Don't check for other pinned messages
                    }
                  }
                  
                  // If we're before the last pinned message, find the first pinned message above the viewport
                  let foundIndex = -1;
                  for (let i = 0; i < sortedPinned.length; i++) {
                    const pinnedMsg = sortedPinned[i];
                    const messageEl = container.querySelector(`[data-message-id="${pinnedMsg.messageId}"]`);
                    if (messageEl) {
                      // Get position relative to container
                      const containerRect = container.getBoundingClientRect();
                      const messageRect = messageEl.getBoundingClientRect();
                      const messageTop = messageRect.top - containerRect.top + scrollTop;
                      const messageBottom = messageTop + messageRect.height;
                      
                      // Check if message is above the viewport (with some buffer)
                      // Only consider messages that are actually above the viewport
                      if (messageBottom < viewportTop + 100) { // 100px buffer
                        foundIndex = i;
                        break;
                      }
                    }
                  }
                  
                  // If no pinned message found above viewport, show the first one
                  if (foundIndex === -1 && sortedPinned.length > 0) {
                    foundIndex = 0;
                  }
                  
                  // Only update if we found a different index
                  if (foundIndex >= 0 && foundIndex !== currentPinnedIndex) {
                    setCurrentPinnedIndex(foundIndex);
                  }
                }
              }}
            >
              {/* Pinned Messages Section - Telegram Style - Shows first pinned message above viewport */}
              {pinnedMessages.length > 0 && (() => {
                // Sort pinned messages by messageId (descending - newest first)
                const sortedPinned = [...pinnedMessages].sort((a, b) => b.messageId - a.messageId);
                // Get the current pinned message to display (first one above viewport)
                const currentPinned = sortedPinned[currentPinnedIndex] || sortedPinned[0];
                
                if (!currentPinned) return null;
                
                const displayText = currentPinned.hasMedia 
                  ? (currentPinned.fileName || 'Media')
                  : (currentPinned.text || 'Message');
                const truncatedText = displayText.length > 60 
                  ? displayText.substring(0, 60) + '...'
                  : displayText;
                
                return (
                  <div style={{ 
                    padding: '12px 16px', 
                    background: 'white',
                    borderBottom: '1px solid #e0e0e0',
                    marginBottom: '0',
                    position: 'sticky',
                    top: 0,
                    zIndex: 10
                  }}>
                    <div style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '8px'
                    }}>
                      <div style={{
                        width: '2px',
                        height: '40px',
                        background: '#999',
                        borderRadius: '1px',
                        marginLeft: '4px'
                      }}></div>
                      <div style={{ flex: 1 }}>
                        <div
                          onClick={() => {
                            // Scroll to the pinned message within the messages container
                            const container = messagesContainerRef.current;
                            if (!container) return;
                            
                            // Try to find the message element
                            const messageEl = container.querySelector(`[data-message-id="${currentPinned.messageId}"]`);
                            if (messageEl) {
                              // Calculate the position relative to the container
                              const containerRect = container.getBoundingClientRect();
                              const messageRect = messageEl.getBoundingClientRect();
                              const scrollTop = container.scrollTop;
                              const messageTop = messageRect.top - containerRect.top + scrollTop;
                              
                              // Scroll to center the message in the viewport
                              const targetScroll = messageTop - (container.clientHeight / 2) + (messageRect.height / 2);
                              container.scrollTo({
                                top: targetScroll,
                                behavior: 'smooth'
                              });
                              
                              // Highlight the message briefly
                              messageEl.style.background = 'rgba(102, 126, 234, 0.2)';
                              setTimeout(() => {
                                messageEl.style.background = '';
                              }, 2000);
                              
                              // After scrolling, update to show next pinned message
                              setTimeout(() => {
                                const sortedPinned = [...pinnedMessages].sort((a, b) => b.messageId - a.messageId);
                                if (currentPinnedIndex < sortedPinned.length - 1) {
                                  setCurrentPinnedIndex(currentPinnedIndex + 1);
                                } else {
                                  // If we're at the last pinned message, show the first one
                                  setCurrentPinnedIndex(0);
                                }
                              }, 800);
                            } else {
                              console.warn('Pinned message element not found:', currentPinned.messageId);
                            }
                          }}
                          style={{
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '4px 0'
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.opacity = '0.7';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.opacity = '1';
                          }}
                        >
                          <div style={{ flex: 1 }}>
                            <div style={{ 
                              fontSize: '0.75rem', 
                              color: '#0088cc', 
                              fontWeight: '500',
                              marginBottom: '2px'
                            }}>
                              Pinned message #{sortedPinned.length - currentPinnedIndex}
                            </div>
                            <div style={{ 
                              fontSize: '0.875rem', 
                              color: '#000',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap'
                            }}>
                              {truncatedText}
                            </div>
                          </div>
                          <div style={{ 
                            display: 'flex', 
                            gap: '8px', 
                            alignItems: 'center',
                            marginLeft: '12px'
                          }}>
                            <span style={{ 
                              fontSize: '0.75rem', 
                              color: '#999',
                              cursor: 'pointer'
                            }}>📌</span>
                            <span style={{ 
                              fontSize: '0.75rem', 
                              color: '#999',
                              cursor: 'pointer'
                            }}>☰</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })()}
              {messages.length === 0 && pinnedMessages.length === 0 && pinNotifications.length === 0 ? (
                <div className="empty-messages">
                  <p>No messages yet. {conversationInitialized ? 'Start the conversation!' : 'Messages will appear here once ingestion is complete.'}</p>
                </div>
              ) : (
                // Combine messages and pin notifications, sorted by timestamp
                // IMPORTANT: Include pinned messages in regular chat (they show in both pinned section and chat)
                (() => {
                  // Create combined array with pin notifications
                  const combined = [];
                  
                  // First, add all messages (including pinned ones - they should show in chat too)
                  messages.forEach(msg => {
                    combined.push({ ...msg, isPinNotification: false });
                  });
                  
                  // Sort messages by timestamp first (so we can correctly position notifications)
                  combined.sort((a, b) => {
                    const timeA = a.timestamp?.getTime() || 0;
                    const timeB = b.timestamp?.getTime() || 0;
                    return timeA - timeB;
                  });
                  
                  // Then, add pin notifications at the correct position (after the last message before the pinned message)
                  pinNotifications.forEach(notif => {
                    // Skip if notification already in combined array
                    if (combined.some(item => item.isPinNotification && item.messageId === notif.messageId)) {
                      return;
                    }
                    
                    // Find the pinned message to get its timestamp
                    const pinnedMsg = pinnedMessages.find(pm => pm.messageId === notif.messageId);
                    if (pinnedMsg) {
                      // Find the pinned message's position in the combined array
                      const pinnedMsgIndex = combined.findIndex(item => 
                        !item.isPinNotification && item.messageId === pinnedMsg.messageId
                      );
                      
                      if (pinnedMsgIndex >= 0) {
                        // Insert the notification AFTER the pinned message itself
                        // A message can only be pinned after it was sent, so the notification should appear after the message
                        const insertIndex = pinnedMsgIndex + 1;
                        combined.splice(insertIndex, 0, { ...notif, isPinNotification: true, pinnedBy: notif.pinnedBy, timestamp: pinnedMsg.timestamp });
                      } else {
                        // Pinned message not in combined array, add notification at the end
                        combined.push({ ...notif, isPinNotification: true, pinnedBy: notif.pinnedBy, timestamp: pinnedMsg.timestamp });
                      }
                    }
                  });
                  
                  return combined.map((msg, idx) => {
                    // Render pin notification as system message
                    if (msg.isPinNotification) {
                      return (
                        <div
                          key={`pin-notif-${msg.messageId}-${idx}`}
                          style={{
                            display: 'flex',
                            justifyContent: 'center',
                            alignItems: 'center',
                            margin: '8px 0',
                            padding: '0 16px'
                          }}
                        >
                          <div style={{
                            background: 'rgba(0, 0, 0, 0.05)',
                            borderRadius: '12px',
                            padding: '6px 12px',
                            fontSize: '0.875rem',
                            color: '#666',
                            maxWidth: '80%',
                            textAlign: 'center'
                          }}>
                            {msg.pinnedBy} pinned "{msg.text.length > 30 ? msg.text.substring(0, 30) + '...' : msg.text}"
                          </div>
                        </div>
                      );
                    }
                    
                    // Regular message rendering
                    // Find the message being replied to (if any)
                    const repliedToMsg = msg.referenceId ? messages.find(m => m.messageId === msg.referenceId) : null;
                  
                  return (
                  <div 
                    key={idx} 
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: '8px',
                      marginBottom: '0.5rem',
                      justifyContent: isMultiSelectMode ? 'space-between' : (msg.fromUser ? 'flex-end' : 'flex-start'),
                      position: 'relative',
                      width: '100%'
                    }}
                  >
                    {/* Message container */}
                    <div 
                      className={`message ${msg.fromUser ? 'from-user' : 'from-target'} ${selectedMessages.has(msg.messageId) ? 'selected' : ''}`}
                      onTouchStart={(e) => {
                        // Track touch start for swipe-to-reply
                        if (!msg.fromUser && !isMultiSelectMode) { // Only allow replying to target's messages
                          e.touchStartY = e.touches[0].clientY;
                        }
                      }}
                      onTouchEnd={(e) => {
                        // Swipe up to reply (only if not in multi-select mode)
                        if (!msg.fromUser && !isMultiSelectMode && e.changedTouches[0]) {
                          const touchEndY = e.changedTouches[0].clientY;
                          const touchStartY = e.touchStartY;
                          if (touchStartY && touchEndY < touchStartY - 50) { // Swipe up more than 50px
                            setReplyingTo({
                              messageId: msg.messageId,
                              text: msg.hasMedia ? (msg.fileName || 'Media') : (msg.text || 'Message'),
                              fromUser: msg.fromUser,
                              hasMedia: msg.hasMedia || false,
                              fileName: msg.fileName || null
                            });
                            // Focus the message input field after a brief delay to ensure state update
                            setTimeout(() => {
                              if (messageInputRef.current) {
                                messageInputRef.current.focus();
                              }
                            }, 100);
                          }
                        }
                      }}
                      onContextMenu={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        // Don't show context menu in multi-select mode
                        if (isMultiSelectMode) return;
                        // Get the message element to position context menu relative to it
                        const messageElement = e.currentTarget;
                        const rect = messageElement.getBoundingClientRect();
                        setContextMenu({
                          x: rect.right + 8, // Position to the right of the message
                          y: rect.top, // Position at the top of the message
                          messageId: msg.messageId,
                          fromUser: msg.fromUser,
                          hasMedia: msg.hasMedia || false,
                          text: msg.text || '',
                          fileName: msg.fileName || null
                        });
                      }}
                      onClick={(e) => {
                        // Toggle selection in multi-select mode - disable all other interactions
                        if (isMultiSelectMode) {
                          e.preventDefault();
                          e.stopPropagation();
                          setSelectedMessages(prev => {
                            const updated = new Set(prev);
                            if (updated.has(msg.messageId)) {
                              updated.delete(msg.messageId);
                            } else {
                              updated.add(msg.messageId);
                            }
                            return updated;
                          });
                        }
                      }}
                      style={{ 
                        cursor: isMultiSelectMode ? 'pointer' : (!msg.fromUser ? 'pointer' : 'default'),
                        position: 'relative',
                        backgroundColor: selectedMessages.has(msg.messageId) ? 'rgba(102, 126, 234, 0.3)' : undefined,
                        flex: '0 1 auto',
                        marginRight: isMultiSelectMode ? '28px' : undefined
                      }}
                    >
                    {/* Reply indicator - show message being replied to */}
                    {repliedToMsg && !isMultiSelectMode && (
                      <div 
                        style={{ 
                          padding: '4px 8px', 
                          background: 'rgba(0,0,0,0.05)', 
                          borderRadius: 4, 
                          marginBottom: 4,
                          fontSize: '0.75rem',
                          borderLeft: '2px solid #667eea',
                          cursor: 'pointer'
                        }}
                        onClick={(e) => {
                          // Only allow scroll to reply if not in multi-select mode
                          if (!isMultiSelectMode) {
                            e.stopPropagation();
                            // Scroll to the referenced message
                            const messageEl = document.querySelector(`[data-message-id="${msg.referenceId}"]`);
                            if (messageEl) {
                              messageEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
                              // Highlight the message briefly
                              messageEl.style.background = 'rgba(102, 126, 234, 0.2)';
                              setTimeout(() => {
                                messageEl.style.background = '';
                              }, 1000);
                            }
                          }
                        }}
                        title="Click to scroll to referenced message"
                      >
                        {repliedToMsg.fromUser ? 'You' : target?.name}: {repliedToMsg.hasMedia ? (repliedToMsg.fileName ? repliedToMsg.fileName : 'Media') : (repliedToMsg.text || 'Message')}
                      </div>
                    )}
                    {!repliedToMsg && msg.referenceId && (
                      <div 
                        style={{ 
                          padding: '4px 8px', 
                          background: 'rgba(0,0,0,0.05)', 
                          borderRadius: 4, 
                          marginBottom: 4,
                          fontSize: '0.75rem',
                          borderLeft: '2px solid #999',
                          fontStyle: 'italic',
                          color: '#666'
                        }}
                      >
                        Deleted message
                      </div>
                    )}
                    <div 
                      className="message-content" 
                      data-message-id={msg.messageId}
                      style={{
                        position: 'relative'
                      }}
                      onClick={(e) => {
                        // In multi-select mode, allow clicking anywhere on message content to select
                        if (isMultiSelectMode) {
                          e.stopPropagation();
                          setSelectedMessages(prev => {
                            const updated = new Set(prev);
                            if (updated.has(msg.messageId)) {
                              updated.delete(msg.messageId);
                            } else {
                              updated.add(msg.messageId);
                            }
                            return updated;
                          });
                        } else {
                          e.stopPropagation();
                        }
                      }}
                    >
                      {(() => {
                        // Check if this is just a link (not actual downloadable media)
                        // A link is detected if:
                        // 1. mediaUrl is a URL AND no fileName AND no mimeType, OR
                        // 2. text contains a URL AND (no hasMedia OR mediaUrl is just a link)
                        const isJustALink = (msg.mediaUrl && isUrl(msg.mediaUrl) && !msg.fileName && !msg.mimeType) ||
                                          (msg.text && isUrl(msg.text) && (!msg.hasMedia || (msg.mediaUrl && isUrl(msg.mediaUrl) && !msg.fileName && !msg.mimeType)));
                        
                        // If it's just a link, render as clickable link only
                        if (isJustALink) {
                          const linkText = msg.text || msg.mediaUrl;
                          return renderTextWithLinks(linkText);
                        }
                        
                        // Otherwise, render actual media or text
                        if (msg.hasMedia && msg.mediaUrl && (msg.mediaUrl.match(/\.mp4$|video\//) || msg.mimeType?.match(/^video\//))) {
                          return (
                            <div>
                              <video 
                                src={msg.mediaUrl} 
                                controls 
                                style={{ maxWidth: '180px', maxHeight: '150px', borderRadius: 6, cursor: 'pointer' }} 
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setMediaViewer({ type: 'video', url: msg.mediaUrl, fileName: msg.fileName });
                                }}
                                onError={(e) => {
                                  // If video fails to load, hide it
                                  e.target.style.display = 'none';
                                  console.error('Failed to load video:', msg.mediaUrl);
                                }}
                              />
                              {/* Download button for videos */}
                              {msg.mediaUrl && (
                                <div style={{ marginTop: 4, fontSize: '0.75rem' }}>
                                  <a 
                                    href={msg.mediaUrl} 
                                    download={msg.fileName || 'video.mp4'}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      // Force download
                                      e.preventDefault();
                                      const link = document.createElement('a');
                                      link.href = msg.mediaUrl;
                                      link.download = msg.fileName || 'video.mp4';
                                      link.target = '_blank';
                                      document.body.appendChild(link);
                                      link.click();
                                      document.body.removeChild(link);
                                    }}
                                    style={{ color: 'inherit', textDecoration: 'underline', cursor: 'pointer' }}
                                  >
                                    Download
                                  </a>
                                  {msg.fileName && (
                                    <span style={{ fontSize: '0.7rem', color: '#666', marginLeft: '4px' }}>
                                      ({msg.fileName})
                                    </span>
                                  )}
                                  {msg.fileSize && (
                                    <span style={{ fontSize: '0.7rem', color: '#666', marginLeft: '4px' }}>
                                      ({(msg.fileSize / 1024).toFixed(1)} KB)
                                    </span>
                                  )}
                                </div>
                              )}
                              {/* Show text if present */}
                              {msg.text && msg.text.trim() && (
                                <div style={{ marginTop: 8 }}>
                                  {renderTextWithLinks(msg.text)}
                                </div>
                              )}
                            </div>
                          );
                        } else if (msg.hasMedia && msg.mediaUrl && (msg.mediaUrl.match(/\.(ogg|oga|mp3|m4a|wav|opus)$/i) || msg.mimeType?.match(/^audio\//))) {
                          // Audio/voice note player
                          return (
                            <div>
                              <div style={{ 
                                padding: '12px', 
                                background: 'rgba(255,255,255,0.1)', 
                                borderRadius: 8,
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                minWidth: '200px'
                              }}>
                                <audio 
                                  src={msg.mediaUrl} 
                                  controls 
                                  style={{ flex: 1, maxWidth: '100%' }}
                                  onClick={(e) => e.stopPropagation()}
                                  onError={(e) => {
                                    console.error('Failed to load audio:', msg.mediaUrl);
                                  }}
                                />
                                {msg.fileName && (
                                  <span style={{ fontSize: '0.75rem', color: 'rgba(255,255,255,0.8)' }}>
                                    {msg.fileName}
                                  </span>
                                )}
                                {msg.mediaUrl && (
                                  <a 
                                    href={msg.mediaUrl} 
                                    download={msg.fileName || 'audio.ogg'}
                                    onClick={(e) => e.stopPropagation()}
                                    style={{ 
                                      padding: '4px 8px', 
                                      background: 'rgba(255,255,255,0.2)', 
                                      color: 'white', 
                                      textDecoration: 'none', 
                                      borderRadius: 4,
                                      fontSize: '0.7rem'
                                    }}
                                  >
                                    ⬇
                                  </a>
                                )}
                              </div>
                              {/* Show text if present */}
                              {msg.text && msg.text.trim() && (
                                <div style={{ marginTop: 8 }}>
                                  {renderTextWithLinks(msg.text)}
                                </div>
                              )}
                            </div>
                          );
                        } else if (msg.hasMedia && msg.mediaUrl && (msg.mediaUrl.match(/\.(jpg|jpeg|png|gif|webp)$/i) || msg.mimeType?.match(/^image\//))) {
                          return (
                            <div>
                              <img 
                                src={msg.mediaUrl} 
                                alt="sent" 
                                style={{ maxWidth: '180px', maxHeight: '150px', borderRadius: 6, objectFit: 'contain', cursor: 'pointer' }} 
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setMediaViewer({ type: 'image', url: msg.mediaUrl, fileName: msg.fileName });
                                }}
                                onError={(e) => {
                                  // If image fails to load, show placeholder or file info
                                  e.target.style.display = 'none';
                                  const parent = e.target.parentElement;
                                  if (parent) {
                                    parent.innerHTML = `
                                      <div style="padding: 6px 8px; border: 1px solid #ccc; border-radius: 6px; font-size: 0.85rem;">
                                        📎 ${msg.fileName && msg.fileName.trim() ? `<span>${msg.fileName}</span>` : '<span>Media file</span>'}
                                        ${msg.fileSize ? `<span style="font-size: 0.75rem; color: #666; margin-left: 4px;">(${(msg.fileSize / 1024).toFixed(1)} KB)</span>` : ''}
                                      </div>
                                    `;
                                  }
                                  console.error('Failed to load image:', msg.mediaUrl);
                                }}
                              />
                              {/* Download button for images */}
                              {msg.mediaUrl && (
                                <div style={{ marginTop: 4, fontSize: '0.75rem' }}>
                                  <a 
                                    href={msg.mediaUrl} 
                                    download={msg.fileName || 'image.jpg'}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      // Force download
                                      e.preventDefault();
                                      const link = document.createElement('a');
                                      link.href = msg.mediaUrl;
                                      link.download = msg.fileName || 'image.jpg';
                                      link.target = '_blank';
                                      document.body.appendChild(link);
                                      link.click();
                                      document.body.removeChild(link);
                                    }}
                                    style={{ color: 'inherit', textDecoration: 'underline', cursor: 'pointer' }}
                                  >
                                    Download
                                  </a>
                                  {msg.fileName && (
                                    <span style={{ fontSize: '0.7rem', color: '#666', marginLeft: '4px' }}>
                                      ({msg.fileName})
                                    </span>
                                  )}
                                  {msg.fileSize && (
                                    <span style={{ fontSize: '0.7rem', color: '#666', marginLeft: '4px' }}>
                                      ({(msg.fileSize / 1024).toFixed(1)} KB)
                                    </span>
                                  )}
                                </div>
                              )}
                              {/* Show text if present */}
                              {msg.text && msg.text.trim() && (
                                <div style={{ marginTop: 8 }}>
                                  {renderTextWithLinks(msg.text)}
                                </div>
                              )}
                            </div>
                          );
                        } else if (msg.hasMedia && msg.mediaUrl) {
                          return (
                            // Display files (PDF, documents, etc.) - similar to Telegram style
                            <div>
                              <div 
                                style={{ 
                                  padding: '8px 12px', 
                                  border: '1px solid rgba(255,255,255,0.3)', 
                                  borderRadius: 8, 
                                  background: 'rgba(255,255,255,0.1)',
                                  cursor: msg.mediaUrl ? 'pointer' : 'default',
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: '12px'
                                }}
                                onClick={(e) => {
                                  if (!msg.mediaUrl) {
                                    e.stopPropagation();
                                    return;
                                  }
                                  e.stopPropagation();
                                  // Open file in new tab
                                  window.open(msg.mediaUrl, '_blank');
                                }}
                              >
                                {/* File icon - circular with document icon */}
                                <div style={{
                                  width: '40px',
                                  height: '40px',
                                  borderRadius: '50%',
                                  background: 'rgba(255,255,255,0.2)',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontSize: '20px',
                                  flexShrink: 0
                                }}>
                                  📄
                                </div>
                                {/* File info */}
                                <div style={{ flex: 1, minWidth: 0 }}>
                                  <div style={{ 
                                    fontWeight: 'bold', 
                                    color: 'white',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    fontSize: '0.9rem'
                                  }}>
                                    {msg.fileName && msg.fileName.trim() ? msg.fileName : 'Media file'}
                                  </div>
                                  {msg.fileSize && (
                                    <div style={{ fontSize: '0.75rem', color: 'rgba(255,255,255,0.7)', marginTop: '2px' }}>
                                      {(msg.fileSize / 1024).toFixed(1)} KB
                                    </div>
                                  )}
                                </div>
                                {/* Download button - only show if mediaUrl exists */}
                                {msg.mediaUrl && (
                                  <a 
                                    href={msg.mediaUrl} 
                                    download={msg.fileName && msg.fileName.trim() ? msg.fileName : 'media'}
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      // Force download
                                      e.preventDefault();
                                      const link = document.createElement('a');
                                      link.href = msg.mediaUrl;
                                      link.download = msg.fileName && msg.fileName.trim() ? msg.fileName : 'media';
                                      document.body.appendChild(link);
                                      link.click();
                                      document.body.removeChild(link);
                                    }}
                                    style={{ 
                                      padding: '6px 12px', 
                                      background: 'rgba(255,255,255,0.2)', 
                                      color: 'white', 
                                      textDecoration: 'none', 
                                      borderRadius: 6,
                                      fontSize: '0.75rem',
                                      cursor: 'pointer',
                                      fontWeight: 'bold',
                                      flexShrink: 0
                                    }}
                                  >
                                    Download
                                  </a>
                                )}
                              </div>
                              {/* Show text if present */}
                              {msg.text && msg.text.trim() && (
                                <div style={{ marginTop: 8 }}>
                                  {renderTextWithLinks(msg.text)}
                                </div>
                              )}
                            </div>
                          );
                        } else if (isUrl(msg.text)) {
                          // Render text with clickable links if it's a URL (and no media)
                          return renderTextWithLinks(msg.text);
                        } else {
                          // Regular text (no media, no link)
                          return msg.text;
                        }
                      })()}
                    </div>
                    <div className="message-time" style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.7rem', marginTop: '0.2rem' }}>
                      <span>{new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      {msg.edited && (
                        <span style={{ fontSize: '0.65rem', color: '#999', fontStyle: 'italic' }}>
                          (edited)
                        </span>
                      )}
                      {/* Message status indicators */}
                      {msg.fromUser && (
                        <span style={{ marginLeft: '4px', fontSize: '0.85rem', display: 'inline-flex', alignItems: 'center' }}>
                          {msg.status === 'failed' ? (
                            <span 
                              style={{ color: '#d32f2f', cursor: 'pointer', fontSize: '0.9rem' }} 
                              title="Failed to send. Click to retry or delete."
                              onClick={(e) => {
                                e.stopPropagation();
                                // Show retry/delete options
                                if (window.confirm('Message failed to send. Retry?')) {
                                  // Retry logic would go here
                                }
                              }}
                            >
                              •
                            </span>
                          ) : msg.status === 'read' ? (
                            <span style={{ color: 'white', fontWeight: 'bold' }} title="Read">✓✓</span>
                          ) : msg.status === 'delivered' ? (
                            <span style={{ color: 'white', fontWeight: 'bold' }} title="Delivered">✓✓</span>
                          ) : msg.isPending ? (
                            <span style={{ color: 'rgba(255,255,255,0.7)', opacity: 0.7 }} title="Sending...">⏳</span>
                          ) : (
                            <span style={{ color: 'white', fontWeight: 'bold' }} title="Sent">✓</span>
                          )}
                        </span>
                      )}
                    </div>
                    </div>
                    {/* Multi-select checkbox - positioned to the extreme right */}
                    {isMultiSelectMode && (
                      <div 
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          setSelectedMessages(prev => {
                            const updated = new Set(prev);
                            if (updated.has(msg.messageId)) {
                              updated.delete(msg.messageId);
                            } else {
                              updated.add(msg.messageId);
                            }
                            return updated;
                          });
                        }}
                        style={{
                          position: 'absolute',
                          right: '0',
                          top: '0.5rem',
                          width: '20px',
                          height: '20px',
                          borderRadius: '50%',
                          border: '2px solid #667eea',
                          backgroundColor: selectedMessages.has(msg.messageId) ? '#667eea' : 'transparent',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          flexShrink: 0,
                          cursor: 'pointer',
                          transition: 'background-color 0.2s'
                        }}
                      >
                        {selectedMessages.has(msg.messageId) && (
                          <span style={{ color: 'white', fontSize: '12px', fontWeight: 'bold' }}>✓</span>
                        )}
                      </div>
                    )}
                  </div>
                  );
                  })
                })()
              )}
            </div>

            {/* New Message Notification */}
            {showNewMessageNotification && (
              <div 
                style={{
                  position: 'absolute',
                  bottom: '60px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  background: '#667eea',
                  color: 'white',
                  padding: '8px 16px',
                  borderRadius: '20px',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
                  zIndex: 100,
                  animation: 'pulse 2s infinite'
                }}
                onClick={() => {
                  const container = messagesContainerRef.current;
                  if (container) {
                    container.scrollTop = container.scrollHeight;
                    setShowNewMessageNotification(false);
                  }
                }}
              >
                New message
              </div>
            )}

            {/* Scroll to Bottom Button */}
            {showScrollToBottom && (
              <button
                style={{
                  position: 'absolute',
                  bottom: (() => {
                    let offset = 60; // Base offset
                    if (pendingMedia) offset += 60; // Add space for media preview
                    if (replyingTo) offset += 50; // Add space for reply indicator
                    return `${offset}px`;
                  })(),
                  right: '20px',
                  width: '40px',
                  height: '40px',
                  borderRadius: '50%',
                  background: '#667eea',
                  color: 'white',
                  border: 'none',
                  cursor: 'pointer',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
                  zIndex: 100,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '1.2rem'
                }}
                onClick={() => {
                  const container = messagesContainerRef.current;
                  if (container) {
                    container.scrollTop = container.scrollHeight;
                    setShowScrollToBottom(false);
                    setShowNewMessageNotification(false);
                  }
                }}
                title="Scroll to bottom"
              >
                ↓
              </button>
            )}

            {/* Edit form - shows when a message is selected for editing */}
            {selectedMessageId && (
              <div className="edit-form-container" style={{ 
                marginBottom: '10px', 
                padding: '10px', 
                backgroundColor: '#f0f0f0', 
                borderRadius: '8px',
                border: '1px solid #ddd'
              }}>
                <div style={{ marginBottom: '8px', fontWeight: 'bold' }}>
                  Editing message:
                </div>
                {selectedMessageHasMedia && (
                  <div style={{ marginBottom: '8px' }}>
                    <label className="btn btn-secondary" style={{ marginRight: '8px' }}>
                      Replace Media
                      <input
                        type="file"
                        accept="image/*,video/*,audio/*,application/pdf"
                        style={{ display: 'none' }}
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (!file) return;
                          const preview = URL.createObjectURL(file);
                          setPendingMediaReplacement({ file, preview });
                          e.target.value = '';
                        }}
                      />
                    </label>
                    {pendingMediaReplacement && (
                      <button
                        className="btn btn-secondary"
                        onClick={() => {
                          if (pendingMediaReplacement.preview) {
                            URL.revokeObjectURL(pendingMediaReplacement.preview);
                          }
                          setPendingMediaReplacement(null);
                        }}
                      >
                        Remove New Media
                      </button>
                    )}
                    {pendingMediaReplacement && (
                      <div style={{ marginTop: '8px', padding: '8px', background: '#fff', borderRadius: 4 }}>
                        {pendingMediaReplacement.preview && pendingMediaReplacement.file.type.startsWith('image/') ? (
                          <img src={pendingMediaReplacement.preview} alt="Preview" style={{ maxWidth: '100px', maxHeight: '100px', borderRadius: 4 }} />
                        ) : (
                          <span>📎 {pendingMediaReplacement.file.name}</span>
                        )}
                      </div>
                    )}
                  </div>
                )}
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <input
                    type="text"
                    value={editText}
                    onChange={(e) => setEditText(e.target.value)}
                    placeholder={selectedMessageHasMedia ? "Edit caption (optional)..." : "Edit message..."}
                    className="message-input"
                    style={{ flex: 1 }}
                    autoFocus
                  />
                  <button
                    type="button"
                    onClick={handleEditLast}
                    className="btn btn-primary"
                    disabled={!selectedMessageHasMedia && !editText.trim() && !pendingMediaReplacement}
                  >
                    {pendingMediaReplacement ? 'Replace Media' : 'Save Edit'}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedMessageId(null);
                      setEditText('');
                      setPendingMediaReplacement(null);
                      if (pendingMediaReplacement?.preview) {
                        URL.revokeObjectURL(pendingMediaReplacement.preview);
                      }
                    }}
                    className="btn btn-secondary"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            {pendingMedia && (
              <div style={{ marginBottom: '0.5rem', padding: '0.4rem 0.5rem', background: '#f0f0f0', borderRadius: 6, fontSize: '0.85rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  {pendingMedia.preview && pendingMedia.file.type.startsWith('image/') ? (
                    <img src={pendingMedia.preview} alt="Preview" style={{ maxWidth: '60px', maxHeight: '60px', borderRadius: 4 }} />
                  ) : (
                    <div style={{ padding: '4px 6px', background: '#fff', borderRadius: 4, fontSize: '0.8rem', display: 'flex', alignItems: 'center', justifyContent: 'center', minWidth: '40px', minHeight: '40px' }}>
                      📎
                    </div>
                  )}
                  <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0 }}>
                    <span style={{ fontSize: '0.85rem', color: '#333', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {pendingMedia.file.name}
                    </span>
                    <span style={{ fontSize: '0.75rem', color: '#666' }}>
                      ({(pendingMedia.file.size / 1024).toFixed(1)} KB)
                    </span>
                  </div>
                  <button
                    onClick={() => {
                      setPendingMedia(null);
                      if (pendingMedia.preview) {
                        URL.revokeObjectURL(pendingMedia.preview);
                      }
                    }}
                    style={{
                      background: 'transparent',
                      border: 'none',
                      cursor: 'pointer',
                      padding: '4px 8px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#666',
                      fontSize: '18px',
                      lineHeight: 1,
                      borderRadius: '4px',
                      flexShrink: 0,
                    }}
                    onMouseEnter={(e) => e.target.style.background = '#e0e0e0'}
                    onMouseLeave={(e) => e.target.style.background = 'transparent'}
                    title="Remove media"
                  >
                    ✕
                  </button>
                </div>
              </div>
            )}
            {/* Reply indicator */}
            {replyingTo && (
              <div style={{ 
                padding: '0.5rem 0.75rem', 
                background: '#e3f2fd', 
                borderRadius: 6, 
                marginBottom: '0.5rem',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                fontSize: '0.85rem'
              }}>
                <div>
                  <strong>Replying to:</strong> {replyingTo.hasMedia ? (replyingTo.fileName ? `"${replyingTo.fileName}"` : 'Media') : (replyingTo.text || 'Message')}
                </div>
                <button
                  className="btn btn-secondary btn-sm"
                  style={{ padding: '0.2rem 0.4rem', fontSize: '0.7rem' }}
                  onClick={() => setReplyingTo(null)}
                >
                  ✕
                </button>
              </div>
            )}
            <form onSubmit={handleSendMessage} className="message-form" style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <label style={{ 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center',
                padding: '8px',
                cursor: 'pointer',
                borderRadius: '50%',
                background: '#f0f0f0',
                width: '36px',
                height: '36px',
                flexShrink: 0
              }}>
                📎
                <input
                  type="file"
                  accept="image/*,video/*,audio/*,application/pdf"
                  style={{ display: 'none' }}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (!file) return;
                    
                    // Create preview URL
                    const preview = URL.createObjectURL(file);
                    setPendingMedia({ file, preview });
                    
                    // Clear file input
                    e.target.value = '';
                  }}
                />
              </label>
              <input
                ref={messageInputRef}
                type="text"
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                placeholder={replyingTo 
                  ? `Replying to: ${replyingTo.text || 'message'}...` 
                  : pendingMedia 
                    ? "Add a caption (optional)..." 
                    : "Type a message..."}
                className="message-input"
                disabled={selectedMessageId !== null}
                style={{ flex: 1 }}
              />
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', marginRight: '0.5rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', fontSize: '0.75rem', cursor: 'pointer', userSelect: 'none' }}>
                  <input
                    type="checkbox"
                    checked={showMultipleSuggestions}
                    onChange={(e) => setShowMultipleSuggestions(e.target.checked)}
                    style={{ marginRight: '0.25rem', cursor: 'pointer' }}
                    title="Get multiple suggestions"
                  />
                  <span style={{ color: '#666' }}>Multiple</span>
                </label>
                <button 
                  type="button" 
                  onClick={async () => {
                    try {
                      setLoadingSuggestion(true);
                      setError(null);
                      setAiSuggestion(null);
                      setAiSuggestions(null);
                      const response = await conversationApi.getSuggestion(targetId, userId, subtargetUserId, showMultipleSuggestions);
                      if (response.data?.success) {
                        if (showMultipleSuggestions && Array.isArray(response.data.data)) {
                          setAiSuggestions(response.data.data);
                        } else {
                          setAiSuggestion(response.data.data);
                        }
                      } else {
                        setError(response.data?.error || 'Failed to generate suggestion');
                      }
                    } catch (err) {
                      setError(err.response?.data?.error || err.message || 'Failed to generate AI suggestion');
                    } finally {
                      setLoadingSuggestion(false);
                    }
                  }}
                  className="btn btn-secondary"
                  disabled={selectedMessageId !== null || loadingSuggestion || !conversationInitialized}
                  title="Get AI suggestion"
                >
                  {loadingSuggestion ? '...' : 'AI'}
                </button>
              </div>
              <button 
                type="button" 
                onClick={handleSendMessage} 
                className="btn btn-primary"
                disabled={selectedMessageId !== null || (!pendingMedia && !newMessage.trim())}
              >
                {pendingMedia ? 'Send Media' : 'Send'}
              </button>
            </form>
          </div>
        )}

        {/* AI Suggestion Display - Single */}
        {aiSuggestion && !aiSuggestions && (
          <div style={{
            margin: '1rem',
            padding: '1rem',
            backgroundColor: '#f0f7ff',
            border: '1px solid #4a90e2',
            borderRadius: '8px',
            position: 'relative'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
              <div style={{ fontWeight: 'bold', color: '#4a90e2', fontSize: '0.9rem' }}>
                🤖 AI Suggestion
              </div>
              <button
                type="button"
                onClick={() => {
                  setAiSuggestion(null);
                  setAiSuggestions(null);
                }}
                style={{
                  background: 'none',
                  border: 'none',
                  fontSize: '1.2rem',
                  cursor: 'pointer',
                  color: '#666',
                  padding: '0',
                  lineHeight: '1'
                }}
                title="Close"
              >
                ×
              </button>
            </div>
            <div style={{
              padding: '0.75rem',
              backgroundColor: 'white',
              borderRadius: '4px',
              marginBottom: '0.5rem',
              fontSize: '0.95rem',
              lineHeight: '1.5',
              whiteSpace: 'pre-wrap',
              wordWrap: 'break-word'
            }}>
              {aiSuggestion}
            </div>
            <button
              type="button"
              onClick={() => {
                setNewMessage(aiSuggestion);
                setAiSuggestion(null);
                setAiSuggestions(null);
                // Focus the input field
                setTimeout(() => {
                  if (messageInputRef.current) {
                    messageInputRef.current.focus();
                    // Move cursor to end
                    messageInputRef.current.setSelectionRange(
                      messageInputRef.current.value.length,
                      messageInputRef.current.value.length
                    );
                  }
                }, 100);
              }}
              className="btn btn-primary btn-sm"
              style={{ width: '100%' }}
            >
              Copy to Message Field
            </button>
          </div>
        )}

        {/* AI Suggestions Display - Multiple (Popup) */}
        {aiSuggestions && Array.isArray(aiSuggestions) && aiSuggestions.length > 0 && (
          <div style={{
            position: 'fixed',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            backgroundColor: 'white',
            border: '2px solid #4a90e2',
            borderRadius: '12px',
            padding: '1.5rem',
            maxWidth: '600px',
            width: '90%',
            maxHeight: '80vh',
            overflowY: 'auto',
            zIndex: 1000,
            boxShadow: '0 4px 20px rgba(0,0,0,0.3)'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <div style={{ fontWeight: 'bold', color: '#4a90e2', fontSize: '1.1rem' }}>
                🤖 AI Suggestions ({aiSuggestions.length})
              </div>
              <button
                type="button"
                onClick={() => {
                  setAiSuggestions(null);
                  setAiSuggestion(null);
                }}
                style={{
                  background: 'none',
                  border: 'none',
                  fontSize: '1.5rem',
                  cursor: 'pointer',
                  color: '#666',
                  padding: '0',
                  lineHeight: '1',
                  width: '30px',
                  height: '30px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
                title="Close"
              >
                ×
              </button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              {aiSuggestions.map((suggestion, index) => (
                <div
                  key={index}
                  style={{
                    padding: '1rem',
                    backgroundColor: '#f9f9f9',
                    border: '1px solid #ddd',
                    borderRadius: '8px',
                    position: 'relative'
                  }}
                >
                  <div style={{
                    padding: '0.75rem',
                    backgroundColor: 'white',
                    borderRadius: '4px',
                    marginBottom: '0.75rem',
                    fontSize: '0.95rem',
                    lineHeight: '1.5',
                    whiteSpace: 'pre-wrap',
                    wordWrap: 'break-word',
                    minHeight: '60px'
                  }}>
                    {suggestion}
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setNewMessage(suggestion);
                      setAiSuggestions(null);
                      setAiSuggestion(null);
                      // Focus the input field
                      setTimeout(() => {
                        if (messageInputRef.current) {
                          messageInputRef.current.focus();
                          // Move cursor to end
                          messageInputRef.current.setSelectionRange(
                            messageInputRef.current.value.length,
                            messageInputRef.current.value.length
                          );
                        }
                      }, 100);
                    }}
                    className="btn btn-primary btn-sm"
                    style={{ width: '100%' }}
                  >
                    Copy to Message Field
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Backdrop for popup */}
        {aiSuggestions && Array.isArray(aiSuggestions) && aiSuggestions.length > 0 && (
          <div
            style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              backgroundColor: 'rgba(0, 0, 0, 0.5)',
              zIndex: 999
            }}
            onClick={() => {
              setAiSuggestions(null);
              setAiSuggestion(null);
            }}
          />
        )}
        
        {/* Right-click Context Menu */}
        {contextMenu && !isMultiSelectMode && (
          <>
            <div 
              style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                zIndex: 999
              }}
              onClick={() => setContextMenu(null)}
            />
            <div
              style={{
                position: 'fixed',
                left: `${contextMenu.x}px`,
                top: `${contextMenu.y}px`,
                background: 'white',
                border: '1px solid #e0e0e0',
                borderRadius: '8px',
                boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                zIndex: 1000,
                minWidth: '150px',
                padding: '4px 0',
                maxHeight: '90vh',
                overflowY: 'auto'
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <div
                style={{ padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' }}
                onClick={() => {
                  setReplyingTo({
                    messageId: contextMenu.messageId,
                    text: contextMenu.hasMedia ? (contextMenu.fileName || 'Media') : (contextMenu.text || 'Message'),
                    fromUser: contextMenu.fromUser,
                    hasMedia: contextMenu.hasMedia || false,
                    fileName: contextMenu.fileName || null
                  });
                  setContextMenu(null);
                  setTimeout(() => {
                    if (messageInputRef.current) {
                      messageInputRef.current.focus();
                    }
                  }, 100);
                }}
                onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
                onMouseLeave={(e) => e.target.style.background = 'transparent'}
              >
                ↶ Reply
              </div>
              {contextMenu.fromUser && (
                <div
                  style={{ padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' }}
                  onClick={() => {
                    setSelectedMessageId(contextMenu.messageId);
                    setEditText(contextMenu.text || '');
                    setSelectedMessageHasMedia(contextMenu.hasMedia || false);
                    setPendingMediaReplacement(null);
                    setContextMenu(null);
                  }}
                  onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
                  onMouseLeave={(e) => e.target.style.background = 'transparent'}
                >
                  ✏️ Edit
                </div>
              )}
              <div
                style={{ padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' }}
                onClick={async () => {
                  try {
                    // Check if message is pinned (check both messages and pinnedMessages arrays)
                    const msg = messages.find(m => m.messageId === contextMenu.messageId);
                    const pinnedMsg = pinnedMessages.find(pm => pm.messageId === contextMenu.messageId);
                    const isPinned = msg?.pinned || pinnedMsg !== undefined;
                    await conversationApi.pin(targetId, userId, contextMenu.messageId, !isPinned, subtargetUserId);
                    // Reload messages to get updated pinned status
                    await loadMessages();
                  } catch (err) {
                    console.error('Error pinning message:', err);
                    setError('Failed to pin message');
                    setTimeout(() => setError(null), 3000);
                  }
                  setContextMenu(null);
                }}
                onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
                onMouseLeave={(e) => e.target.style.background = 'transparent'}
              >
                {(messages.find(m => m.messageId === contextMenu.messageId)?.pinned || pinnedMessages.some(pm => pm.messageId === contextMenu.messageId)) ? '📌 Unpin' : '📌 Pin'}
              </div>
              <div
                style={{ padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' }}
                onClick={() => {
                  // Copy text
                  const textToCopy = contextMenu.text || (contextMenu.hasMedia ? 'Media' : 'Message');
                  navigator.clipboard.writeText(textToCopy).then(() => {
                    // Show brief feedback
                    setError('Text copied to clipboard');
                    setTimeout(() => setError(null), 2000);
                  });
                  setContextMenu(null);
                }}
                onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
                onMouseLeave={(e) => e.target.style.background = 'transparent'}
              >
                📋 Copy Text
              </div>
              <div
                style={{ padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' }}
                onClick={() => {
                  handleDelete(contextMenu.messageId);
                  setContextMenu(null);
                }}
                onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
                onMouseLeave={(e) => e.target.style.background = 'transparent'}
              >
                🗑️ Delete
              </div>
              <div style={{ borderTop: '1px solid #e0e0e0', margin: '4px 0' }} />
              <div
                style={{ padding: '8px 16px', cursor: 'pointer', fontSize: '0.9rem' }}
                onClick={() => {
                  setIsMultiSelectMode(true);
                  setSelectedMessages(new Set([contextMenu.messageId]));
                  setContextMenu(null);
                }}
                onMouseEnter={(e) => e.target.style.background = '#f5f5f5'}
                onMouseLeave={(e) => e.target.style.background = 'transparent'}
              >
                ✓ Select
              </div>
            </div>
          </>
        )}


        {/* Media Viewer Modal */}
        {mediaViewer && (
          <div 
            className="modal-overlay" 
            style={{ 
              position: 'fixed', 
              inset: 0, 
              background: 'rgba(0,0,0,0.95)', 
              zIndex: 2000,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
            onClick={() => setMediaViewer(null)}
          >
            <div 
              style={{ 
                position: 'relative', 
                maxWidth: '90vw', 
                maxHeight: '90vh',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center'
              }}
              onClick={(e) => e.stopPropagation()}
            >
              <button
                onClick={() => setMediaViewer(null)}
                style={{
                  position: 'absolute',
                  top: '-40px',
                  right: 0,
                  background: 'rgba(255,255,255,0.2)',
                  border: 'none',
                  color: 'white',
                  fontSize: '24px',
                  width: '32px',
                  height: '32px',
                  borderRadius: '50%',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                ×
              </button>
              {mediaViewer.type === 'image' ? (
                <img 
                  src={mediaViewer.url} 
                  alt={mediaViewer.fileName || 'Media'} 
                  style={{ 
                    maxWidth: '90vw', 
                    maxHeight: '90vh', 
                    objectFit: 'contain',
                    borderRadius: 8
                  }} 
                />
              ) : mediaViewer.type === 'video' ? (
                <video 
                  src={mediaViewer.url} 
                  controls 
                  autoPlay
                  style={{ 
                    maxWidth: '90vw', 
                    maxHeight: '90vh',
                    borderRadius: 8
                  }} 
                />
              ) : null}
              {mediaViewer.fileName && (
                <div style={{ marginTop: '16px', color: 'white', fontSize: '0.9rem' }}>
                  {mediaViewer.fileName}
                </div>
              )}
              <a
                href={mediaViewer.url}
                download={mediaViewer.fileName || 'media'}
                style={{
                  marginTop: '12px',
                  padding: '8px 16px',
                  background: '#667eea',
                  color: 'white',
                  textDecoration: 'none',
                  borderRadius: 6,
                  fontSize: '0.9rem'
                }}
              >
                Download
              </a>
            </div>
          </div>
        )}

        {/* Delete Confirmation Modal */}
        {deleteModal && (
          <div className="modal-overlay" onClick={cancelDelete}>
            <div className="modal" onClick={(e) => e.stopPropagation()}>
              <h3>Delete Message</h3>
              <p>Do you want to delete this message?</p>
              <div className="form-group">
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={deleteModal.revoke}
                    onChange={(e) => setDeleteModal({ ...deleteModal, revoke: e.target.checked })}
                  />
                  <span>Also delete for {target?.name || 'target user'}</span>
                </label>
              </div>
              <div className="modal-actions">
                <button onClick={cancelDelete} className="btn btn-secondary">Cancel</button>
                <button onClick={performDelete} className="btn btn-danger">Delete</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default ConversationView;

