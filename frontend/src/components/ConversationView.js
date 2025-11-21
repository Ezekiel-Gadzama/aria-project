import React, { useState, useEffect } from 'react';
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

  useEffect(() => {
    loadTarget();
    loadPlatformAccounts();
    // Check if there is an active conversation; if yes, skip goal page
    (async () => {
      try {
        const resp = await conversationApi.isActive(targetId, userId);
        if (resp.data?.success && resp.data?.data === true) {
          setConversationInitialized(true);
          await loadMessages();
        }
      } catch (e) {
        // ignore
      }
    })();
  }, [targetId]);

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
        const response = await conversationApi.sendMediaWithText(targetId, userId, file, messageText);
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
          };
          setMessages(prev => [...prev, newMsg]);
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
        const response = await conversationApi.respond(targetId, messageText, userId);
        // If response includes message data, add it immediately to the UI
        if (response.data?.success && response.data?.data) {
          const messageData = response.data.data;
          const newMsg = {
            text: messageData.text || messageText,
            fromUser: messageData.fromUser !== undefined ? messageData.fromUser : true,
            timestamp: messageData.timestamp ? new Date(messageData.timestamp) : new Date(),
            mediaUrl: messageData.mediaDownloadUrl || null,
            messageId: messageData.messageId,
          };
          // Add to messages list immediately
          setMessages(prev => [...prev, newMsg]);
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
    
    try {
      let response;
      if (msgId) {
        response = await conversationApi.edit(targetId, userId, msgId, messageText);
      } else {
        response = await conversationApi.editLast(targetId, userId, messageText);
      }
      
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
        setMessages(prev => prev.map(msg => {
          if (msg.messageId === updatedMsg.messageId) {
            // Preserve media info if the original message had media
            // This ensures media shows even if backend doesn't return hasMedia=true
            if (msg.hasMedia || msg.mediaUrl) {
              return { 
                ...updatedMsg, 
                hasMedia: true, // Ensure hasMedia is true if original had media
                mediaUrl: updatedMsg.mediaUrl || msg.mediaUrl, // Use new URL if available, otherwise keep old
                fileName: updatedMsg.fileName || msg.fileName,
                mimeType: updatedMsg.mimeType || msg.mimeType
              };
            }
            return updatedMsg;
          }
          return msg;
        }));
      } else {
        // Fallback: update locally and reload
        setMessages(prev => prev.map(msg => 
          msg.messageId === msgId ? { ...msg, text: messageText, edited: true } : msg
        ));
        setTimeout(loadMessages, 300);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to edit message');
      // Restore edit state if editing failed
      setSelectedMessageId(msgId);
      setEditText(messageText);
    }
  };

  const loadMessages = async () => {
    try {
      const resp = await conversationApi.getMessages(targetId, userId, 100);
      if (resp.data?.success) {
        const rows = resp.data.data || [];
        setMessages(
          rows.map((r) => ({
            text: r.text || '',
            fromUser: !!r.fromUser,
            timestamp: r.timestamp ? new Date(r.timestamp) : new Date(),
            mediaUrl: r.mediaDownloadUrl || null,
            messageId: r.messageId,
            hasMedia: r.hasMedia || false,
            fileName: r.fileName || null,
            mimeType: r.mimeType || null,
            edited: r.edited || false, // Preserve edited flag from database or API
          }))
        );
      }
    } catch (e) {
      // ignore
    }
  };

  const handleDelete = async (messageId) => {
    try {
      await conversationApi.delete(targetId, userId, messageId);
      setMessages(prev => prev.filter(m => m.messageId !== messageId));
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete message');
    }
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
                  <span className="offline-indicator" title="Offline"></span>
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
                  <p>No messages yet. Start the conversation!</p>
                </div>
              ) : (
                messages.map((msg, idx) => (
                  <div key={idx} className={`message ${msg.fromUser ? 'from-user' : 'from-target'}`}>
                    <div className="message-content">
                      {msg.hasMedia || msg.mediaUrl ? (
                        <div>
                          {msg.mediaUrl && (msg.mediaUrl.match(/\.mp4$|video\//) || msg.mimeType?.match(/^video\//)) ? (
                            <video src={msg.mediaUrl} controls style={{ maxWidth: '180px', maxHeight: '150px', borderRadius: 6 }} />
                          ) : msg.mediaUrl && (msg.mediaUrl.match(/\.(jpg|jpeg|png|gif|webp)$/i) || msg.mimeType?.match(/^image\//)) ? (
                            <img src={msg.mediaUrl} alt="sent" style={{ maxWidth: '180px', maxHeight: '150px', borderRadius: 6, objectFit: 'contain' }} />
                          ) : (
                            <div style={{ padding: '6px 8px', border: '1px solid #ccc', borderRadius: 6, fontSize: '0.85rem' }}>
                              📎 {msg.fileName || 'Media file'}
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
                      </div>
                    )}
                  </div>
                ))
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
            <form onSubmit={handleSendMessage} className="message-form">
              <input
                type="text"
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                placeholder={pendingMedia ? "Add a caption (optional)..." : "Type a message..."}
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
      </div>
    </div>
  );
}

export default ConversationView;

