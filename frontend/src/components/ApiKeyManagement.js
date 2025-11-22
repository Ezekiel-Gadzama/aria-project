import React, { useState, useEffect } from 'react';
import { userApi } from '../services/api';
import Modal from './Modal';
import './ApiKeyManagement.css';

function ApiKeyManagement({ userId = 1 }) {
  const [apiKeys, setApiKeys] = useState([]);
  const [credits, setCredits] = useState(0);
  const [subscription, setSubscription] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showCreateKey, setShowCreateKey] = useState(false);
  const [newKeyName, setNewKeyName] = useState('');
  const [newKeySecret, setNewKeySecret] = useState(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(null);
  const [showCreditsModal, setShowCreditsModal] = useState(false);
  const [showSubscriptionModal, setShowSubscriptionModal] = useState(false);
  const [copiedSecret, setCopiedSecret] = useState(false);

  useEffect(() => {
    loadApiKeys();
    loadCredits();
    loadSubscription();
  }, []);

  const loadApiKeys = async () => {
    try {
      const response = await userApi.getApiKeys(userId);
      if (response.data?.success) {
        setApiKeys(response.data.data || []);
      }
    } catch (err) {
      setError(err.message || 'Failed to load API keys');
    } finally {
      setLoading(false);
    }
  };

  const loadCredits = async () => {
    try {
      const response = await userApi.getCredits(userId);
      if (response.data?.success) {
        setCredits(response.data.data?.credits || 0);
      }
    } catch (err) {
      console.error('Failed to load credits:', err);
    }
  };

  const loadSubscription = async () => {
    try {
      const response = await userApi.getSubscription(userId);
      if (response.data?.success) {
        setSubscription(response.data.data);
      }
    } catch (err) {
      console.error('Failed to load subscription:', err);
    }
  };

  const handleCreateKey = async (e) => {
    e.preventDefault();
    try {
      const response = await userApi.createApiKey(userId, { name: newKeyName });
      if (response.data?.success) {
        setNewKeySecret(response.data.data.secret);
        setNewKeyName('');
        setCopiedSecret(false);
        loadApiKeys();
      } else {
        setError(response.data?.error || 'Failed to create API key');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to create API key');
    }
  };

  const handleDeleteKey = async (keyId) => {
    try {
      await userApi.deleteApiKey(userId, keyId);
      setApiKeys(apiKeys.filter(k => k.id !== keyId));
      setShowDeleteConfirm(null);
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete API key');
      setShowDeleteConfirm(null);
    }
  };

  const handleSubscribe = async () => {
    try {
      const response = await userApi.subscribe(userId);
      if (response.data?.success) {
        await loadSubscription();
        setShowSubscriptionModal(false);
        setError(null);
      } else {
        setError(response.data?.error || 'Failed to subscribe');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to subscribe');
    }
  };

  const handleAddCredits = async (amount) => {
    try {
      const response = await userApi.addCredits(userId, amount);
      if (response.data?.success) {
        await loadCredits();
        setShowCreditsModal(false);
        setError(null);
      } else {
        setError(response.data?.error || 'Failed to add credits');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to add credits');
    }
  };

  const handleCopySecret = async () => {
    try {
      await navigator.clipboard.writeText(newKeySecret);
      setCopiedSecret(true);
      setTimeout(() => setCopiedSecret(false), 2000);
    } catch (err) {
      setError('Failed to copy to clipboard');
    }
  };

  if (loading) {
    return <div className="spinner"></div>;
  }

  return (
    <div className="api-key-management">
      <div className="container">
        <h1>API Key Management</h1>
        
        {error && <div className="alert alert-error">{error}</div>}

        {/* Credits Display */}
        <div className="credits-section">
          <div className="credits-card">
            <h2>API Credits</h2>
            <div className="credits-amount">${credits.toFixed(5)}</div>
            <p className="credits-info">
              Each API request costs $0.00001 (10 requests = $0.0001). 
              Credits are used for API access only, separate from app subscription.
            </p>
            <button className="btn btn-primary" onClick={() => setShowCreditsModal(true)}>
              Add $10 Credits
            </button>
          </div>
        </div>

        {/* Subscription Section */}
        <div className="subscription-section">
          <h2>Monthly Subscription</h2>
          <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '1rem' }}>
            Subscribe to use ARIAssistance features in the ARIA app (chat suggestions, AI-powered replies). 
            This subscription is for app usage only and does not include API access.
          </p>
          {subscription ? (
            <div className="subscription-card active">
              <h3>Active Subscription</h3>
              <p>ARIAssistance for chatting: $5/month</p>
              <p>Next billing: {new Date(subscription.nextBilling).toLocaleDateString()}</p>
              <button className="btn btn-secondary">Cancel Subscription</button>
            </div>
          ) : (
            <div className="subscription-card">
              <h3>Subscribe to ARIAssistance</h3>
              <p>Get AI-powered suggested replies for your conversations in the ARIA app</p>
              <p className="price">$5/month</p>
              <p style={{ fontSize: '0.85rem', color: '#666', fontStyle: 'italic' }}>
                Note: This subscription is for app features only. API access requires separate credits.
              </p>
              <button className="btn btn-primary" onClick={() => setShowSubscriptionModal(true)}>
                Subscribe Now
              </button>
            </div>
          )}
        </div>

        {/* Free Tier Info */}
        <div className="free-tier-section">
          <h2>Free Tier</h2>
          <div className="info-card">
            <h3>30-Day Free Trial (API Access)</h3>
            <ul>
              <li>✓ 30 days of free API access</li>
              <li>✓ Limited to 100 requests per day</li>
              <li>✓ Basic features only</li>
              <li>✗ No ARIAssistance chat suggestions</li>
            </ul>
            <p className="note">
              After 30 days, pay $0.00001 per 10 API requests. 
              To use ARIAssistance features in the app, subscribe separately for $5/month.
            </p>
          </div>
        </div>

        {/* API Keys Section */}
        <div className="api-keys-section">
          <div className="section-header">
            <h2>API Keys</h2>
            <button 
              className="btn btn-primary" 
              onClick={() => {
                setShowCreateKey(true);
                setNewKeySecret(null);
              }}
            >
              + Create API Key
            </button>
          </div>

          {showCreateKey && (
            <div className="create-key-form">
              <h3>Create New API Key</h3>
              {newKeySecret ? (
                <div className="key-display">
                  <div className="alert alert-success">
                    <strong>API Key Created!</strong>
                    <p>Save this secret key - you won't be able to see it again:</p>
                    <code className="secret-key">{newKeySecret}</code>
                    <button 
                      className="btn btn-secondary" 
                      onClick={handleCopySecret}
                    >
                      {copiedSecret ? '✓ Copied!' : 'Copy Secret'}
                    </button>
                  </div>
                  <button 
                    className="btn btn-primary" 
                    onClick={() => {
                      setShowCreateKey(false);
                      setNewKeySecret(null);
                    }}
                  >
                    Done
                  </button>
                </div>
              ) : (
                <form onSubmit={handleCreateKey}>
                  <div className="form-group">
                    <label>Key Name</label>
                    <input
                      type="text"
                      value={newKeyName}
                      onChange={(e) => setNewKeyName(e.target.value)}
                      placeholder="e.g., Production Key"
                      required
                    />
                  </div>
                  <div className="form-actions">
                    <button type="submit" className="btn btn-primary">Create Key</button>
                    <button 
                      type="button" 
                      className="btn btn-secondary"
                      onClick={() => {
                        setShowCreateKey(false);
                        setNewKeyName('');
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              )}
            </div>
          )}

          <div className="api-keys-list">
            {apiKeys.length === 0 ? (
              <p>No API keys created yet. Create one to get started!</p>
            ) : (
              apiKeys.map((key) => (
                <div key={key.id} className="api-key-card">
                  <div className="key-info">
                    <h4>{key.name}</h4>
                    <code className="api-key">{key.key}</code>
                    <p className="key-meta">
                      Created: {new Date(key.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <div className="key-actions">
                    <button 
                      className="btn btn-danger btn-sm"
                      onClick={() => setShowDeleteConfirm(key.id)}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Delete Confirmation Modal */}
        <Modal
          isOpen={showDeleteConfirm !== null}
          onClose={() => setShowDeleteConfirm(null)}
          title="Delete API Key"
          size="small"
        >
          <p>Are you sure you want to delete this API key? This action cannot be undone.</p>
          <div className="modal-footer">
            <button 
              className="btn btn-secondary" 
              onClick={() => setShowDeleteConfirm(null)}
            >
              Cancel
            </button>
            <button 
              className="btn btn-danger" 
              onClick={() => handleDeleteKey(showDeleteConfirm)}
            >
              Delete
            </button>
          </div>
        </Modal>

        {/* Add Credits Modal */}
        <Modal
          isOpen={showCreditsModal}
          onClose={() => setShowCreditsModal(false)}
          title="Add Credits"
          size="small"
        >
          <p>Add $10 credits to your account?</p>
          <p style={{ fontSize: '0.9rem', color: '#666', marginTop: '0.5rem' }}>
            Payment integration (Stripe) coming soon! This is a mock payment.
          </p>
          <div className="modal-footer">
            <button 
              className="btn btn-secondary" 
              onClick={() => setShowCreditsModal(false)}
            >
              Cancel
            </button>
            <button 
              className="btn btn-primary" 
              onClick={() => handleAddCredits(10)}
            >
              Add $10 Credits
            </button>
          </div>
        </Modal>

        {/* Subscription Modal */}
        <Modal
          isOpen={showSubscriptionModal}
          onClose={() => setShowSubscriptionModal(false)}
          title="Subscribe to ARIAssistance"
          size="medium"
        >
          <p>Subscribe to ARIAssistance for $5/month?</p>
          <p style={{ fontSize: '0.9rem', color: '#666', marginTop: '0.5rem' }}>
            This subscription gives you access to ARIAssistance features in the ARIA app:
          </p>
          <ul style={{ fontSize: '0.9rem', color: '#666', marginLeft: '1.5rem', marginTop: '0.5rem' }}>
            <li>AI-powered chat suggestions</li>
            <li>Suggested replies for conversations</li>
            <li>Enhanced conversation assistance</li>
          </ul>
          <p style={{ fontSize: '0.85rem', color: '#999', marginTop: '0.5rem', fontStyle: 'italic' }}>
            Note: This subscription is for app features only. API access requires separate credits.
          </p>
          <p style={{ fontSize: '0.9rem', color: '#666', marginTop: '0.5rem' }}>
            Payment integration (Stripe) coming soon! This is a mock payment.
          </p>
          <div className="modal-footer">
            <button 
              className="btn btn-secondary" 
              onClick={() => setShowSubscriptionModal(false)}
            >
              Cancel
            </button>
            <button 
              className="btn btn-primary" 
              onClick={handleSubscribe}
            >
              Subscribe Now
            </button>
          </div>
        </Modal>
      </div>
    </div>
  );
}

export default ApiKeyManagement;

