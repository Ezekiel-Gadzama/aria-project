import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { targetApi } from '../services/api';
import './TargetManagement.css';

function EditTargetUser({ userId = 1 }) {
  const { targetId } = useParams();
  const navigate = useNavigate();
  const [target, setTarget] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    bio: '',
    desiredOutcome: '',
    meetingContext: '',
    importantDetails: '',
  });
  const [profilePicture, setProfilePicture] = useState(null);
  const [profilePicturePreview, setProfilePicturePreview] = useState(null);

  useEffect(() => {
    loadTarget();
  }, [targetId]);

  const loadTarget = async () => {
    try {
      setLoading(true);
      const response = await targetApi.getById(parseInt(targetId), userId);
      if (response.data.success) {
        const targetData = response.data.data;
        setTarget(targetData);
        setFormData({
          name: targetData.name || '',
          bio: targetData.bio || '',
          desiredOutcome: targetData.desiredOutcome || '',
          meetingContext: targetData.meetingContext || '',
          importantDetails: targetData.importantDetails || '',
        });
        if (targetData.profilePictureUrl) {
          setProfilePicturePreview(targetData.profilePictureUrl);
        }
      } else {
        setError(response.data.error || 'Failed to load target user');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load target user');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData({
      ...formData,
      [name]: value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const updateData = {
        name: formData.name,
        bio: formData.bio,
        desiredOutcome: formData.desiredOutcome,
        meetingContext: formData.meetingContext,
        importantDetails: formData.importantDetails,
      };
      
      const response = await targetApi.update(parseInt(targetId), updateData, userId);
      
      if (response.data.success) {
        // Handle profile picture
        if (profilePicture) {
          try {
            await targetApi.uploadProfilePicture(parseInt(targetId), profilePicture, userId);
          } catch (picErr) {
            console.error('Failed to upload profile picture:', picErr);
          }
        }
        
        // Navigate back to targets
        navigate('/targets');
      } else {
        setError(response.data.error || 'Failed to update target user');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to update target user');
    }
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
            <h1>Edit Target User</h1>
          </div>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

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
                    e.target.style.display = 'none';
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
            <label htmlFor="importantDetails">Important Details (Optional)</label>
            <textarea
              id="importantDetails"
              name="importantDetails"
              value={formData.importantDetails}
              onChange={handleChange}
              placeholder="Any details that help AI personalize (interests, prior topics...)"
              rows="3"
            />
          </div>

          <div style={{ display: 'flex', gap: '1rem', marginTop: '2rem' }}>
            <button type="submit" className="btn btn-primary">
              Save Changes
            </button>
            <button 
              type="button" 
              className="btn btn-secondary"
              onClick={() => navigate('/targets')}
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EditTargetUser;

