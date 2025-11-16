import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { targetApi, platformApi } from '../services/api';
import './TargetManagement.css';

function TargetManagement({ userId = 1 }) {
  const navigate = useNavigate();
  const [targets, setTargets] = useState([]);
  const [platforms, setPlatforms] = useState([]); // enum list (TELEGRAM, INSTAGRAM, ...)
  const [platformAccounts, setPlatformAccounts] = useState([]); // concrete accounts
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    username: '',
    platform: '',
    bio: '',
    desiredOutcome: '',
    meetingContext: '',
    contextDetails: '',
  });

  useEffect(() => {
    loadTargets();
    loadPlatforms();
    loadPlatformAccounts();
  }, []);

  const loadTargets = async () => {
    try {
      setLoading(true);
      const response = await targetApi.getAll(userId);
      if (response.data.success) {
        setTargets(response.data.data || []);
      } else {
        setError(response.data.error || 'Failed to load targets');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load targets');
    } finally {
      setLoading(false);
    }
  };

  const loadPlatforms = async () => {
    try {
      const response = await platformApi.getAll();
      if (response.data.success) {
        setPlatforms(response.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load platforms:', err);
    }
  };

  const loadPlatformAccounts = async () => {
    try {
      const response = await platformApi.getAccounts(userId);
      if (response.data.success) {
        setPlatformAccounts(response.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load platform accounts:', err);
    }
  };

  const handleChange = (e) => {
    const { name, value, selectedOptions } = e.target;
    if (name === 'platform' && selectedOptions && selectedOptions[0]) {
      const accountId = selectedOptions[0].getAttribute('data-account-id');
      setFormData({
        ...formData,
        platform: value,
        platformAccountId: accountId ? parseInt(accountId, 10) : undefined,
      });
    } else {
      setFormData({
        ...formData,
        [name]: value,
      });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const response = await targetApi.create(formData, userId);
      if (response.data.success) {
        setShowForm(false);
        setFormData({ name: '', username: '', platform: '', bio: '', desiredOutcome: '', meetingContext: '', contextDetails: '' });
        loadTargets();
      } else {
        setError(response.data.error || 'Failed to create target');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to create target');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Are you sure you want to delete this target?')) {
      return;
    }

    try {
      const response = await targetApi.delete(id, userId);
      if (response.data.success) {
        loadTargets();
      } else {
        setError(response.data.error || 'Failed to delete target');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete target');
    }
  };

  const handleStartConversation = (targetId) => {
    navigate(`/conversations/${targetId}`);
  };

  if (loading && targets.length === 0) {
    return <div className="spinner"></div>;
  }

  return (
    <div className="target-management">
      <div className="container">
        <div className="header">
          <h1>Target Users</h1>
          <button 
            className="btn btn-primary" 
            onClick={() => setShowForm(!showForm)}
          >
            {showForm ? 'Cancel' : '+ Add Target'}
          </button>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {showForm && (
          <div className="target-form-container">
            <h2>Add New Target User</h2>
            <form onSubmit={handleSubmit} className="target-form">
              <div className="form-group">
                <label htmlFor="name">Name *</label>
                <input
                  type="text"
                  id="name"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  required
                  placeholder="Enter target name"
                />
              </div>

              <div className="form-group">
                <label htmlFor="username">Username *</label>
                <input
                  type="text"
                  id="username"
                  name="username"
                  value={formData.username}
                  onChange={handleChange}
                  required
                  placeholder="Enter username"
                />
              </div>

              <div className="form-group">
                <label htmlFor="platform">Platform *</label>
                <select
                  id="platform"
                  name="platform"
                  value={formData.platform}
                  onChange={(e) => {
                    // Reset selected account if platform changes
                    setFormData({
                      ...formData,
                      platform: e.target.value,
                      platformAccountId: undefined,
                    });
                  }}
                  required
                >
                  <option value="">Select platform</option>
                  {platforms.map((platform) => (
                    <option key={platform} value={platform}>
                      {platform}
                    </option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="platformAccountId">Account (Username) *</label>
                <select
                  id="platformAccountId"
                  name="platformAccountId"
                  value={formData.platformAccountId || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, platformAccountId: parseInt(e.target.value, 10) })
                  }
                  required
                  disabled={!formData.platform}
                >
                  <option value="">Select account</option>
                  {platformAccounts
                    .filter((acc) => acc.platform === formData.platform)
                    .map((acc) => (
                      <option key={acc.id} value={acc.id}>
                        {acc.username ? `@${String(acc.username).replace(/^@/, '')}` : acc.number || '(no username)'}
                      </option>
                    ))}
                </select>
              </div>

              <div className="form-group">
                <label htmlFor="bio">Bio (Optional)</label>
                <textarea
                  id="bio"
                  name="bio"
                  value={formData.bio}
                  onChange={handleChange}
                  placeholder="Enter bio"
                  rows="3"
                />
              </div>

              <div className="form-group">
                <label htmlFor="desiredOutcome">Desired Outcome *</label>
                <textarea
                  id="desiredOutcome"
                  name="desiredOutcome"
                  value={formData.desiredOutcome}
                  onChange={handleChange}
                  placeholder="e.g., Arrange a date, Secure investment"
                  rows="2"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="meetingContext">Where/How You Met *</label>
                <textarea
                  id="meetingContext"
                  name="meetingContext"
                  value={formData.meetingContext}
                  onChange={handleChange}
                  placeholder="e.g., Met at a tech conference keynote Q&A"
                  rows="2"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="contextDetails">Important Details (Optional)</label>
                <textarea
                  id="contextDetails"
                  name="contextDetails"
                  value={formData.contextDetails}
                  onChange={handleChange}
                  placeholder="Any details that help AI personalize (interests, prior topics...)"
                  rows="3"
                />
              </div>

              <button type="submit" className="btn btn-primary">
                Create Target
              </button>
            </form>
          </div>
        )}

        <div className="targets-grid">
          {targets.length === 0 ? (
            <div className="empty-state">
              <p>No target users yet. Add one to get started!</p>
            </div>
          ) : (
            targets.map((target) => (
              <div key={target.id} className="card">
                <div className="card-header">
                  <h3 className="card-title">{target.name}</h3>
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={() => handleDelete(target.id)}
                  >
                    Delete
                  </button>
                </div>
                <div className="card-content">
                  <p><strong>Username:</strong> {target.username}</p>
                  <p><strong>Platform:</strong> {target.platform}</p>
                  {target.bio && <p><strong>Bio:</strong> {target.bio}</p>}
                </div>
                <div className="card-actions">
                  <button
                    className="btn btn-primary"
                    onClick={() => handleStartConversation(target.id)}
                  >
                    Start Conversation
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default TargetManagement;

