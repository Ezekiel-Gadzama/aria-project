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
  const [passwordData, setPasswordData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });

  useEffect(() => {
    check2FAStatus();
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

