import React, { useState } from 'react';
import { platformApi } from '../services/api';
import './PlatformRegistration.css';

function PlatformRegistration({ userId = 1 }) {
  const [platforms, setPlatforms] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [selectedPlatform, setSelectedPlatform] = useState('');
  const [credentials, setCredentials] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  React.useEffect(() => {
    loadPlatforms();
    loadAccounts();
  }, []);

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

  const loadAccounts = async () => {
    try {
      const response = await platformApi.getAccounts(userId);
      if (response.data.success) {
        setAccounts(response.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load platform accounts:', err);
    }
  };

  const handlePlatformChange = (e) => {
    setSelectedPlatform(e.target.value);
    setCredentials({});
    setError(null);
  };

  const handleCredentialChange = (e) => {
    setCredentials({
      ...credentials,
      [e.target.name]: e.target.value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setSuccess(false);

    try {
      // For Telegram, verify OTP first if provided
      if (selectedPlatform === 'TELEGRAM') {
        const { apiId, apiHash, phoneNumber, username, otpCode, password } = credentials;
        if (!otpCode) {
          setError('Please enter the OTP code sent to your Telegram before registering.');
          setLoading(false);
          return;
        }
        await platformApi.verifyTelegramOtp({ apiId, apiHash, phoneNumber, username, code: otpCode, password });
      }
      const response = await platformApi.register(selectedPlatform, credentials, userId);
      if (response.data.success) {
        setSuccess(true);
        setCredentials({});
        setSelectedPlatform('');
        loadAccounts();
        setTimeout(() => setSuccess(false), 3000);
      } else {
        setError(response.data.error || 'Registration failed');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const handleDeleteAccount = async (id) => {
    setConfirmDeleteId(id);
  };
  const performDeleteAccount = async () => {
    try {
      const response = await platformApi.deleteAccount(confirmDeleteId, userId);
      if (response.data.success) {
        setConfirmDeleteId(null);
        loadAccounts();
      } else {
        setError(response.data.error || 'Failed to delete platform account');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to delete platform account');
    }
  };
  const cancelDelete = () => setConfirmDeleteId(null);

  const renderPlatformForm = () => {
    if (selectedPlatform === 'TELEGRAM') {
      return (
        <>
          <div className="form-group">
            <label htmlFor="username">Telegram Username *</label>
            <input
              type="text"
              id="username"
              name="username"
              value={credentials.username || ''}
              onChange={handleCredentialChange}
              required
              placeholder="@your_username"
            />
          </div>
          <div className="form-group">
            <label htmlFor="apiId">API ID *</label>
            <input
              type="text"
              id="apiId"
              name="apiId"
              value={credentials.apiId || ''}
              onChange={handleCredentialChange}
              required
              placeholder="Get from https://my.telegram.org/apps"
            />
          </div>
          <div className="form-group">
            <label htmlFor="apiHash">API Hash *</label>
            <input
              type="text"
              id="apiHash"
              name="apiHash"
              value={credentials.apiHash || ''}
              onChange={handleCredentialChange}
              required
              placeholder="Get from https://my.telegram.org/apps"
            />
          </div>
          <div className="form-group">
            <label htmlFor="phoneNumber">Phone Number *</label>
            <input
              type="tel"
              id="phoneNumber"
              name="phoneNumber"
              value={credentials.phoneNumber || ''}
              onChange={handleCredentialChange}
              required
              placeholder="+1234567890"
            />
          </div>
          <div className="form-group">
            <label htmlFor="otpCode">Enter OTP</label>
            <div style={{ display: 'flex', gap: 8 }}>
              <input
                type="text"
                id="otpCode"
                name="otpCode"
                value={credentials.otpCode || ''}
                onChange={handleCredentialChange}
                placeholder="6-digit code"
              />
              <button
                type="button"
                className="btn btn-secondary"
                onClick={async () => {
                  try {
                    const { apiId, apiHash, phoneNumber, username } = credentials;
                    if (!apiId || !apiHash || !phoneNumber) {
                      setError('Provide API ID, API Hash, and Phone Number first.');
                      return;
                    }
                    await platformApi.sendTelegramOtp({ apiId, apiHash, phoneNumber, username });
                    setError(null);
                  } catch (err) {
                    setError(err.response?.data?.error || err.message || 'Failed to send OTP');
                  }
                }}
              >
                Send OTP
              </button>
            </div>
            <small className="hint">Optional: If your account has 2FA, enter password below.</small>
          </div>
          <div className="form-group">
            <label htmlFor="password">Telegram 2FA Password (if enabled)</label>
            <input
              type="password"
              id="password"
              name="password"
              value={credentials.password || ''}
              onChange={handleCredentialChange}
              placeholder="Optional"
            />
          </div>
        </>
      );
    }
    // Add more platform forms as needed
    return <p>Platform registration form coming soon</p>;
  };

  return (
    <div className="platform-registration">
      <div className="container">
        <h1>Platform Registration</h1>
        <p className="subtitle">Connect your social media accounts</p>

        {error && <div className="alert alert-error">{error}</div>}
        {success && <div className="alert alert-success">Platform registered successfully!</div>}

        <form onSubmit={handleSubmit} className="platform-form">
          <div className="form-group">
            <label htmlFor="platform">Select Platform *</label>
            <select
              id="platform"
              value={selectedPlatform}
              onChange={handlePlatformChange}
              required
            >
              <option value="">Select a platform</option>
              {platforms.map((platform) => (
                <option key={platform} value={platform}>
                  {platform}
                </option>
              ))}
            </select>
          </div>

          {selectedPlatform && renderPlatformForm()}

          {selectedPlatform && (
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Registering...' : 'Register Platform'}
            </button>
          )}
        </form>

        <div className="accounts-list card">
          <h2>Connected Accounts</h2>
          {accounts.length === 0 ? (
            <p className="muted">No platforms connected yet.</p>
          ) : (
            <ul className="account-list">
              {accounts.map((acc) => (
                <li key={acc.id} className="account-item row">
                  <div className="col grow">
                    <span className="badge">{acc.platform}</span>{' '}
                    <span className="mono">
                      {acc.username && `(@${String(acc.username).replace(/^@/, '')})`}{' '}
                      {acc.number && `[${acc.number}]`}
                    </span>
                  </div>
                  <div className="col">
                    <button
                      className="btn btn-danger btn-sm"
                      onClick={() => handleDeleteAccount(acc.id)}
                    >
                      Delete
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {confirmDeleteId !== null && (
            <div className="modal-overlay">
              <div className="modal">
                <h3>Delete Platform</h3>
                <p>
                  This will permanently remove the platform account and ALL associated data:
                  dialogs, messages/media, and any target users linked to this account.
                </p>
                <p><strong>This action cannot be undone.</strong></p>
                <div className="modal-actions">
                  <button className="btn btn-secondary" onClick={cancelDelete}>Cancel</button>
                  <button className="btn btn-danger" onClick={performDeleteAccount}>Delete</button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default PlatformRegistration;

