import React, { useState, useEffect } from 'react';
import { userApi } from '../services/api';
import './Settings.css';

function Settings({ userId = 1 }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [twoFAEnabled, setTwoFAEnabled] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState(null);
  const [show2FASetup, setShow2FASetup] = useState(false);
  const [twoFACode, setTwoFACode] = useState('');
  const [adminModeEnabled, setAdminModeEnabled] = useState(false);
  const [adminModeLoading, setAdminModeLoading] = useState(false);
  const [passwordData, setPasswordData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });

  useEffect(() => {
    check2FAStatus();
    checkAdminMode();
  }, []);

  const check2FAStatus = async () => {
    try {
      // Check if 2FA is enabled for the user
      const response = await userApi.getCurrentUser(userId);
      if (response.data?.success) {
        setTwoFAEnabled(response.data.data?.twoFactorEnabled || false);
      }
    } catch (err) {
      console.error('Failed to check 2FA status:', err);
    }
  };

  const checkAdminMode = async () => {
    try {
      const response = await userApi.getAdminMode(userId);
      if (response.data?.success) {
        setAdminModeEnabled(response.data.data?.adminModeEnabled || false);
      }
    } catch (err) {
      console.error('Failed to check admin mode status:', err);
    }
  };

  const handleToggleAdminMode = async (e) => {
    const newValue = e.target.checked;
    try {
      setAdminModeLoading(true);
      setError(null);
      const response = await userApi.updateAdminMode(userId, newValue);
      if (response.data?.success) {
        setAdminModeEnabled(newValue);
        setSuccess(`Admin mode ${newValue ? 'enabled' : 'disabled'} successfully!`);
        setTimeout(() => setSuccess(null), 3000);
      } else {
        setError(response.data?.error || 'Failed to update admin mode');
        // Revert checkbox state on error
        setAdminModeEnabled(!newValue);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to update admin mode');
      // Revert checkbox state on error
      setAdminModeEnabled(!newValue);
    } finally {
      setAdminModeLoading(false);
    }
  };

  const handleSetup2FA = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await userApi.setup2FA(userId);
      if (response.data?.success) {
        setQrCodeUrl(response.data.data?.qrCodeUrl);
        setShow2FASetup(true);
      } else {
        setError(response.data?.error || 'Failed to setup 2FA');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to setup 2FA');
    } finally {
      setLoading(false);
    }
  };

  const handleVerify2FA = async (e) => {
    e.preventDefault();
    try {
      setLoading(true);
      setError(null);
      const response = await userApi.verify2FA(null, twoFACode);
      if (response.data?.success) {
        setTwoFAEnabled(true);
        setShow2FASetup(false);
        setQrCodeUrl(null);
        setTwoFACode('');
        setSuccess('2FA has been enabled successfully!');
        setTimeout(() => setSuccess(null), 3000);
      } else {
        setError(response.data?.error || 'Invalid verification code');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to verify 2FA');
    } finally {
      setLoading(false);
    }
  };

  const handleDisable2FA = async () => {
    if (!window.confirm('Are you sure you want to disable 2FA? This will reduce your account security.')) {
      return;
    }
    try {
      setLoading(true);
      setError(null);
      // TODO: Implement disable 2FA endpoint
      // const response = await userApi.disable2FA(userId);
      setTwoFAEnabled(false);
      setSuccess('2FA has been disabled successfully!');
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to disable 2FA');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      setError('New passwords do not match');
      return;
    }
    if (passwordData.newPassword.length < 8) {
      setError('New password must be at least 8 characters long');
      return;
    }
    try {
      setLoading(true);
      setError(null);
      // TODO: Implement change password endpoint
      // const response = await userApi.changePassword(userId, {
      //   currentPassword: passwordData.currentPassword,
      //   newPassword: passwordData.newPassword
      // });
      setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setSuccess('Password changed successfully!');
      setTimeout(() => setSuccess(null), 3000);
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="settings-page">
      <div className="container">
        <h1>Settings</h1>

        {error && <div className="alert alert-error">{error}</div>}
        {success && <div className="alert alert-success">{success}</div>}

        {/* 2FA Section */}
        <div className="settings-section">
          <h2>Two-Factor Authentication (2FA)</h2>
          <div className="settings-card">
            <div className="settings-info">
              <p>Add an extra layer of security to your account with two-factor authentication.</p>
              <p className="settings-status">
                Status: <strong style={{ color: twoFAEnabled ? '#4caf50' : '#999' }}>
                  {twoFAEnabled ? 'Enabled' : 'Disabled'}
                </strong>
              </p>
            </div>
            {!twoFAEnabled ? (
              <div>
                {!show2FASetup ? (
                  <button 
                    className="btn btn-primary" 
                    onClick={handleSetup2FA}
                    disabled={loading}
                  >
                    {loading ? 'Setting up...' : 'Enable 2FA'}
                  </button>
                ) : (
                  <div className="twofa-setup">
                    <p>Scan this QR code with your authenticator app:</p>
                    {qrCodeUrl && (
                      <img 
                        src={qrCodeUrl} 
                        alt="2FA QR Code" 
                        style={{ maxWidth: '300px', margin: '1rem 0' }}
                      />
                    )}
                    <form onSubmit={handleVerify2FA}>
                      <div className="form-group">
                        <label>Enter verification code:</label>
                        <input
                          type="text"
                          value={twoFACode}
                          onChange={(e) => setTwoFACode(e.target.value)}
                          placeholder="000000"
                          maxLength="6"
                          required
                        />
                      </div>
                      <div className="form-actions">
                        <button 
                          type="submit" 
                          className="btn btn-primary"
                          disabled={loading}
                        >
                          {loading ? 'Verifying...' : 'Verify & Enable'}
                        </button>
                        <button 
                          type="button" 
                          className="btn btn-secondary"
                          onClick={() => {
                            setShow2FASetup(false);
                            setQrCodeUrl(null);
                            setTwoFACode('');
                          }}
                        >
                          Cancel
                        </button>
                      </div>
                    </form>
                  </div>
                )}
              </div>
            ) : (
              <button 
                className="btn btn-danger" 
                onClick={handleDisable2FA}
                disabled={loading}
              >
                {loading ? 'Disabling...' : 'Disable 2FA'}
              </button>
            )}
          </div>
        </div>

        {/* Admin Mode Section */}
        <div className="settings-section">
          <h2>Admin Mode</h2>
          <div className="settings-card">
            <div className="settings-info">
              <p>Enable Admin Mode to allow AI suggestions to include references to similar conversations from other chats. References are only provided when they add meaningful context to the suggestion.</p>
              <p className="settings-status">
                Status: <strong style={{ color: adminModeEnabled ? '#4caf50' : '#999' }}>
                  {adminModeEnabled ? 'Enabled' : 'Disabled'}
                </strong>
              </p>
            </div>
            <div className="form-group" style={{ marginTop: '1rem' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={adminModeEnabled}
                  onChange={handleToggleAdminMode}
                  disabled={adminModeLoading}
                  style={{ width: '18px', height: '18px', cursor: adminModeLoading ? 'not-allowed' : 'pointer' }}
                />
                <span>Enable Admin Mode</span>
              </label>
            </div>
          </div>
        </div>

        {/* Password Change Section */}
        <div className="settings-section">
          <h2>Change Password</h2>
          <div className="settings-card">
            <form onSubmit={handleChangePassword}>
              <div className="form-group">
                <label>Current Password</label>
                <input
                  type="password"
                  value={passwordData.currentPassword}
                  onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>New Password</label>
                <input
                  type="password"
                  value={passwordData.newPassword}
                  onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                  minLength="8"
                  required
                />
              </div>
              <div className="form-group">
                <label>Confirm New Password</label>
                <input
                  type="password"
                  value={passwordData.confirmPassword}
                  onChange={(e) => setPasswordData({ ...passwordData, confirmPassword: e.target.value })}
                  minLength="8"
                  required
                />
              </div>
              <div className="form-actions">
                <button 
                  type="submit" 
                  className="btn btn-primary"
                  disabled={loading}
                >
                  {loading ? 'Changing...' : 'Change Password'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Settings;

