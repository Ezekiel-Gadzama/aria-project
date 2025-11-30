import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { businessApi, platformApi } from '../services/api';
import BusinessBotChat from './BusinessBotChat';
import './BusinessManagement.css';

function BusinessManagement({ userId = 1 }) {
  const navigate = useNavigate();
  const [businesses, setBusinesses] = useState([]);
  const [platforms, setPlatforms] = useState([]);
  const [platformAccounts, setPlatformAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [showBusinessForm, setShowBusinessForm] = useState(false);
  const [editingBusiness, setEditingBusiness] = useState(null);
  const [selectedBusiness, setSelectedBusiness] = useState(null);
  const [showBotChat, setShowBotChat] = useState(false);
  const [showSubTargetForm, setShowSubTargetForm] = useState(false);
  const [selectedBusinessForSubTarget, setSelectedBusinessForSubTarget] = useState(null);
  
  // Business form data
  const [businessFormData, setBusinessFormData] = useState({
    name: '',
    description: ''
  });
  
  // Sub-target form data
  const [subTargetFormData, setSubTargetFormData] = useState({
    name: '',
    type: 'GROUP', // CHANNEL, GROUP, or PRIVATE_CHAT
    platform: '',
    platformAccountId: '',
    dialogId: '',
    platformId: '',
    username: '',
    description: ''
  });

  useEffect(() => {
    loadBusinesses();
    loadPlatforms();
    loadPlatformAccounts();
  }, []);

  const loadBusinesses = async () => {
    try {
      setLoading(true);
      const response = await businessApi.getAll(userId);
      if (response.data?.success) {
        setBusinesses(response.data.data || []);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load businesses');
    } finally {
      setLoading(false);
    }
  };

  const loadPlatforms = async () => {
    try {
      const response = await platformApi.getAll();
      if (response.data?.success) {
        setPlatforms(response.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load platforms:', err);
    }
  };

  const loadPlatformAccounts = async () => {
    try {
      const response = await platformApi.getAccounts(userId);
      if (response.data?.success) {
        setPlatformAccounts(response.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load platform accounts:', err);
    }
  };

  const handleBusinessSubmit = async (e) => {
    e.preventDefault();
    try {
      setError(null);
      setSuccess(null);
      
      if (editingBusiness) {
        const response = await businessApi.update(editingBusiness.id, businessFormData, userId);
        if (response.data?.success) {
          setSuccess('Business updated successfully!');
          setShowBusinessForm(false);
          setEditingBusiness(null);
          setBusinessFormData({ name: '', description: '' });
          loadBusinesses();
        } else {
          setError(response.data?.error || 'Failed to update business');
        }
      } else {
        const response = await businessApi.create(businessFormData, userId);
        if (response.data?.success) {
          setSuccess('Business created successfully!');
          setShowBusinessForm(false);
          setBusinessFormData({ name: '', description: '' });
          loadBusinesses();
        } else {
          setError(response.data?.error || 'Failed to create business');
        }
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to save business');
    }
  };

  const handleDeleteBusiness = async (id) => {
    if (!window.confirm('Are you sure you want to delete this business? All sub-targets will also be deleted.')) {
      return;
    }
    try {
      setError(null);
      const response = await businessApi.delete(id, userId);
      if (response.data?.success) {
        setSuccess('Business deleted successfully!');
        loadBusinesses();
      } else {
        setError(response.data?.error || 'Failed to delete business');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete business');
    }
  };

  const handleEditBusiness = (business) => {
    setEditingBusiness(business);
    setBusinessFormData({
      name: business.name || '',
      description: business.description || ''
    });
    setShowBusinessForm(true);
  };

  const handleAddSubTarget = async (business) => {
    // Load full business details to get sub-targets
    try {
      const response = await businessApi.getById(business.id, userId);
      if (response.data?.success) {
        const fullBusiness = response.data.data;
        setSelectedBusinessForSubTarget(fullBusiness);
        setSubTargetFormData({
          name: '',
          type: 'GROUP',
          platform: '',
          platformAccountId: '',
          dialogId: '',
          platformId: '',
          username: '',
          description: ''
        });
        setShowSubTargetForm(true);
      } else {
        setSelectedBusinessForSubTarget(business);
        setSubTargetFormData({
          name: '',
          type: 'GROUP',
          platform: '',
          platformAccountId: '',
          dialogId: '',
          platformId: '',
          username: '',
          description: ''
        });
        setShowSubTargetForm(true);
      }
    } catch (err) {
      // Fallback to basic business object
      setSelectedBusinessForSubTarget(business);
      setSubTargetFormData({
        name: '',
        type: 'GROUP',
        platform: '',
        platformAccountId: '',
        dialogId: '',
        platformId: '',
        username: '',
        description: ''
      });
      setShowSubTargetForm(true);
    }
  };

  const handleSubTargetSubmit = async (e) => {
    e.preventDefault();
    try {
      setError(null);
      setSuccess(null);
      
      const subTargetData = {
        name: subTargetFormData.name,
        type: subTargetFormData.type,
        platform: subTargetFormData.platform,
        platformAccountId: subTargetFormData.platformAccountId ? parseInt(subTargetFormData.platformAccountId) : null,
        dialogId: subTargetFormData.dialogId ? parseInt(subTargetFormData.dialogId) : null,
        platformId: subTargetFormData.platformId ? parseInt(subTargetFormData.platformId) : 0,
        username: subTargetFormData.username,
        description: subTargetFormData.description
      };
      
      const response = await businessApi.addSubTarget(selectedBusinessForSubTarget.id, subTargetData, userId);
      if (response.data?.success) {
        setSuccess('Sub-target added successfully!');
        setShowSubTargetForm(false);
        setSelectedBusinessForSubTarget(null);
        setSubTargetFormData({
          name: '',
          type: 'GROUP',
          platform: '',
          platformAccountId: '',
          dialogId: '',
          platformId: '',
          username: '',
          description: ''
        });
        // Reload businesses and update the specific business with full details
        await loadBusinesses();
        // Reload the specific business to get updated sub-targets
        if (selectedBusinessForSubTarget) {
          try {
            const businessResponse = await businessApi.getById(selectedBusinessForSubTarget.id, userId);
            if (businessResponse.data?.success) {
              setBusinesses(prev => prev.map(b => 
                b.id === selectedBusinessForSubTarget.id ? businessResponse.data.data : b
              ));
            }
          } catch (err) {
            // Ignore error, just use the count
          }
        }
      } else {
        setError(response.data?.error || 'Failed to add sub-target');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to add sub-target');
    }
  };

  const handleDeleteSubTarget = async (businessId, subTargetId) => {
    if (!window.confirm('Are you sure you want to remove this sub-target?')) {
      return;
    }
    try {
      setError(null);
      const response = await businessApi.deleteSubTarget(businessId, subTargetId, userId);
      if (response.data?.success) {
        setSuccess('Sub-target removed successfully!');
        await loadBusinesses();
        // Reload the specific business to get updated sub-targets
        try {
          const businessResponse = await businessApi.getById(businessId, userId);
          if (businessResponse.data?.success) {
            setBusinesses(prev => prev.map(b => 
              b.id === businessId ? businessResponse.data.data : b
            ));
          }
        } catch (err) {
          // Ignore error, just use the count
        }
      } else {
        setError(response.data?.error || 'Failed to remove sub-target');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to remove sub-target');
    }
  };

  const handleOpenBotChat = async (business) => {
    // Load full business details for bot chat
    try {
      const response = await businessApi.getById(business.id, userId);
      if (response.data?.success) {
        setSelectedBusiness(response.data.data);
      } else {
        setSelectedBusiness(business);
      }
    } catch (err) {
      setSelectedBusiness(business);
    }
    setShowBotChat(true);
  };

  const handleCloseBotChat = () => {
    setShowBotChat(false);
    setSelectedBusiness(null);
  };

  if (loading && businesses.length === 0) {
    return <div className="spinner"></div>;
  }

  return (
    <div className="business-management">
      <div className="container">
        <div className="header">
          <h1>Target Business</h1>
          <button 
            className="btn btn-primary" 
            onClick={() => {
              if (showBusinessForm && !editingBusiness) {
                setShowBusinessForm(false);
                setEditingBusiness(null);
              } else {
                setShowBusinessForm(true);
                setEditingBusiness(null);
                setBusinessFormData({ name: '', description: '' });
              }
            }}
          >
            {showBusinessForm && !editingBusiness ? 'Cancel' : '+ Add Business'}
          </button>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {success && <div className="alert alert-success">{success}</div>}

        {/* Business Form */}
        {showBusinessForm && (
          <div className="business-form-container">
            <h2>{editingBusiness ? 'Edit Business' : 'Add New Business'}</h2>
            <form onSubmit={handleBusinessSubmit} className="business-form">
              <div className="form-group">
                <label htmlFor="name">Business Name *</label>
                <input
                  type="text"
                  id="name"
                  value={businessFormData.name}
                  onChange={(e) => setBusinessFormData({ ...businessFormData, name: e.target.value })}
                  required
                  placeholder="e.g., Company Projects, Team Communications"
                />
              </div>
              <div className="form-group">
                <label htmlFor="description">Description (Optional)</label>
                <textarea
                  id="description"
                  value={businessFormData.description}
                  onChange={(e) => setBusinessFormData({ ...businessFormData, description: e.target.value })}
                  rows="3"
                  placeholder="Describe the purpose of this business context"
                />
              </div>
              <div className="form-actions">
                <button type="submit" className="btn btn-primary">
                  {editingBusiness ? 'Update Business' : 'Create Business'}
                </button>
                <button 
                  type="button" 
                  className="btn btn-secondary"
                  onClick={() => {
                    setShowBusinessForm(false);
                    setEditingBusiness(null);
                    setBusinessFormData({ name: '', description: '' });
                  }}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Sub-Target Form */}
        {showSubTargetForm && selectedBusinessForSubTarget && (
          <div className="subtarget-form-container">
            <h2>Add Sub-Target to {selectedBusinessForSubTarget.name}</h2>
            <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '1rem' }}>
              Add a channel, group, or private chat to this business context.
            </p>
            <form onSubmit={handleSubTargetSubmit} className="subtarget-form">
              <div className="form-group">
                <label htmlFor="subtarget-name">Name *</label>
                <input
                  type="text"
                  id="subtarget-name"
                  value={subTargetFormData.name}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, name: e.target.value })}
                  required
                  placeholder="e.g., PILOT - WIREPAS - VELAVU"
                />
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-type">Type *</label>
                <select
                  id="subtarget-type"
                  value={subTargetFormData.type}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, type: e.target.value })}
                  required
                >
                  <option value="CHANNEL">Channel</option>
                  <option value="GROUP">Group</option>
                  <option value="PRIVATE_CHAT">Private Chat</option>
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-platform">Platform *</label>
                <select
                  id="subtarget-platform"
                  value={subTargetFormData.platform}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, platform: e.target.value })}
                  required
                >
                  <option value="">Select platform</option>
                  {platforms.map((platform) => (
                    <option key={platform} value={platform}>{platform}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-account">Platform Account</label>
                <select
                  id="subtarget-account"
                  value={subTargetFormData.platformAccountId}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, platformAccountId: e.target.value })}
                >
                  <option value="">Select account</option>
                  {platformAccounts.map((account) => (
                    <option key={account.id} value={account.id}>{account.username || account.phoneNumber}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-dialog-id">Dialog ID (Optional)</label>
                <input
                  type="number"
                  id="subtarget-dialog-id"
                  value={subTargetFormData.dialogId}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, dialogId: e.target.value })}
                  placeholder="Dialog ID from dialogs table"
                />
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-platform-id">Platform ID (Optional)</label>
                <input
                  type="number"
                  id="subtarget-platform-id"
                  value={subTargetFormData.platformId}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, platformId: e.target.value })}
                  placeholder="Platform-specific ID (e.g., Telegram chat ID)"
                />
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-username">Username (Optional)</label>
                <input
                  type="text"
                  id="subtarget-username"
                  value={subTargetFormData.username}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, username: e.target.value })}
                  placeholder="Platform username"
                />
              </div>
              <div className="form-group">
                <label htmlFor="subtarget-description">Description (Optional)</label>
                <textarea
                  id="subtarget-description"
                  value={subTargetFormData.description}
                  onChange={(e) => setSubTargetFormData({ ...subTargetFormData, description: e.target.value })}
                  rows="2"
                  placeholder="Optional description"
                />
              </div>
              <div className="form-actions">
                <button type="submit" className="btn btn-primary">
                  Add Sub-Target
                </button>
                <button 
                  type="button" 
                  className="btn btn-secondary"
                  onClick={() => {
                    setShowSubTargetForm(false);
                    setSelectedBusinessForSubTarget(null);
                  }}
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {/* Businesses List */}
        {businesses.length === 0 && !showBusinessForm ? (
          <div className="empty-state">
            <p>No businesses created yet.</p>
            <p>Create a business to start aggregating conversations from channels, groups, and private chats.</p>
          </div>
        ) : (
          <div className="businesses-grid">
            {businesses.map((business) => (
              <div key={business.id} className="business-card">
                <div className="business-card-header">
                  <h3>{business.name}</h3>
                  <div className="business-actions">
                    <button
                      className="btn btn-sm btn-secondary"
                      onClick={() => handleEditBusiness(business)}
                      title="Edit Business"
                    >
                      Edit
                    </button>
                    <button
                      className="btn btn-sm btn-danger"
                      onClick={() => handleDeleteBusiness(business.id)}
                      title="Delete Business"
                    >
                      Delete
                    </button>
                  </div>
                </div>
                {business.description && (
                  <p className="business-description">{business.description}</p>
                )}
                <div className="business-info">
                  <p><strong>Sub-Targets:</strong> {business.subTargetsCount || 0}</p>
                </div>
                
                {/* Sub-Targets List - Load on demand */}
                {business.subTargets && business.subTargets.length > 0 ? (
                  <div className="subtargets-list">
                    <h4>Sub-Targets:</h4>
                    {business.subTargets.map((subTarget) => (
                      <div key={subTarget.id} className="subtarget-item">
                        <div>
                          <strong>{subTarget.name}</strong>
                          <span className="subtarget-badge">{subTarget.type}</span>
                          <span className="subtarget-badge">{subTarget.platform}</span>
                        </div>
                        <button
                          className="btn btn-sm btn-danger"
                          onClick={() => handleDeleteSubTarget(business.id, subTarget.id)}
                          title="Remove Sub-Target"
                        >
                          Remove
                        </button>
                      </div>
                    ))}
                  </div>
                ) : business.subTargetsCount > 0 && (
                  <div className="subtargets-list">
                    <p style={{ fontSize: '0.9rem', color: '#666' }}>
                      {business.subTargetsCount} sub-target(s) configured. 
                      Click "View Details" to see them.
                    </p>
                  </div>
                )}
                
                <div className="business-card-actions">
                  <button
                    className="btn btn-primary"
                    onClick={() => handleAddSubTarget(business)}
                  >
                    + Add Sub-Target
                  </button>
                  <button
                    className="btn btn-secondary"
                    onClick={() => handleOpenBotChat(business)}
                  >
                    🤖 Bot Chat
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Bot Chat Modal */}
      {showBotChat && selectedBusiness && (
        <BusinessBotChat
          business={selectedBusiness}
          userId={userId}
          onClose={handleCloseBotChat}
        />
      )}
    </div>
  );
}

export default BusinessManagement;

