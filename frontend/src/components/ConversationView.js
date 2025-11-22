import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { targetApi, conversationApi, platformApi } from '../services/api';
import './ConversationView.css';

function ConversationView({ userId = 1 }) {
  const { targetId } = useParams();
  const navigate = useNavigate();
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
  const messageInputRef = useRef(null); // Ref for message input field to focus when replying

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
        await loadMessages();
      } catch (e) {
        // Try to load messages anyway
        console.error('Failed to check active conversation:', e);
        loadMessages().catch(err => console.error('Failed to load messages:', err));
      }
    })();
  }, [targetId]);

  // Poll for online status when conversation is initialized
  useEffect(() => {
    if (!conversationInitialized || !target) return;
    
    const checkOnlineStatus = async () => {
      try {
        const response = await targetApi.checkOnlineStatus(targetId, userId);
        if (response.data?.success) {
          const data = response.data.data;
          setTargetOnline(data.online === true);
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
  }, [conversationInitialized, target, targetId, userId]);

  // Poll for new messages and trigger priority ingestion every 5 seconds
  useEffect(() => {
    if (!conversationInitialized || !targetId || isOperationInProgress) return; // Don't poll if operation is in progress
    
    const pollForNewMessages = async () => {
      // Skip polling if an operation (delete/edit) is in progress
      if (isOperationInProgress) {
        return;
      }
      
      try {
        // Trigger priority ingestion every 5 seconds to sync with Telegram
        // This will check for new messages and delete messages that no longer exist in Telegram
        // Only trigger if not already running (backend will check and skip if already running)
        try {
          await conversationApi.ingestTarget(targetId, userId);
        } catch (ingestErr) {
          // Ignore ingestion errors - it's a background process
          // The backend will skip if ingestion is already running
          if (ingestErr.response?.status !== 200) {
            console.warn('Priority ingestion failed:', ingestErr);
          }
        }
        
        // Then fetch messages from database
        const resp = await conversationApi.getMessages(targetId, userId, 50);
        if (resp.data?.success) {
          const rows = resp.data.data || [];
          if (rows.length > 0) {
            // Always update messages (in case order changed or messages were edited/deleted)
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
            }));
            
            // Always sync messages to reflect deletions and updates
            // But preserve edited messages that might not be updated in DB yet
            setMessages(prev => {
              // Create maps for quick lookup
              const prevMap = new Map(prev.map(m => [m.messageId, m]));
              
              // Merge loaded messages with previous state, preserving edited messages
              // If a message was marked as edited in previous state or was recently edited, preserve it
              const syncedMessages = loadedMessages.map(newMsg => {
                const prevMsg = prevMap.get(newMsg.messageId);
                const isRecentlyEdited = recentlyEditedMessages.has(newMsg.messageId);
                
                if (prevMsg) {
                  // If this message was recently edited (within last 5 seconds), keep the previous version
                  // This prevents polling from overwriting edits before database is updated
                  if (isRecentlyEdited) {
                    return prevMsg; // Keep the edited version from previous state
                  }
                  
                  // If the message was previously edited and the text matches, keep it as edited
                  if (prevMsg.edited && prevMsg.text === newMsg.text) {
                    return { ...newMsg, edited: true };
                  }
                  
                  // If text changed, it was edited - use the new text from DB and mark as edited
                  if (prevMsg.text !== newMsg.text) {
                    return { ...newMsg, edited: true };
                  }
                }
                return newMsg;
              });
              
              const prevIds = new Set(prev.map(m => m.messageId));
              const newIds = new Set(syncedMessages.map(m => m.messageId));
              const prevHighest = prev.length > 0 ? Math.max(...prev.map(m => m.messageId || 0)) : 0;
              const newHighest = syncedMessages.length > 0 ? Math.max(...syncedMessages.map(m => m.messageId || 0)) : 0;
              
              // Check if messages are different (added, removed, or edited)
              const hasNewMessages = newHighest > prevHighest;
              const hasRemovedMessages = prevIds.size > newIds.size;
              
              // Check if any existing message was edited (text changed)
              let hasEditedMessages = false;
              for (const newMsg of syncedMessages) {
                const prevMsg = prevMap.get(newMsg.messageId);
                if (prevMsg && prevMsg.text !== newMsg.text) {
                  hasEditedMessages = true;
                  break;
                }
              }
              
              const hasChanged = hasNewMessages || hasRemovedMessages || hasEditedMessages || 
                prevIds.size !== newIds.size || syncedMessages.length !== prev.length;
              
              // Update if messages changed (new, deleted, or edited)
              if (hasChanged) {
                // Auto-scroll to bottom if we're near the bottom and have new messages
                setTimeout(() => {
                  const messagesContainer = document.querySelector('.messages-container');
                  if (messagesContainer && hasNewMessages) {
                    const isNearBottom = messagesContainer.scrollHeight - messagesContainer.scrollTop - messagesContainer.clientHeight < 200;
                    if (isNearBottom) {
                      messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    }
                  }
                }, 100);
                // Return synced messages (preserving edited state where appropriate)
                return syncedMessages;
              }
              return prev; // No changes, keep previous messages
            });
          }
        }
      } catch (err) {
        // Silently fail - polling errors are not critical
        console.error('Failed to poll for new messages:', err);
      }
    };

    // Poll immediately (to check for new messages)
    pollForNewMessages();
    
    // Then poll every 5 seconds for new messages (only if no operation in progress)
    // This also triggers priority ingestion to sync with Telegram
    const messageInterval = setInterval(() => {
      if (!isOperationInProgress) {
        pollForNewMessages();
      }
    }, 5000); // Changed to 5 seconds to give ingestion time to complete
    
    return () => clearInterval(messageInterval);
  }, [conversationInitialized, targetId, userId, isOperationInProgress]); // Include isOperationInProgress to pause/resume polling

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
      const response = await conversationApi.initialize(targetId, payload, userId);
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
      setNewMessage('');
      setPendingMedia(null); // Clear pending media immediately for better UX

      try {
        const response = await conversationApi.sendMediaWithText(
          targetId, 
          userId, 
          file, 
          messageText,
          replyingTo?.messageId
        );
        if (response.data?.success && response.data?.data) {
          const messageData = response.data.data;
          const newMsg = {
            text: messageData.text || messageText || null,
            fromUser: messageData.fromUser !== undefined ? messageData.fromUser : true,
            timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
            mediaUrl: messageData.mediaDownloadUrl || null,
            messageId: messageData.messageId,
            hasMedia: messageData.hasMedia || true,
            fileName: messageData.fileName || null,
            mimeType: messageData.mimeType || null,
            fileSize: messageData.fileSize || null, // Include file size
            referenceId: replyingTo?.messageId || null,
          };
          setMessages(prev => [...prev, newMsg]);
          setReplyingTo(null); // Clear reply after sending
        } else {
          await loadMessages();
        }
      } catch (err) {
        setError(err.response?.data?.error || err.message || 'Failed to send media');
        // Restore state if sending failed
        setPendingMedia({ file, preview: pendingMedia.preview });
        setNewMessage(messageText || '');
      }
    } else {
      // Send text-only message
      if (!newMessage.trim()) return;

      const messageText = newMessage.trim();
      setNewMessage(''); // Clear input immediately for better UX

      try {
        const response = await conversationApi.respond(
          targetId, 
          messageText, 
          userId,
          replyingTo?.messageId
        );
        // If response includes message data, add it immediately to the UI
        if (response.data?.success && response.data?.data) {
          const messageData = response.data.data;
          const newMsg = {
            text: messageData.text || messageText,
            fromUser: messageData.fromUser !== undefined ? messageData.fromUser : true,
            timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
            mediaUrl: messageData.mediaDownloadUrl || null,
            messageId: messageData.messageId,
            referenceId: replyingTo?.messageId || null,
          };
          // Add to messages list immediately
          setMessages(prev => [...prev, newMsg]);
          setReplyingTo(null); // Clear reply after sending
        } else {
          // Fallback: reload all messages if no message data in response
          await loadMessages();
        }
      } catch (err) {
        setError(err.response?.data?.error || err.message || 'Failed to send message');
        // Restore message text if sending failed
        setNewMessage(messageText);
      }
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
        const response = await conversationApi.replaceMedia(targetId, userId, msgId, file, messageText);
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
    
    setEditText('');
    setSelectedMessageId(null);
    
    // Pause polling while edit is in progress
    setIsOperationInProgress(true);
    
    try {
      let response;
      if (msgId) {
        response = await conversationApi.edit(targetId, userId, msgId, messageText);
      } else {
        response = await conversationApi.editLast(targetId, userId, messageText);
      }
      
      // Wait a bit to ensure database update is committed
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // If response includes updated message data, update it in the UI
      if (response.data?.success && response.data?.data) {
        const messageData = response.data.data;
        const updatedMsg = {
          text: messageData.text || messageText || null,
          fromUser: messageData.fromUser !== undefined ? messageData.fromUser : true,
          timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
          mediaUrl: messageData.mediaDownloadUrl || null,
          messageId: messageData.messageId,
          edited: messageData.edited || true, // Mark as edited
          hasMedia: messageData.hasMedia || false,
          fileName: messageData.fileName || null,
          mimeType: messageData.mimeType || null,
        };
        
        // Update the message in the messages list, preserving media info
        // Use functional update to ensure we're working with the latest state
        setMessages(prev => {
          const updated = prev.map(msg => {
            if (msg.messageId === updatedMsg.messageId) {
              // Preserve media info if the original message had media
              // This ensures media shows even if backend doesn't return hasMedia=true
              if (msg.hasMedia || msg.mediaUrl) {
                return { 
                  ...updatedMsg, 
                  hasMedia: true, // Ensure hasMedia is true if original had media
                  mediaUrl: updatedMsg.mediaUrl || msg.mediaUrl, // Use new URL if available, otherwise keep old
                  fileName: updatedMsg.fileName || msg.fileName,
                  mimeType: updatedMsg.mimeType || msg.mimeType,
                  edited: true // Explicitly mark as edited
                };
              }
              return { ...updatedMsg, edited: true }; // Explicitly mark as edited
            }
            return msg;
          });
          // Force re-render by returning a new array reference
          return updated;
        });
        
        // Mark this message as recently edited to prevent polling from overwriting it
        setRecentlyEditedMessages(prev => new Set(prev).add(updatedMsg.messageId));
        // After 5 seconds, allow polling to sync normally (database should be updated by then)
        setTimeout(() => {
          setRecentlyEditedMessages(prev => {
            const next = new Set(prev);
            next.delete(updatedMsg.messageId);
            return next;
          });
        }, 5000);
      } else {
        // Fallback: update locally and reload
        setMessages(prev => prev.map(msg => 
          msg.messageId === msgId ? { ...msg, text: messageText, edited: true } : msg
        ));
        // Mark as recently edited even for fallback
        setRecentlyEditedMessages(prev => new Set(prev).add(msgId));
        setTimeout(() => {
          setRecentlyEditedMessages(prev => {
            const next = new Set(prev);
            next.delete(msgId);
            return next;
          });
        }, 5000);
        setTimeout(loadMessages, 300);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to edit message');
      // Restore edit state if editing failed
      setSelectedMessageId(msgId);
      setEditText(messageText);
    } finally {
      // Resume polling after edit completes (with a delay to ensure DB is updated)
      setTimeout(() => {
        setIsOperationInProgress(false);
      }, 2000); // Wait 2 seconds to ensure database is updated before resuming polling
    }
  };

  const loadMessages = async () => {
    try {
      // Auto-load last 50 messages
      const resp = await conversationApi.getMessages(targetId, userId, 50);
      if (resp.data?.success) {
        const rows = resp.data.data || [];
        const loadedMessages = rows.map((r) => ({
          text: r.text || '',
          fromUser: !!r.fromUser,
          timestamp: r.timestamp ? new Date(r.timestamp) : new Date(),
          mediaUrl: r.mediaDownloadUrl || null,
          messageId: r.messageId,
          hasMedia: r.hasMedia || false,
          fileName: r.fileName || null,
          mimeType: r.mimeType || null,
          edited: r.edited || false, // Preserve edited flag from database or API
          referenceId: r.referenceId || null, // Include reference_id for replies
          fileSize: r.fileSize || null, // Include file size
        }));
        setMessages(loadedMessages);
        console.log(`Loaded ${loadedMessages.length} messages for target ${targetId}`);
        
        // Auto-scroll to bottom after messages are loaded
        setTimeout(() => {
          const messagesContainer = document.querySelector('.messages-container');
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
    
    // Pause polling while delete is in progress
    setIsOperationInProgress(true);
    
    // Remove message from UI optimistically (immediately)
    setMessages(prev => prev.filter(m => m.messageId !== messageIdToDelete));
    
    try {
      const response = await conversationApi.delete(targetId, userId, messageIdToDelete, revoke);
      
      // Wait a bit to ensure database update is committed
      await new Promise(resolve => setTimeout(resolve, 500));
      
      if (response.data?.success) {
        if (response.data?.data?.message) {
          // Show info if message was only deleted for user (not revoked)
          setError(response.data.data.message);
          setTimeout(() => setError(null), 5000);
        }
      } else {
        // Show error if deletion failed - message will be restored by polling
        setError(response.data?.error || 'Failed to delete message');
        setTimeout(() => setError(null), 5000);
        // Reload messages to restore the deleted message if deletion failed
        setTimeout(() => loadMessages(), 1000);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete message');
      setTimeout(() => setError(null), 5000);
      // Reload messages to restore the deleted message if deletion failed
      setTimeout(() => loadMessages(), 1000);
    } finally {
      // Resume polling after delete completes (with a delay to ensure DB is updated)
      setTimeout(() => {
        setIsOperationInProgress(false);
      }, 2000); // Wait 2 seconds to ensure database is updated before resuming polling
    }
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
          <div className="conversation-container">
            <div className="conversation-header">
              <h2>
                {target?.name || 'Conversation'}
                {targetOnline ? (
                  <span className="online-indicator" title="Online"></span>
                ) : (
                  <span className="offline-indicator" title={lastActive || "Offline"}></span>
                )}
                {!targetOnline && lastActive && (
                  <span style={{ fontSize: '0.8rem', color: '#666', marginLeft: '0.5rem' }}>
                    {lastActive}
                  </span>
                )}
              </h2>
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
            <div className="toolbar" style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
              <label className="btn btn-secondary" style={{ marginLeft: 8 }}>
                Upload Media
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
              {pendingMedia && (
                <button
                  className="btn btn-secondary"
                  style={{ marginLeft: 8 }}
                  onClick={() => {
                    setPendingMedia(null);
                    if (pendingMedia.preview) {
                      URL.revokeObjectURL(pendingMedia.preview);
                    }
                  }}
                >
                  Remove Media
                </button>
              )}
            </div>
            <div className="messages-container">
              {messages.length === 0 ? (
                <div className="empty-messages">
                  <p>No messages yet. {conversationInitialized ? 'Start the conversation!' : 'Messages will appear here once ingestion is complete.'}</p>
                </div>
              ) : (
                messages.map((msg, idx) => {
                  // Find the message being replied to (if any)
                  const repliedToMsg = msg.referenceId ? messages.find(m => m.messageId === msg.referenceId) : null;
                  
                  return (
                  <div 
                    key={idx} 
                    className={`message ${msg.fromUser ? 'from-user' : 'from-target'}`}
                    onTouchStart={(e) => {
                      // Track touch start for swipe-to-reply
                      if (!msg.fromUser) { // Only allow replying to target's messages
                        e.touchStartY = e.touches[0].clientY;
                      }
                    }}
                    onTouchEnd={(e) => {
                      // Swipe up to reply
                      if (!msg.fromUser && e.changedTouches[0]) {
                        const touchEndY = e.changedTouches[0].clientY;
                        const touchStartY = e.touchStartY;
                        if (touchStartY && touchEndY < touchStartY - 50) { // Swipe up more than 50px
                          setReplyingTo({
                            messageId: msg.messageId,
                            text: msg.text || (msg.hasMedia ? 'Media' : 'Message'),
                            fromUser: msg.fromUser
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
                    style={{ 
                      cursor: !msg.fromUser ? 'pointer' : 'default',
                      position: 'relative'
                    }}
                  >
                    {/* Reply indicator - show message being replied to */}
                    {repliedToMsg && (
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
                        onClick={() => {
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
                        }}
                        title="Click to scroll to referenced message"
                      >
                        {repliedToMsg.fromUser ? 'You' : target?.name}: {repliedToMsg.text || (repliedToMsg.hasMedia ? 'Media' : 'Message')}
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
                        Replying to a message not in recent 50 messages. Check message directly from {target?.platform || 'telegram'}
                      </div>
                    )}
                    <div className="message-content" data-message-id={msg.messageId}>
                      {msg.hasMedia || msg.mediaUrl ? (
                        <div>
                          {msg.mediaUrl && (msg.mediaUrl.match(/\.mp4$|video\//) || msg.mimeType?.match(/^video\//)) ? (
                            <video src={msg.mediaUrl} controls style={{ maxWidth: '180px', maxHeight: '150px', borderRadius: 6 }} />
                          ) : msg.mediaUrl && (msg.mediaUrl.match(/\.(jpg|jpeg|png|gif|webp)$/i) || msg.mimeType?.match(/^image\//)) ? (
                            <img src={msg.mediaUrl} alt="sent" style={{ maxWidth: '180px', maxHeight: '150px', borderRadius: 6, objectFit: 'contain' }} />
                          ) : (
                            <div style={{ padding: '6px 8px', border: '1px solid #ccc', borderRadius: 6, fontSize: '0.85rem' }}>
                              📎 {msg.fileName ? (
                                <span>{msg.fileName}</span>
                              ) : (
                                <span>Media file</span>
                              )}
                              {msg.fileSize && (
                                <span style={{ fontSize: '0.75rem', color: '#666', marginLeft: '4px' }}>
                                  ({(msg.fileSize / 1024).toFixed(1)} KB)
                                </span>
                              )}
                            </div>
                          )}
                          {msg.text && (
                            <div style={{ marginTop: '4px', fontSize: '0.9rem' }}>
                              {msg.text}
                            </div>
                          )}
                          <div style={{ marginTop: 2, fontSize: '0.75rem' }}>
                            <a 
                              href={msg.mediaUrl || conversationApi.downloadMediaUrl(targetId, userId, msg.messageId)} 
                              download={msg.fileName || 'media'}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              Download
                            </a>
                          </div>
                        </div>
                      ) : (
                        msg.text
                      )}
                    </div>
                    <div className="message-time" style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '0.7rem', marginTop: '0.2rem' }}>
                      <span>{new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      {msg.edited && (
                        <span style={{ fontSize: '0.65rem', color: '#999', fontStyle: 'italic' }}>
                          (edited)
                        </span>
                      )}
                    </div>
                    {msg.fromUser && msg.messageId !== undefined && (
                      <div className="message-actions" style={{ display: 'flex', gap: 4, marginTop: 2 }}>
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '0.2rem 0.4rem', fontSize: '0.7rem' }}
                          onClick={() => {
                            setSelectedMessageId(msg.messageId);
                            setEditText(msg.text || '');
                            setSelectedMessageHasMedia(msg.hasMedia || false);
                            setPendingMediaReplacement(null); // Clear any pending replacement
                          }}
                        >
                          Edit
                        </button>
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '0.2rem 0.4rem', fontSize: '0.7rem' }}
                          onClick={() => handleDelete(msg.messageId)}
                        >
                          Delete
                        </button>
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '0.2rem 0.4rem', fontSize: '0.7rem' }}
                          onClick={() => {
                            setReplyingTo({
                              messageId: msg.messageId,
                              text: msg.text || (msg.hasMedia ? 'Media' : 'Message'),
                              fromUser: msg.fromUser
                            });
                            // Focus the message input field after a brief delay to ensure state update
                            setTimeout(() => {
                              if (messageInputRef.current) {
                                messageInputRef.current.focus();
                              }
                            }, 100);
                          }}
                          title="Reply to this message"
                        >
                          Reply
                        </button>
                      </div>
                    )}
                    {!msg.fromUser && msg.messageId !== undefined && (
                      <div className="message-actions" style={{ display: 'flex', gap: 4, marginTop: 2 }}>
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '0.2rem 0.4rem', fontSize: '0.7rem', background: '#f5f5f5', color: '#333' }}
                          onClick={() => {
                            setReplyingTo({
                              messageId: msg.messageId,
                              text: msg.text || (msg.hasMedia ? 'Media' : 'Message'),
                              fromUser: msg.fromUser
                            });
                            // Focus the message input field after a brief delay to ensure state update
                            setTimeout(() => {
                              if (messageInputRef.current) {
                                messageInputRef.current.focus();
                              }
                            }, 100);
                          }}
                          title="Reply to this message"
                        >
                          Reply
                        </button>
                        <button
                          className="btn btn-secondary btn-sm"
                          style={{ padding: '0.2rem 0.4rem', fontSize: '0.7rem', background: '#f5f5f5', color: '#333' }}
                          onClick={() => handleDelete(msg.messageId)}
                        >
                          Delete
                        </button>
                      </div>
                    )}
                  </div>
                  );
                })
              )}
            </div>

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
                    <div style={{ padding: '4px 6px', background: '#fff', borderRadius: 4, fontSize: '0.8rem' }}>
                      📎 {pendingMedia.file.name}
                    </div>
                  )}
                  <span style={{ flex: 1, fontSize: '0.85rem', color: '#666' }}>
                    {pendingMedia.file.name} ({(pendingMedia.file.size / 1024).toFixed(1)} KB)
                  </span>
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
                  <strong>Replying to:</strong> {replyingTo.text || (replyingTo.hasMedia ? 'Media' : 'Message')}
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
            <form onSubmit={handleSendMessage} className="message-form">
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
              />
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

