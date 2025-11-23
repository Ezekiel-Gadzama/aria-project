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
  const [onlineStatus, setOnlineStatus] = useState({}); // { targetId: { online: boolean, lastActive: string } }
  const [showForm, setShowForm] = useState(false);
  const [editingTarget, setEditingTarget] = useState(null); // Target being edited
  const [showAdvanced, setShowAdvanced] = useState(false); // Show/hide advanced settings
  const [confirmDeleteId, setConfirmDeleteId] = useState(null); // Target ID to confirm delete
  const [formData, setFormData] = useState({
    name: '',
    username: '',
    platform: '',
    bio: '',
    desiredOutcome: '',
    meetingContext: '',
    contextDetails: '',
    humorLevel: 0.4,
    formalityLevel: 0.5,
    empathyLevel: 0.7,
    responseTimeAverage: 120.0,
    messageLengthAverage: 25.0,
    questionRate: 0.3,
    engagementLevel: 0.6,
    preferredOpening: 'Hey! How are you doing?',
  });
  const [profilePicture, setProfilePicture] = useState(null); // File object
  const [profilePicturePreview, setProfilePicturePreview] = useState(null); // Preview URL

  useEffect(() => {
    loadTargets();
    loadPlatforms();
    loadPlatformAccounts();
  }, []);

  // Poll for online status of all targets
  useEffect(() => {
    if (targets.length === 0) return;

    const checkOnlineStatus = async () => {
      const statuses = {};
      await Promise.all(
        targets.map(async (target) => {
          try {
            const response = await targetApi.checkOnlineStatus(target.id, userId);
            if (response.data?.success) {
              const data = response.data.data;
              // Backend returns "isOnline" not "online"
              statuses[target.id] = {
                online: data.isOnline === true || data.online === true,
                lastActive: data.lastActive || null
              };
            }
          } catch (err) {
            // Silently fail - online status is not critical
            statuses[target.id] = { online: false, lastActive: null };
          }
        })
      );
      setOnlineStatus(statuses);
    };

    // Check immediately
    checkOnlineStatus();

    // Then check every 5 seconds
    const interval = setInterval(checkOnlineStatus, 5000);

    return () => clearInterval(interval);
  }, [targets, userId]);

  const loadTargets = async () => {
    try {
      setLoading(true);
      const response = await targetApi.getAll(userId);
      if (response.data.success) {
        const targetsData = response.data.data || [];
        // Debug: Log profile picture URLs
        targetsData.forEach(target => {
          if (target.profilePictureUrl) {
            console.log(`Target ${target.name} (ID: ${target.id}) profile picture:`, target.profilePictureUrl);
          }
        });
        setTargets(targetsData);
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
    const { name, value, type, selectedOptions } = e.target;
    if (name === 'platform' && selectedOptions && selectedOptions[0]) {
      const accountId = selectedOptions[0].getAttribute('data-account-id');
      setFormData({
        ...formData,
        platform: value,
        platformAccountId: accountId ? parseInt(accountId, 10) : undefined,
      });
    } else {
      // Handle range inputs (sliders) - convert to numbers
      const newValue = type === 'range' ? parseFloat(value) : value;
      setFormData({
        ...formData,
        [name]: newValue,
      });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      let response;
      if (editingTarget) {
        // Update existing target
        response = await targetApi.update(editingTarget.id, formData, userId);
      } else {
        // Create new target
        response = await targetApi.create(formData, userId);
      }
      if (response.data.success) {
        // Get target ID from response or editing target
        let targetId = editingTarget ? editingTarget.id : null;
        if (!targetId && response.data.data) {
          // Try to get ID from response data
          targetId = response.data.data.id || response.data.data;
        }
        
        // Upload profile picture separately if provided
        if (profilePicture && targetId) {
          try {
            const picResponse = await targetApi.uploadProfilePicture(targetId, profilePicture, userId);
            console.log('Profile picture uploaded:', picResponse.data);
            if (picResponse.data.success && picResponse.data.data?.profilePictureUrl) {
              console.log('Profile picture URL saved:', picResponse.data.data.profilePictureUrl);
            }
          } catch (picErr) {
            console.error('Failed to upload profile picture:', picErr);
            console.error('Error details:', picErr.response?.data);
            // Don't fail the whole operation if profile picture upload fails
          }
        }
        
        setShowForm(false);
        setEditingTarget(null);
        setShowAdvanced(false);
        setProfilePicture(null);
        setProfilePicturePreview(null);
        setFormData({ 
          name: '', username: '', platform: '', bio: '', desiredOutcome: '', meetingContext: '', contextDetails: '',
          humorLevel: 0.4, formalityLevel: 0.5, empathyLevel: 0.7, responseTimeAverage: 120.0,
          messageLengthAverage: 25.0, questionRate: 0.3, engagementLevel: 0.6, preferredOpening: 'Hey! How are you doing?'
        });
        loadTargets();
      } else {
        setError(response.data.error || `Failed to ${editingTarget ? 'update' : 'create'} target`);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || `Failed to ${editingTarget ? 'update' : 'create'} target`);
    }
  };

  const handleDelete = async (id) => {
    setConfirmDeleteId(id);
  };

  const performDelete = async () => {
    if (!confirmDeleteId) return;
    
    try {
      const response = await targetApi.delete(confirmDeleteId, userId);
      if (response.data.success) {
        setConfirmDeleteId(null);
        loadTargets();
      } else {
        setError(response.data.error || 'Failed to delete target');
        setConfirmDeleteId(null);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete target');
      setConfirmDeleteId(null);
    }
  };

  const cancelDelete = () => {
    setConfirmDeleteId(null);
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
            onClick={() => {
              if (showForm && !editingTarget) {
                setShowForm(false);
                setEditingTarget(null);
                setShowAdvanced(false);
              } else {
                setShowForm(true);
                setEditingTarget(null);
                setFormData({
                  name: '', username: '', platform: '', bio: '', desiredOutcome: '', meetingContext: '', contextDetails: '',
                  humorLevel: 0.4, formalityLevel: 0.5, empathyLevel: 0.7, responseTimeAverage: 120.0,
                  messageLengthAverage: 25.0, questionRate: 0.3, engagementLevel: 0.6, preferredOpening: 'Hey! How are you doing?'
                });
              }
            }}
          >
            {showForm && !editingTarget ? 'Cancel' : '+ Add Target'}
          </button>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {showForm && (
          <div className="target-form-container">
            <h2>{editingTarget ? 'Edit Target User' : 'Add New Target User'}</h2>
            <form onSubmit={handleSubmit} className="target-form" encType="multipart/form-data">
              <div className="form-group">
                <label htmlFor="profilePicture">Profile Picture (Optional)</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
                  {profilePicturePreview && (
                    <img 
                      src={profilePicturePreview} 
                      alt="Preview" 
                      style={{ width: '80px', height: '80px', borderRadius: '50%', objectFit: 'cover' }}
                      onError={(e) => {
                        // If image fails to load, hide it
                        e.target.style.display = 'none';
                        console.error('Failed to load profile picture:', profilePicturePreview);
                      }}
                    />
                  )}
                  <input
                    type="file"
                    id="profilePicture"
                    name="profilePicture"
                    accept="image/*"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (file) {
                        setProfilePicture(file);
                        const preview = URL.createObjectURL(file);
                        // Revoke old preview if it was a blob URL
                        if (profilePicturePreview && profilePicturePreview.startsWith('blob:')) {
                          URL.revokeObjectURL(profilePicturePreview);
                        }
                        setProfilePicturePreview(preview);
                      }
                    }}
                  />
                  {profilePicturePreview && (
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => {
                        setProfilePicture(null);
                        // Only revoke if it's a blob URL (new upload), not if it's an existing image URL
                        if (profilePicturePreview.startsWith('blob:')) {
                          URL.revokeObjectURL(profilePicturePreview);
                        }
                        setProfilePicturePreview(null);
                      }}
                    >
                      Remove
                    </button>
                  )}
                </div>
                <p style={{ fontSize: '0.85rem', color: '#666' }}>
                  If no picture is uploaded, we'll automatically fetch it from {formData.platform || 'the platform'}
                </p>
              </div>
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
                    .map((acc) => {
                      const displayName = acc.accountName || (acc.username ? `@${String(acc.username).replace(/^@/, '')}` : acc.number || '(no username)');
                      const usernamePart = acc.username ? `@${String(acc.username).replace(/^@/, '')}` : '';
                      const displayText = acc.accountName && usernamePart ? `${acc.accountName} (${usernamePart})` : displayName;
                      return (
                        <option key={acc.id} value={acc.id}>
                          {displayText}
                        </option>
                      );
                    })}
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

              {/* Advanced Communication Settings */}
              <div style={{ marginTop: '1.5rem', padding: '1rem', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#f9f9f9' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                  <h4 style={{ margin: 0 }}>Advanced Communication Settings (Optional)</h4>
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    onClick={() => setShowAdvanced(!showAdvanced)}
                  >
                    {showAdvanced ? 'Hide' : 'Show'} Advanced
                  </button>
                </div>
                
                {showAdvanced && (
                  <div>
                    <div className="form-group">
                      <label htmlFor="humorLevel">Humor Level: {(formData.humorLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="humorLevel"
                        name="humorLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={formData.humorLevel || 0.4}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="formalityLevel">Formality Level: {(formData.formalityLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="formalityLevel"
                        name="formalityLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={formData.formalityLevel || 0.5}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="empathyLevel">Empathy Level: {(formData.empathyLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="empathyLevel"
                        name="empathyLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={formData.empathyLevel || 0.7}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="responseTimeAverage">Response Time Average (seconds): {formData.responseTimeAverage || 120}</label>
                      <input
                        type="range"
                        id="responseTimeAverage"
                        name="responseTimeAverage"
                        min="0"
                        max="600"
                        step="10"
                        value={formData.responseTimeAverage || 120.0}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="messageLengthAverage">Message Length Average (words): {formData.messageLengthAverage || 25}</label>
                      <input
                        type="range"
                        id="messageLengthAverage"
                        name="messageLengthAverage"
                        min="0"
                        max="100"
                        step="1"
                        value={formData.messageLengthAverage || 25.0}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="questionRate">Question Rate: {(formData.questionRate || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="questionRate"
                        name="questionRate"
                        min="0"
                        max="1"
                        step="0.1"
                        value={formData.questionRate || 0.3}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="engagementLevel">Engagement Level: {(formData.engagementLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="engagementLevel"
                        name="engagementLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={formData.engagementLevel}
                        onChange={handleChange}
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="preferredOpening">Preferred Opening</label>
                      <input
                        type="text"
                        id="preferredOpening"
                        name="preferredOpening"
                        value={formData.preferredOpening}
                        onChange={handleChange}
                        placeholder="e.g., Hey! How are you doing?"
                      />
                    </div>
                  </div>
                )}
              </div>

              <button type="submit" className="btn btn-primary">
                {editingTarget ? 'Update Target' : 'Create Target'}
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
                  <h3 className="card-title" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    {target.profilePictureUrl ? (
                      <img 
                        src={target.profilePictureUrl} 
                        alt={target.name}
                        style={{ 
                          width: '40px', 
                          height: '40px', 
                          borderRadius: '50%', 
                          objectFit: 'cover',
                          marginRight: '0.5rem'
                        }}
                      />
                    ) : (
                      <div 
                        style={{ 
                          width: '40px', 
                          height: '40px', 
                          borderRadius: '50%', 
                          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'white',
                          fontWeight: 'bold',
                          fontSize: '1.2rem',
                          marginRight: '0.5rem'
                        }}
                      >
                        {target.name.charAt(0).toUpperCase()}
                      </div>
                    )}
                    {target.name}
                    {onlineStatus[target.id]?.online ? (
                      <>
                        <span className="online-indicator" title="Online" style={{ 
                          display: 'inline-block',
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          backgroundColor: '#4caf50',
                          boxShadow: '0 0 0 2px rgba(76, 175, 80, 0.3)',
                          animation: 'pulse 2s infinite',
                          marginLeft: '0.5rem'
                        }}></span>
                        <span style={{ fontSize: '0.8rem', color: '#4caf50', marginLeft: '0.5rem' }}>
                          online
                        </span>
                      </>
                    ) : (
                      <>
                        <span className="offline-indicator" title={onlineStatus[target.id]?.lastActive || "Offline"} style={{ 
                          display: 'inline-block',
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          backgroundColor: '#999',
                          marginLeft: '0.5rem'
                        }}></span>
                        {onlineStatus[target.id]?.lastActive && (
                          <span style={{ fontSize: '0.75rem', color: '#666', marginLeft: '0.5rem' }}>
                            {onlineStatus[target.id].lastActive}
                          </span>
                        )}
                      </>
                    )}
                  </h3>
                  <button
                    className="btn btn-danger btn-sm"
                    onClick={() => handleDelete(target.id)}
                  >
                    Delete
                  </button>
                </div>
                {/* Delete Confirmation Modal */}
                {confirmDeleteId === target.id && (
                  <div className="modal-overlay" style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    backgroundColor: 'rgba(0, 0, 0, 0.5)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 1000
                  }}>
                    <div className="modal" style={{
                      backgroundColor: 'white',
                      padding: '2rem',
                      borderRadius: '8px',
                      maxWidth: '500px',
                      width: '90%',
                      boxShadow: '0 4px 6px rgba(0, 0, 0, 0.1)'
                    }}>
                      <h3>Delete Target User</h3>
                      <p>Are you sure you want to delete this target user?</p>
                      <p style={{ color: '#d32f2f', fontWeight: 'bold' }}>This action cannot be undone.</p>
                      <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem', justifyContent: 'flex-end' }}>
                        <button
                          className="btn btn-secondary"
                          onClick={cancelDelete}
                        >
                          Cancel
                        </button>
                        <button
                          className="btn btn-danger"
                          onClick={performDelete}
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                )}
                <div className="card-content">
                  <p><strong>Username:</strong> {target.username}</p>
                  <p><strong>Platform:</strong> {target.platform}</p>
                  {(target.platformAccountName || target.platformAccountUsername) && (
                    <p>
                      <strong>Acct:</strong>{' '}
                      {target.platformAccountName || (target.platformAccountUsername.startsWith('@') ? target.platformAccountUsername : '@' + target.platformAccountUsername)}
                    </p>
                  )}
                  {target.bio && <p><strong>Bio:</strong> {target.bio}</p>}
                </div>
                <div className="card-actions">
                  <button
                    className="btn btn-primary"
                    onClick={() => handleStartConversation(target.id)}
                  >
                    Start Conversation
                  </button>
                  <button
                    className="btn btn-secondary"
                    style={{ marginLeft: '0.5rem' }}
                    onClick={() => {
                      setEditingTarget(target);
                      setShowAdvanced(true); // Show advanced settings when editing
                      
                      // Load existing profile picture if available
                      if (target.profilePictureUrl) {
                        setProfilePicturePreview(target.profilePictureUrl);
                      } else {
                        setProfilePicturePreview(null);
                      }
                      setProfilePicture(null); // Reset file input
                      
                      setFormData({
                        name: target.name,
                        username: target.username || '',
                        platform: target.platform || '',
                        platformAccountId: target.platformAccountId || undefined,
                        bio: target.bio || '',
                        desiredOutcome: target.desiredOutcome || '',
                        meetingContext: target.meetingContext || '',
                        contextDetails: target.contextDetails || '',
                        humorLevel: target.humorLevel !== undefined ? target.humorLevel : 0.4,
                        formalityLevel: target.formalityLevel !== undefined ? target.formalityLevel : 0.5,
                        empathyLevel: target.empathyLevel !== undefined ? target.empathyLevel : 0.7,
                        responseTimeAverage: target.responseTimeAverage !== undefined ? target.responseTimeAverage : 120.0,
                        messageLengthAverage: target.messageLengthAverage !== undefined ? target.messageLengthAverage : 25.0,
                        questionRate: target.questionRate !== undefined ? target.questionRate : 0.3,
                        engagementLevel: target.engagementLevel !== undefined ? target.engagementLevel : 0.6,
                        preferredOpening: target.preferredOpening || 'Hey! How are you doing?',
                      });
                      setShowForm(true);
                    }}
                  >
                    Edit
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

