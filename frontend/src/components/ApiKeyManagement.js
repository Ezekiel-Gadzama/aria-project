import React, { useState, useEffect } from 'react';
import { userApi } from '../services/api';
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

  useEffect(() => {
    loadApiKeys();
    loadCredits();
    loadSubscription();
  }, []);

  const loadApiKeys = async () => {
    try {
      // TODO: Implement API endpoint
      // const response = await userApi.getApiKeys(userId);
      // setApiKeys(response.data.data || []);
      setApiKeys([]);
    } catch (err) {
      setError(err.message || 'Failed to load API keys');
    } finally {
      setLoading(false);
    }
  };

  const loadCredits = async () => {
    try {
      // TODO: Implement API endpoint
      // const response = await userApi.getCredits(userId);
      // setCredits(response.data.data || 0);
      setCredits(0);
    } catch (err) {
      console.error('Failed to load credits:', err);
    }
  };

  const loadSubscription = async () => {
    try {
      // TODO: Implement API endpoint
      // const response = await userApi.getSubscription(userId);
      // setSubscription(response.data.data);
      setSubscription(null);
    } catch (err) {
      console.error('Failed to load subscription:', err);
    }
  };

  const handleCreateKey = async (e) => {
    e.preventDefault();
    try {
      // TODO: Implement API endpoint
      // const response = await userApi.createApiKey(userId, { name: newKeyName });
      // if (response.data.success) {
      //   setNewKeySecret(response.data.data.secret);
      //   setNewKeyName('');
      //   loadApiKeys();
      // }
      
      // Mock implementation
      const mockKey = {
        id: Date.now(),
        name: newKeyName,
        key: 'aria_' + Math.random().toString(36).substring(2, 15),
        secret: 'aria_secret_' + Math.random().toString(36).substring(2, 15),
        createdAt: new Date().toISOString(),
        isActive: true
      };
      setNewKeySecret(mockKey.secret);
      setApiKeys([...apiKeys, mockKey]);
      setNewKeyName('');
    } catch (err) {
      setError(err.message || 'Failed to create API key');
    }
  };

  const handleDeleteKey = async (keyId) => {
    if (!window.confirm('Are you sure you want to delete this API key?')) return;
    try {
      // TODO: Implement API endpoint
      // await userApi.deleteApiKey(userId, keyId);
      setApiKeys(apiKeys.filter(k => k.id !== keyId));
    } catch (err) {
      setError(err.message || 'Failed to delete API key');
    }
  };

  const handleSubscribe = async () => {
    try {
      // TODO: Implement payment integration (Stripe)
      // For now, show subscription modal
      if (window.confirm('Subscribe to ARIAssistance for $5/month?\n\nPayment integration (Stripe) coming soon!\n\nThis would:\n1. Open Stripe checkout\n2. Set up recurring payment\n3. Activate subscription')) {
        // In production, this would call the subscription API
        // await userApi.subscribe(userId);
        alert('Subscription payment integration will be implemented with Stripe');
      }
    } catch (err) {
      setError(err.message || 'Failed to subscribe');
    }
  };

  const handleAddCredits = async (amount) => {
    try {
      // TODO: Implement payment integration
      alert(`Add $${amount} credits - Payment integration coming soon!`);
    } catch (err) {
      setError(err.message || 'Failed to add credits');
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
            <h2>Credits</h2>
            <div className="credits-amount">${credits.toFixed(5)}</div>
            <p className="credits-info">Each API request costs $0.00001 (10 requests = $0.0001)</p>
            <button className="btn btn-primary" onClick={() => handleAddCredits(10)}>
              Add $10 Credits
            </button>
          </div>
        </div>

        {/* Subscription Section */}
        <div className="subscription-section">
          <h2>Monthly Subscription</h2>
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
              <p>Get AI-powered suggested replies for your conversations</p>
              <p className="price">$5/month</p>
              <button className="btn btn-primary" onClick={handleSubscribe}>
                Subscribe Now
              </button>
            </div>
          )}
        </div>

        {/* Free Tier Info */}
        <div className="free-tier-section">
          <h2>Free Tier</h2>
          <div className="info-card">
            <h3>30-Day Free Trial</h3>
            <ul>
              <li>✓ 30 days of free API access</li>
              <li>✓ Limited to 100 requests per day</li>
              <li>✓ Basic features only</li>
              <li>✗ No ARIAssistance chat suggestions</li>
            </ul>
            <p className="note">After 30 days, pay $0.00001 per 10 requests or subscribe for unlimited access.</p>
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
                      onClick={() => {
                        navigator.clipboard.writeText(newKeySecret);
                        alert('Secret key copied to clipboard!');
                      }}
                    >
                      Copy Secret
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
                      onClick={() => handleDeleteKey(key.id)}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default ApiKeyManagement;

