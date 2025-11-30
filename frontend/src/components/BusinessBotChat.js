import React, { useState, useEffect, useRef } from 'react';
import { businessApi } from '../services/api';
import './BusinessBotChat.css';

function BusinessBotChat({ business, userId, onClose }) {
  const [messages, setMessages] = useState([]);
  const [inputMessage, setInputMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    // Load bot history if available
    loadBotHistory();
    // Add welcome message
    setMessages([{
      id: 'welcome',
      text: `Hello! I'm your business assistant for "${business.name}". I can help you answer questions about tasks, projects, and discussions across all channels, groups, and private chats in this business. What would you like to know?`,
      fromBot: true,
      timestamp: new Date()
    }]);
  }, [business.id]);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const loadBotHistory = async () => {
    try {
      const response = await businessApi.getBotHistory(business.id, userId);
      if (response.data?.success && response.data.data) {
        // In the future, we can load conversation history here
        // For now, we'll just use the welcome message
      }
    } catch (err) {
      console.error('Failed to load bot history:', err);
    }
  };

  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!inputMessage.trim() || loading) return;

    const userMessage = {
      id: Date.now(),
      text: inputMessage,
      fromBot: false,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setInputMessage('');
    setLoading(true);
    setError(null);

    try {
      const response = await businessApi.botChat(business.id, inputMessage, userId);
      if (response.data?.success) {
        const botMessage = {
          id: Date.now() + 1,
          text: response.data.data.message,
          fromBot: true,
          timestamp: new Date(response.data.data.timestamp || Date.now())
        };
        setMessages(prev => [...prev, botMessage]);
      } else {
        setError(response.data?.error || 'Failed to get bot response');
        const errorMessage = {
          id: Date.now() + 1,
          text: 'Sorry, I encountered an error. Please try again.',
          fromBot: true,
          timestamp: new Date(),
          isError: true
        };
        setMessages(prev => [...prev, errorMessage]);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to send message');
      const errorMessage = {
        id: Date.now() + 1,
        text: 'Sorry, I encountered an error. Please try again.',
        fromBot: true,
        timestamp: new Date(),
        isError: true
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (timestamp) => {
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="bot-chat-modal">
      <div className="bot-chat-container">
        <div className="bot-chat-header">
          <div>
            <h2>🤖 Business Bot</h2>
            <p className="bot-chat-subtitle">{business.name}</p>
          </div>
          <button className="btn-close" onClick={onClose}>×</button>
        </div>

        <div className="bot-chat-messages">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`message ${message.fromBot ? 'bot-message' : 'user-message'} ${message.isError ? 'error-message' : ''}`}
            >
              <div className="message-content">
                {message.fromBot && (
                  <div className="bot-avatar">🤖</div>
                )}
                <div className="message-bubble">
                  <p>{message.text}</p>
                  <span className="message-time">{formatTime(message.timestamp)}</span>
                </div>
              </div>
            </div>
          ))}
          {loading && (
            <div className="message bot-message">
              <div className="message-content">
                <div className="bot-avatar">🤖</div>
                <div className="message-bubble">
                  <div className="typing-indicator">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {error && (
          <div className="bot-chat-error">
            {error}
          </div>
        )}

        <form className="bot-chat-input-form" onSubmit={handleSendMessage}>
          <input
            type="text"
            className="bot-chat-input"
            value={inputMessage}
            onChange={(e) => setInputMessage(e.target.value)}
            placeholder="Ask me anything about your business..."
            disabled={loading}
          />
          <button
            type="submit"
            className="btn btn-primary"
            disabled={loading || !inputMessage.trim()}
          >
            Send
          </button>
        </form>
      </div>
    </div>
  );
}

export default BusinessBotChat;

