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
  const [showTargetForm, setShowTargetForm] = useState(false); // Show Target User form
  const [editingTarget, setEditingTarget] = useState(null); // Target being edited
  const [confirmDeleteId, setConfirmDeleteId] = useState(null); // Target ID to confirm delete
  const [confirmDeleteSubTarget, setConfirmDeleteSubTarget] = useState(null); // { target, subTarget } for delete confirmation
  
  // Target User form data (simple)
  const [targetFormData, setTargetFormData] = useState({
    name: '',
    bio: '',
    desiredOutcome: '',
    meetingContext: '',
    importantDetails: '',
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

  // Target User form handlers
  const handleTargetFormChange = (e) => {
    const { name, value } = e.target;
    setTargetFormData({
      ...targetFormData,
      [name]: value,
    });
  };

  const handleTargetSubmit = async (e) => {
    e.preventDefault();
    try {
      let response;
      if (editingTarget) {
        // Update existing target - only send Target User fields
        const updateData = {
          name: targetFormData.name,
          bio: targetFormData.bio,
          desiredOutcome: targetFormData.desiredOutcome,
          meetingContext: targetFormData.meetingContext,
          importantDetails: targetFormData.importantDetails,
        };
        response = await targetApi.update(editingTarget.id, updateData, userId);
      } else {
        // Create new target - only Target User fields
        response = await targetApi.create(targetFormData, userId);
      }
      if (response.data.success) {
        // Get target ID from response or editing target
        let targetId = editingTarget ? editingTarget.id : null;
        if (!targetId && response.data.data) {
          targetId = response.data.data.id || response.data.data;
        }
        
        // Handle profile picture
        const hadProfilePicture = editingTarget && editingTarget.profilePictureUrl;
        const profilePictureRemoved = hadProfilePicture && !profilePicture && !profilePicturePreview;
        
        if (profilePictureRemoved && targetId) {
          try {
            await targetApi.deleteProfilePicture(targetId, userId);
          } catch (deleteErr) {
            console.error('Failed to delete profile picture:', deleteErr);
          }
        } else if (profilePicture && targetId) {
          try {
            await targetApi.uploadProfilePicture(targetId, profilePicture, userId);
          } catch (picErr) {
            console.error('Failed to upload profile picture:', picErr);
          }
        }
        
        // Reset form
        setShowTargetForm(false);
        setEditingTarget(null);
        setProfilePicture(null);
        setProfilePicturePreview(null);
        setTargetFormData({ 
          name: '', bio: '', desiredOutcome: '', meetingContext: '', importantDetails: ''
        });
        loadTargets();
      } else {
        setError(response.data.error || `Failed to ${editingTarget ? 'update' : 'create'} target`);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || `Failed to ${editingTarget ? 'update' : 'create'} target`);
    }
  };

  // SubTarget User management is now on a separate page (SubTargetUsersView)

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

  const handleStartConversation = (targetId, subtargetUserId = null) => {
    // Navigate to conversation with optional subtargetUserId
    if (subtargetUserId) {
      navigate(`/conversations/${targetId}?subtargetUserId=${subtargetUserId}`);
    } else {
      navigate(`/conversations/${targetId}`);
    }
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
              if (showTargetForm && !editingTarget) {
                setShowTargetForm(false);
                setEditingTarget(null);
              } else {
                setShowTargetForm(true);
                setEditingTarget(null);
                setTargetFormData({
                  name: '', bio: '', desiredOutcome: '', meetingContext: '', importantDetails: ''
                });
                setProfilePicture(null);
                setProfilePicturePreview(null);
              }
            }}
          >
            {showTargetForm && !editingTarget ? 'Cancel' : '+ Add Target User'}
          </button>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {/* Target User Form (Simple) */}
        {showTargetForm && (
          <div className="target-form-container" style={{ marginBottom: '2rem', padding: '1.5rem', backgroundColor: '#f9f9f9', borderRadius: '8px' }}>
            <h2>{editingTarget ? 'Edit Target User' : 'Add New Target User'}</h2>
            <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '1rem' }}>
              Create a Target User with basic information. You can add platform-specific instances (SubTarget Users) after creation.
            </p>
            <form onSubmit={handleTargetSubmit} className="target-form" encType="multipart/form-data">
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
                        if (profilePicturePreview && profilePicturePreview.startsWith('blob:')) {
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
                  If no picture is uploaded, we'll automatically fetch it when you add a SubTarget User.
                </p>
              </div>
              <div className="form-group">
                <label htmlFor="name">Name *</label>
                <input
                  type="text"
                  id="name"
                  name="name"
                  value={targetFormData.name}
                  onChange={handleTargetFormChange}
                  required
                  placeholder="Enter target name"
                />
              </div>

              <div className="form-group">
                <label htmlFor="bio">Bio (Optional)</label>
                <textarea
                  id="bio"
                  name="bio"
                  value={targetFormData.bio}
                  onChange={handleTargetFormChange}
                  placeholder="Enter bio"
                  rows="3"
                />
              </div>

              <div className="form-group">
                <label htmlFor="desiredOutcome">Desired Outcome *</label>
                <textarea
                  id="desiredOutcome"
                  name="desiredOutcome"
                  value={targetFormData.desiredOutcome}
                  onChange={handleTargetFormChange}
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
                  value={targetFormData.meetingContext}
                  onChange={handleTargetFormChange}
                  placeholder="e.g., Met at a tech conference keynote Q&A"
                  rows="2"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="importantDetails">Important Details (Optional)</label>
                <textarea
                  id="importantDetails"
                  name="importantDetails"
                  value={targetFormData.importantDetails}
                  onChange={handleTargetFormChange}
                  placeholder="Any details that help AI personalize (interests, prior topics...)"
                  rows="3"
                />
              </div>

              <button type="submit" className="btn btn-primary">
                {editingTarget ? 'Update Target User' : 'Create Target User'}
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
            targets.map((target) => {
              const subTargets = target.subTargetUsers || [];
              
              return (
              <div key={target.id} className="card" style={{ marginBottom: '1rem' }}>
                <div className="card-header" style={{ 
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '0.5rem',
                  padding: '1rem'
                }}>
                  <div 
                    style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '0.5rem', 
                      flex: '1', 
                      minWidth: '200px',
                      cursor: 'pointer'
                    }}
                    onClick={() => navigate(`/targets/${target.id}/subtargets`)}
                  >
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
                          marginRight: '0.5rem',
                          flexShrink: 0
                        }}
                      >
                        {target.name.charAt(0).toUpperCase()}
                      </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', minWidth: 0 }}>
                      <h3 className="card-title" style={{ margin: 0, fontSize: '1.1rem', fontWeight: 'bold' }}>
                        {target.name}
                      </h3>
                      <span style={{ fontSize: '0.75rem', color: '#666' }}>
                        ({subTargets.length} platform{subTargets.length !== 1 ? 's' : ''})
                      </span>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(`/analysis/${target.id}?from=targets`);
                      }}
                      style={{ flexShrink: 0 }}
                    >
                      View Analysis
                    </button>
                    {/* Cross-Platform Context Toggle */}
                    <label style={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      gap: '0.25rem', 
                      fontSize: '0.85rem', 
                      cursor: 'pointer',
                      whiteSpace: 'nowrap'
                    }} title={subTargets.length === 0 ? 'Add at least one SubTarget User to use cross-platform context' : 'Enable to aggregate chat history across all platforms'}>
                      <input
                        type="checkbox"
                        checked={target.crossPlatformContextEnabled || false}
                        disabled={subTargets.length === 0}
                        onChange={async (e) => {
                          e.stopPropagation();
                          e.preventDefault();
                          if (subTargets.length === 0) {
                            alert('Please add at least one SubTarget User before enabling cross-platform context');
                            return;
                          }
                          const newValue = !target.crossPlatformContextEnabled;
                          try {
                            // Only send the fields that changed
                            const updateData = {
                              name: target.name,
                              bio: target.bio || null,
                              desiredOutcome: target.desiredOutcome || null,
                              meetingContext: target.meetingContext || null,
                              importantDetails: target.importantDetails || null,
                              crossPlatformContextEnabled: newValue
                            };
                            await targetApi.update(target.id, updateData, userId);
                            const updatedTargets = targets.map(t => 
                              t.id === target.id ? { ...t, crossPlatformContextEnabled: newValue } : t
                            );
                            setTargets(updatedTargets);
                          } catch (err) {
                            console.error('Failed to update cross-platform context:', err);
                            alert('Failed to update cross-platform context setting');
                          }
                        }}
                      />
                      <span style={{ opacity: subTargets.length === 0 ? 0.5 : 1 }}>Cross-Platform Context</span>
                    </label>
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDelete(target.id);
                      }}
                      style={{ flexShrink: 0 }}
                    >
                      Delete
                    </button>
                  </div>
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
                {/* Target User Info (always visible) */}
                <div className="card-content">
                  {target.bio && <p><strong>Bio:</strong> {target.bio}</p>}
                  {target.importantDetails && <p><strong>Important Details:</strong> {target.importantDetails}</p>}
                </div>
                
                {/* Delete SubTarget Confirmation Modal */}
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
                      <p>Delete {confirmDeleteSubTarget.subTarget.name || confirmDeleteSubTarget.subTarget.username}? This will not affect the parent Target User.</p>
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
                          onClick={async () => {
                            try {
                              const currentTarget = targets.find(t => t.id === confirmDeleteSubTarget.target.id);
                              const updatedSubTargets = currentTarget.subTargetUsers.filter(st => st.id !== confirmDeleteSubTarget.subTarget.id);
                              
                              const updateData = {
                                name: confirmDeleteSubTarget.target.name,
                                bio: confirmDeleteSubTarget.target.bio || null,
                                desiredOutcome: confirmDeleteSubTarget.target.desiredOutcome || null,
                                meetingContext: confirmDeleteSubTarget.target.meetingContext || null,
                                importantDetails: confirmDeleteSubTarget.target.importantDetails || null,
                                crossPlatformContextEnabled: confirmDeleteSubTarget.target.crossPlatformContextEnabled || false,
                                subTargetUsers: updatedSubTargets,
                              };
                              
                              const response = await targetApi.update(confirmDeleteSubTarget.target.id, updateData, userId);
                              if (response.data.success) {
                                setConfirmDeleteSubTarget(null);
                                loadTargets();
                              } else {
                                setError(response.data.error || 'Failed to delete SubTarget User');
                                setConfirmDeleteSubTarget(null);
                              }
                            } catch (err) {
                              setError(err.response?.data?.error || err.message || 'Failed to delete SubTarget User');
                              setConfirmDeleteSubTarget(null);
                            }
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  </div>
                )}
                
                {/* SubTarget Users are now managed on a separate page - navigate using "View Platform Instances" button */}
                
                {/* Target User Actions */}
                <div className="card-actions" style={{ marginTop: '1rem', padding: '0 1rem 1rem 1rem', display: 'flex', gap: '0.5rem' }}>
                  <button
                    className="btn btn-secondary"
                    onClick={(e) => {
                      e.stopPropagation();
                      navigate(`/targets/${target.id}/edit`);
                    }}
                    style={{ 
                      padding: '0.5rem 1rem',
                      borderRadius: '4px',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '0.9rem',
                      fontWeight: '500'
                    }}
                  >
                    Edit Target User
                  </button>
                  <button
                    className="btn btn-primary"
                    onClick={(e) => {
                      e.stopPropagation();
                      navigate(`/targets/${target.id}/subtargets`);
                    }}
                    style={{ 
                      padding: '0.5rem 1rem',
                      borderRadius: '4px',
                      border: 'none',
                      cursor: 'pointer',
                      fontSize: '0.9rem',
                      fontWeight: '500'
                    }}
                  >
                    View Instances
                  </button>
                </div>
              </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}

export default TargetManagement;

