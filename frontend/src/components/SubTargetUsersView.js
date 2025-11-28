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
  const [allTargets, setAllTargets] = useState([]);
  const [selectedImportTargetId, setSelectedImportTargetId] = useState('');
  const [selectedImportSubTargetId, setSelectedImportSubTargetId] = useState('');
  const [availableSubTargetsForImport, setAvailableSubTargetsForImport] = useState([]);

  useEffect(() => {
    loadTarget();
    loadPlatforms();
    loadPlatformAccounts();
  }, [targetId]);

  useEffect(() => {
    // Reload all targets when target changes (to include/exclude current target based on subtargets)
    // Only reload if target is loaded
    if (target) {
      loadAllTargets();
    }
  }, [targetId, target]);

  useEffect(() => {
    // When import target is selected, load its subtarget users
    if (selectedImportTargetId) {
      loadSubTargetsForImport(selectedImportTargetId);
    } else {
      setAvailableSubTargetsForImport([]);
      setSelectedImportSubTargetId('');
    }
  }, [selectedImportTargetId]);

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

  const loadAllTargets = async () => {
    try {
      const response = await targetApi.getAll(userId);
      if (response.data.success) {
        const allTargetsData = response.data.data || [];
        
        // Include current target if it has at least one subtarget user
        const currentTarget = allTargetsData.find(t => t.id === parseInt(targetId));
        if (currentTarget && currentTarget.subTargetUsers && currentTarget.subTargetUsers.length > 0) {
          // Include current target in the list
          setAllTargets(allTargetsData);
        } else {
          // Filter out current target if it has no subtarget users
          const filteredTargets = allTargetsData.filter(t => t.id !== parseInt(targetId));
          setAllTargets(filteredTargets);
        }
      }
    } catch (err) {
      console.error('Failed to load all targets:', err);
    }
  };

  const loadSubTargetsForImport = async (targetUserId) => {
    try {
      // First check if this is the current target (already loaded)
      if (target && target.id === parseInt(targetUserId) && target.subTargetUsers) {
        const subTargets = target.subTargetUsers || [];
        setAvailableSubTargetsForImport(subTargets);
        // If only one subtarget, auto-select it
        if (subTargets.length === 1) {
          setSelectedImportSubTargetId(subTargets[0].id.toString());
        } else {
          setSelectedImportSubTargetId('');
        }
        return;
      }

      // Then check if we already have this target in allTargets
      const existingTarget = allTargets.find(t => t.id === parseInt(targetUserId));
      if (existingTarget && existingTarget.subTargetUsers) {
        const subTargets = existingTarget.subTargetUsers || [];
        setAvailableSubTargetsForImport(subTargets);
        // If only one subtarget, auto-select it
        if (subTargets.length === 1) {
          setSelectedImportSubTargetId(subTargets[0].id.toString());
        } else {
          setSelectedImportSubTargetId('');
        }
        return;
      }

      // Otherwise, fetch the target details
      const response = await targetApi.getById(parseInt(targetUserId), userId);
      if (response.data.success) {
        const targetData = response.data.data;
        const subTargets = targetData.subTargetUsers || [];
        setAvailableSubTargetsForImport(subTargets);
        // If only one subtarget, auto-select it
        if (subTargets.length === 1) {
          setSelectedImportSubTargetId(subTargets[0].id.toString());
        } else {
          setSelectedImportSubTargetId('');
        }
      }
    } catch (err) {
      console.error('Failed to load subtargets for import:', err);
      setAvailableSubTargetsForImport([]);
    }
  };

  const handleImportSettings = async () => {
    if (!selectedImportTargetId) {
      setError('Please select a target user to import from');
      return;
    }

    // Find the target user
    let importTarget = null;
    
    // First check if it's the current target (already loaded)
    if (target && target.id === parseInt(selectedImportTargetId)) {
      importTarget = target;
    } else {
      // Then check allTargets
      importTarget = allTargets.find(t => t.id === parseInt(selectedImportTargetId));
    }
    
    // If not found, fetch it
    if (!importTarget || !importTarget.subTargetUsers) {
      try {
        const response = await targetApi.getById(parseInt(selectedImportTargetId), userId);
        if (response.data.success) {
          importTarget = response.data.data;
        } else {
          setError('Selected target user not found');
          return;
        }
      } catch (err) {
        setError('Failed to load target user details');
        return;
      }
    }

    // Get subtarget users
    const subTargets = importTarget.subTargetUsers || [];
    
    // Check if target has any subtarget users
    if (subTargets.length === 0) {
      // Show popup
      alert(`There is no Instance of "${importTarget.name}" on any platform. Hence can not import.`);
      return;
    }

    // Determine which subtarget to import from
    let subTargetToImport = null;
    if (selectedImportSubTargetId) {
      subTargetToImport = subTargets.find(st => st.id === parseInt(selectedImportSubTargetId));
    }
    
    // If no subtarget selected, use the first one
    if (!subTargetToImport && subTargets.length > 0) {
      subTargetToImport = subTargets[0];
    }

    if (!subTargetToImport) {
      setError('No subtarget user found to import from');
      return;
    }

    // Parse advanced communication settings
    let importedSettings = {};
    if (subTargetToImport.advancedCommunicationSettings) {
      try {
        importedSettings = JSON.parse(subTargetToImport.advancedCommunicationSettings);
      } catch (e) {
        console.error('Failed to parse advanced communication settings:', e);
      }
    }

    // Update form data with imported settings
    setSubTargetFormData({
      ...subTargetFormData,
      humorLevel: importedSettings.humorLevel !== undefined ? importedSettings.humorLevel : 0.4,
      formalityLevel: importedSettings.formalityLevel !== undefined ? importedSettings.formalityLevel : 0.5,
      empathyLevel: importedSettings.empathyLevel !== undefined ? importedSettings.empathyLevel : 0.7,
      responseTimeAverage: importedSettings.responseTimeAverage !== undefined ? importedSettings.responseTimeAverage : 120.0,
      messageLengthAverage: importedSettings.messageLengthAverage !== undefined ? importedSettings.messageLengthAverage : 25.0,
      questionRate: importedSettings.questionRate !== undefined ? importedSettings.questionRate : 0.3,
      engagementLevel: importedSettings.engagementLevel !== undefined ? importedSettings.engagementLevel : 0.6,
      preferredOpening: importedSettings.preferredOpening || 'Hey! How are you doing?',
    });

    // Show advanced settings if importing
    if (Object.keys(importedSettings).length > 0) {
      setShowAdvanced(true);
    }

    setError(null);
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
                navigate(`/analysis/${targetId}?from=subtargets`);
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
                    {/* Import Settings Section */}
                    <div style={{ marginBottom: '1.5rem', padding: '1rem', border: '1px solid #ccc', borderRadius: '4px', backgroundColor: '#f9f9f9' }}>
                      <h6 style={{ marginTop: 0, marginBottom: '0.75rem' }}>Import Settings from Other Target User</h6>
                      <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                        <div style={{ flex: '1', minWidth: '200px' }}>
                          <label htmlFor="importTargetUser" style={{ display: 'block', marginBottom: '0.25rem', fontSize: '0.9rem' }}>Target User:</label>
                          <select
                            id="importTargetUser"
                            value={selectedImportTargetId}
                            onChange={(e) => setSelectedImportTargetId(e.target.value)}
                            style={{ width: '100%', padding: '0.5rem' }}
                          >
                            <option value="">Select target user</option>
                            {allTargets.map((t) => (
                              <option key={t.id} value={t.id}>
                                {t.name}
                              </option>
                            ))}
                          </select>
                        </div>
                        <div style={{ flex: '1', minWidth: '200px' }}>
                          <label htmlFor="importSubTargetUser" style={{ display: 'block', marginBottom: '0.25rem', fontSize: '0.9rem' }}>SubTarget User (Optional):</label>
                          <select
                            id="importSubTargetUser"
                            value={selectedImportSubTargetId}
                            onChange={(e) => setSelectedImportSubTargetId(e.target.value)}
                            disabled={!selectedImportTargetId || availableSubTargetsForImport.length === 0}
                            style={{ width: '100%', padding: '0.5rem' }}
                          >
                            <option value="">{availableSubTargetsForImport.length === 0 ? 'No instances available' : 'Select subtarget user (or use first)'}</option>
                            {availableSubTargetsForImport.map((st) => (
                              <option key={st.id} value={st.id}>
                                {st.name || st.username} ({st.platform})
                              </option>
                            ))}
                          </select>
                        </div>
                        <div>
                          <button
                            type="button"
                            className="btn btn-sm btn-primary"
                            onClick={handleImportSettings}
                            disabled={!selectedImportTargetId}
                          >
                            Import Settings
                          </button>
                        </div>
                      </div>
                    </div>
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
                        // Add from=subtargets to indicate coming from subtarget user instance
                        const params = new URLSearchParams();
                        params.append('from', 'subtargets');
                        if (subTarget.platformAccountId) {
                          params.append('platformAccountId', subTarget.platformAccountId);
                        }
                        navigate(`/analysis/${targetId}?${params.toString()}`);
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

