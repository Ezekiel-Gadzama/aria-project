import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { targetApi, platformApi } from '../services/api';
import './TargetManagement.css';

function SubTargetUsersView({ userId = 1 }) {
  const { targetId } = useParams();
  const navigate = useNavigate();
  const [target, setTarget] = useState(null);
  const [platforms, setPlatforms] = useState([]);
  const [platformAccounts, setPlatformAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showSubTargetForm, setShowSubTargetForm] = useState(false);
  const [editingSubTarget, setEditingSubTarget] = useState(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [confirmDeleteSubTarget, setConfirmDeleteSubTarget] = useState(null);
  const [subTargetFormData, setSubTargetFormData] = useState({
    name: '',
    username: '',
    platform: '',
    platformAccountId: undefined,
    number: '',
    humorLevel: 0.4,
    formalityLevel: 0.5,
    empathyLevel: 0.7,
    responseTimeAverage: 120.0,
    messageLengthAverage: 25.0,
    questionRate: 0.3,
    engagementLevel: 0.6,
    preferredOpening: 'Hey! How are you doing?',
  });

  useEffect(() => {
    loadTarget();
    loadPlatforms();
    loadPlatformAccounts();
  }, [targetId]);

  const loadTarget = async () => {
    try {
      setLoading(true);
      const response = await targetApi.getById(parseInt(targetId), userId);
      if (response.data.success) {
        setTarget(response.data.data);
      } else {
        setError(response.data.error || 'Failed to load target user');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load target user');
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

  const handleSubTargetFormChange = (e) => {
    const { name, value, type, selectedOptions } = e.target;
    if (name === 'platform' && selectedOptions && selectedOptions[0]) {
      const accountId = selectedOptions[0].getAttribute('data-account-id');
      setSubTargetFormData({
        ...subTargetFormData,
        platform: value,
        platformAccountId: accountId ? parseInt(accountId, 10) : undefined,
      });
    } else {
      const newValue = type === 'range' ? parseFloat(value) : value;
      setSubTargetFormData({
        ...subTargetFormData,
        [name]: newValue,
      });
    }
  };

  const handleSubTargetSubmit = async (e) => {
    e.preventDefault();
    
    if (!target) {
      setError('Target user not found');
      return;
    }
    
    if (!subTargetFormData.username || !subTargetFormData.platform || !subTargetFormData.platformAccountId) {
      setError('Please fill in all required fields: Username, Platform, and Account');
      return;
    }
    
    try {
      setError(null);
      
      const existingSubTargets = target.subTargetUsers || [];
      
      const subTargetData = {
        id: editingSubTarget?.id || null,
        targetUserId: target.id,
        name: subTargetFormData.name || null,
        username: subTargetFormData.username,
        platform: subTargetFormData.platform,
        platformAccountId: subTargetFormData.platformAccountId,
        number: subTargetFormData.number || null,
        // Preserve platformId if editing (important for ON CONFLICT matching)
        platformId: editingSubTarget?.platformId || null,
        advancedCommunicationSettings: JSON.stringify({
          humorLevel: subTargetFormData.humorLevel,
          formalityLevel: subTargetFormData.formalityLevel,
          empathyLevel: subTargetFormData.empathyLevel,
          responseTimeAverage: subTargetFormData.responseTimeAverage,
          messageLengthAverage: subTargetFormData.messageLengthAverage,
          questionRate: subTargetFormData.questionRate,
          engagementLevel: subTargetFormData.engagementLevel,
          preferredOpening: subTargetFormData.preferredOpening,
        }),
      };
      
      let updatedSubTargets;
      if (editingSubTarget) {
        updatedSubTargets = existingSubTargets.map(st => 
          st.id === editingSubTarget.id ? subTargetData : st
        );
      } else {
        updatedSubTargets = [...existingSubTargets, subTargetData];
      }
      
      const updateData = {
        name: target.name,
        bio: target.bio || null,
        desiredOutcome: target.desiredOutcome || null,
        meetingContext: target.meetingContext || null,
        importantDetails: target.importantDetails || null,
        crossPlatformContextEnabled: target.crossPlatformContextEnabled || false,
        subTargetUsers: updatedSubTargets,
      };
      
      const response = await targetApi.update(target.id, updateData, userId);
      
      if (response.data.success) {
        setShowSubTargetForm(false);
        setEditingSubTarget(null);
        setSubTargetFormData({
          name: '', username: '', platform: '', platformAccountId: undefined, number: '',
          humorLevel: 0.4, formalityLevel: 0.5, empathyLevel: 0.7, responseTimeAverage: 120.0,
          messageLengthAverage: 25.0, questionRate: 0.3, engagementLevel: 0.6, preferredOpening: 'Hey! How are you doing?'
        });
        setShowAdvanced(false);
        loadTarget();
      } else {
        setError(response.data.error || 'Failed to save SubTarget User');
      }
    } catch (err) {
      console.error('Error saving SubTarget User:', err);
      setError(err.response?.data?.error || err.message || 'Failed to save SubTarget User');
    }
  };

  const handleDeleteSubTarget = async () => {
    if (!confirmDeleteSubTarget || !target) return;
    
    try {
      const updatedSubTargets = target.subTargetUsers.filter(st => st.id !== confirmDeleteSubTarget.id);
      
      const updateData = {
        name: target.name,
        bio: target.bio || null,
        desiredOutcome: target.desiredOutcome || null,
        meetingContext: target.meetingContext || null,
        importantDetails: target.importantDetails || null,
        crossPlatformContextEnabled: target.crossPlatformContextEnabled || false,
        subTargetUsers: updatedSubTargets,
      };
      
      const response = await targetApi.update(target.id, updateData, userId);
      if (response.data.success) {
        setConfirmDeleteSubTarget(null);
        loadTarget();
      } else {
        setError(response.data.error || 'Failed to delete SubTarget User');
        setConfirmDeleteSubTarget(null);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete SubTarget User');
      setConfirmDeleteSubTarget(null);
    }
  };

  const handleStartConversation = (subtargetUserId) => {
    navigate(`/conversations/${targetId}?subtargetUserId=${subtargetUserId}`);
  };

  if (loading) {
    return <div className="spinner"></div>;
  }

  if (!target) {
    return (
      <div className="container">
        <div className="alert alert-error">Target user not found</div>
        <button className="btn btn-secondary" onClick={() => navigate('/targets')}>
          Back to Targets
        </button>
      </div>
    );
  }

  const subTargets = target.subTargetUsers || [];

  return (
    <div className="target-management">
      <div className="container">
        <div className="header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
          <div>
            <button 
              className="btn btn-secondary" 
              onClick={() => navigate('/targets')}
              style={{ marginRight: '1rem' }}
            >
              ← Back
            </button>
            <h1>{target.name} - Platform Instances</h1>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <button
              className="btn btn-primary btn-sm"
              onClick={() => {
                // Navigate to analysis without platform filter (from list page)
                navigate(`/analysis/${targetId}`);
              }}
            >
              View Analysis
            </button>
            <button
              className="btn btn-primary"
              onClick={() => {
                setEditingSubTarget(null);
                setShowSubTargetForm(true);
                setSubTargetFormData({
                  name: '', username: '', platform: '', platformAccountId: undefined, number: '',
                  humorLevel: 0.4, formalityLevel: 0.5, empathyLevel: 0.7, responseTimeAverage: 120.0,
                  messageLengthAverage: 25.0, questionRate: 0.3, engagementLevel: 0.6, preferredOpening: 'Hey! How are you doing?'
                });
              }}
            >
              + Add SubTarget User
            </button>
          </div>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {/* SubTarget User Form */}
        {showSubTargetForm && (
          <div style={{ marginBottom: '2rem', padding: '1.5rem', backgroundColor: '#f0f0f0', borderRadius: '8px' }}>
            <h3>{editingSubTarget ? 'Edit SubTarget User' : 'Add New SubTarget User'}</h3>
            <form onSubmit={handleSubTargetSubmit}>
              {/* Form fields same as in TargetManagement.js */}
              <div className="form-group">
                <label htmlFor="subTargetName">Name (Platform-specific nickname, optional)</label>
                <input
                  type="text"
                  id="subTargetName"
                  name="name"
                  value={subTargetFormData.name}
                  onChange={handleSubTargetFormChange}
                  placeholder="e.g., Phil (for Telegram)"
                />
              </div>
              
              <div className="form-group">
                <label htmlFor="subTargetUsername">Username *</label>
                <input
                  type="text"
                  id="subTargetUsername"
                  name="username"
                  value={subTargetFormData.username}
                  onChange={handleSubTargetFormChange}
                  required
                  placeholder="Enter username"
                />
              </div>
              
              <div className="form-group">
                <label htmlFor="subTargetPlatform">Platform *</label>
                <select
                  id="subTargetPlatform"
                  name="platform"
                  value={subTargetFormData.platform}
                  onChange={handleSubTargetFormChange}
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
                <label htmlFor="subTargetPlatformAccountId">Account (Username) *</label>
                <select
                  id="subTargetPlatformAccountId"
                  name="platformAccountId"
                  value={subTargetFormData.platformAccountId || ''}
                  onChange={(e) =>
                    setSubTargetFormData({ ...subTargetFormData, platformAccountId: parseInt(e.target.value, 10) })
                  }
                  required
                  disabled={!subTargetFormData.platform}
                >
                  <option value="">Select account</option>
                  {platformAccounts
                    .filter((acc) => acc.platform === subTargetFormData.platform)
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
                <label htmlFor="subTargetNumber">Phone Number (Optional)</label>
                <input
                  type="text"
                  id="subTargetNumber"
                  name="number"
                  value={subTargetFormData.number}
                  onChange={handleSubTargetFormChange}
                  placeholder="Enter phone number"
                />
              </div>
              
              {/* Advanced settings section - same as TargetManagement.js */}
              <div style={{ marginTop: '1rem', padding: '1rem', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#fff' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                  <h5 style={{ margin: 0 }}>Advanced Communication Settings (Optional)</h5>
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
                    {/* Advanced settings inputs - same structure as TargetManagement.js */}
                    <div className="form-group">
                      <label htmlFor="subTargetHumorLevel">Humor Level: {(subTargetFormData.humorLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="subTargetHumorLevel"
                        name="humorLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={subTargetFormData.humorLevel || 0.4}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetFormalityLevel">Formality Level: {(subTargetFormData.formalityLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="subTargetFormalityLevel"
                        name="formalityLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={subTargetFormData.formalityLevel || 0.5}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetEmpathyLevel">Empathy Level: {(subTargetFormData.empathyLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="subTargetEmpathyLevel"
                        name="empathyLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={subTargetFormData.empathyLevel || 0.7}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetResponseTimeAverage">Response Time Average (seconds): {subTargetFormData.responseTimeAverage || 120}</label>
                      <input
                        type="range"
                        id="subTargetResponseTimeAverage"
                        name="responseTimeAverage"
                        min="0"
                        max="600"
                        step="10"
                        value={subTargetFormData.responseTimeAverage || 120.0}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetMessageLengthAverage">Message Length Average (words): {subTargetFormData.messageLengthAverage || 25}</label>
                      <input
                        type="range"
                        id="subTargetMessageLengthAverage"
                        name="messageLengthAverage"
                        min="0"
                        max="100"
                        step="1"
                        value={subTargetFormData.messageLengthAverage || 25.0}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetQuestionRate">Question Rate: {(subTargetFormData.questionRate || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="subTargetQuestionRate"
                        name="questionRate"
                        min="0"
                        max="1"
                        step="0.1"
                        value={subTargetFormData.questionRate || 0.3}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetEngagementLevel">Engagement Level: {(subTargetFormData.engagementLevel || 0).toFixed(1)}</label>
                      <input
                        type="range"
                        id="subTargetEngagementLevel"
                        name="engagementLevel"
                        min="0"
                        max="1"
                        step="0.1"
                        value={subTargetFormData.engagementLevel || 0.6}
                        onChange={handleSubTargetFormChange}
                      />
                    </div>
                    
                    <div className="form-group">
                      <label htmlFor="subTargetPreferredOpening">Preferred Opening</label>
                      <input
                        type="text"
                        id="subTargetPreferredOpening"
                        name="preferredOpening"
                        value={subTargetFormData.preferredOpening}
                        onChange={handleSubTargetFormChange}
                        placeholder="e.g., Hey! How are you doing?"
                      />
                    </div>
                  </div>
                )}
              </div>
              
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
                <button type="submit" className="btn btn-primary">
                  {editingSubTarget ? 'Update SubTarget User' : 'Create SubTarget User'}
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => {
                    setShowSubTargetForm(false);
                    setEditingSubTarget(null);
                  }}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {/* SubTarget Users List */}
        <div className="targets-grid">
          {subTargets.length === 0 ? (
            <div className="empty-state">
              <p>No platform instances yet. Click "+ Add SubTarget User" to add one.</p>
            </div>
          ) : (
            subTargets.map((subTarget) => {
              let advancedSettings = {};
              if (subTarget.advancedCommunicationSettings) {
                try {
                  advancedSettings = JSON.parse(subTarget.advancedCommunicationSettings);
                } catch (e) {}
              }
              
              return (
                <div key={subTarget.id} className="card">
                  <div className="card-content" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <h3 style={{ margin: 0 }}>{subTarget.name || subTarget.username || 'N/A'}</h3>
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => {
                        // Navigate with platform account ID for auto-filtering
                        if (subTarget.platformAccountId) {
                          navigate(`/analysis/${targetId}?platformAccountId=${subTarget.platformAccountId}`);
                        } else {
                          navigate(`/analysis/${targetId}`);
                        }
                      }}
                    >
                      View Analysis
                    </button>
                  </div>
                  <div className="card-content">
                    <p><strong>Username:</strong> {subTarget.username || 'N/A'}</p>
                    <p><strong>Platform:</strong> {subTarget.platform}</p>
                    {subTarget.number && <p><strong>Number:</strong> {subTarget.number}</p>}
                  </div>
                  <div className="card-actions">
                    <button
                      className="btn btn-primary"
                      onClick={() => handleStartConversation(subTarget.id)}
                    >
                      Start Conversation
                    </button>
                    <button
                      className="btn btn-secondary"
                      onClick={() => {
                        setEditingSubTarget(subTarget);
                        setShowSubTargetForm(true);
                        setSubTargetFormData({
                          name: subTarget.name || '',
                          username: subTarget.username || '',
                          platform: subTarget.platform || '',
                          platformAccountId: subTarget.platformAccountId,
                          number: subTarget.number || '',
                          humorLevel: advancedSettings.humorLevel || 0.4,
                          formalityLevel: advancedSettings.formalityLevel || 0.5,
                          empathyLevel: advancedSettings.empathyLevel || 0.7,
                          responseTimeAverage: advancedSettings.responseTimeAverage || 120.0,
                          messageLengthAverage: advancedSettings.messageLengthAverage || 25.0,
                          questionRate: advancedSettings.questionRate || 0.3,
                          engagementLevel: advancedSettings.engagementLevel || 0.6,
                          preferredOpening: advancedSettings.preferredOpening || 'Hey! How are you doing?',
                        });
                        setShowAdvanced(Object.keys(advancedSettings).length > 0);
                      }}
                    >
                      Edit
                    </button>
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => setConfirmDeleteSubTarget(subTarget)}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Delete Confirmation Modal */}
        {confirmDeleteSubTarget && (
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
              <h3>Delete SubTarget User</h3>
              <p>Delete {confirmDeleteSubTarget.name || confirmDeleteSubTarget.username}? This will not affect the parent Target User.</p>
              <p style={{ color: '#d32f2f', fontWeight: 'bold' }}>This action cannot be undone.</p>
              <div style={{ display: 'flex', gap: '1rem', marginTop: '1.5rem', justifyContent: 'flex-end' }}>
                <button
                  className="btn btn-secondary"
                  onClick={() => setConfirmDeleteSubTarget(null)}
                >
                  Cancel
                </button>
                <button
                  className="btn btn-danger"
                  onClick={handleDeleteSubTarget}
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default SubTargetUsersView;

