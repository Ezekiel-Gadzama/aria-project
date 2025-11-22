import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { userApi } from '../services/api';

function Login({ onLogin }) {
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showPassword, setShowPassword] = useState(false);
  const [show2FA, setShow2FA] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [tempSession, setTempSession] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const payload =
        identifier.includes('@')
          ? { email: identifier, password }
          : { phoneNumber: identifier, password };
      const resp = await userApi.login(payload);
      if (resp.data?.success) {
        const userData = resp.data.data;
        
        // Check if 2FA is required
        if (resp.data.data.requires2FA) {
          setTempSession(resp.data.data.tempSession);
          // TODO: Fetch QR code from backend
          // const qrResp = await userApi.get2FAQRCode(resp.data.data.tempSession);
          // setQrCodeUrl(qrResp.data.data.qrCodeUrl);
          setShow2FA(true);
          setLoading(false);
          return;
        }
        
        // Use userId from response or default to 1
        const user = { id: userData.id || 1, ...userData };
        onLogin(user);
        navigate('/targets');
      } else {
        setError(resp.data?.error || 'Login failed');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handle2FASubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      // TODO: Implement 2FA verification endpoint
      // const resp = await userApi.verify2FA(tempSession, twoFactorCode);
      // if (resp.data?.success) {
      //   const userData = resp.data.data;
      //   const user = { id: userData.id || 1, ...userData };
      //   onLogin(user);
      //   navigate('/targets');
      // } else {
      //   setError(resp.data?.error || '2FA verification failed');
      // }
      
      // Mock implementation for now
      alert('2FA verification - Backend implementation needed');
    } catch (err) {
      setError(err.response?.data?.error || err.message || '2FA verification failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="user-registration">
      <div className="container">
        <h1>Login</h1>
        {error && <div className="alert alert-error">{error}</div>}
        {!show2FA ? (
          <form onSubmit={handleSubmit} className="registration-form">
            <div className="form-group">
              <label htmlFor="identifier">Email or Phone</label>
              <input
                id="identifier"
                name="identifier"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder="you@example.com or +1234567890"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="password">Password</label>
              <div className="password-field">
                <input
                  type={showPassword ? 'text' : 'password'}
                  id="password"
                  name="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="password-toggle"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? '🙈' : '👁️'}
                </button>
              </div>
            </div>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Authenticating...' : 'Login'}
            </button>
          </form>
        ) : (
          <form onSubmit={handle2FASubmit} className="registration-form">
            <h2>Two-Factor Authentication</h2>
            {qrCodeUrl && (
              <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
                <p>Scan this QR code with your authenticator app:</p>
                <img 
                  src={qrCodeUrl} 
                  alt="2FA QR Code" 
                  style={{ maxWidth: '200px', border: '1px solid #ddd', borderRadius: '8px' }}
                />
              </div>
            )}
            <div className="form-group">
              <label htmlFor="twoFactorCode">Enter 6-digit code</label>
              <input
                id="twoFactorCode"
                name="twoFactorCode"
                type="text"
                value={twoFactorCode}
                onChange={(e) => setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                maxLength={6}
                required
                style={{ textAlign: 'center', fontSize: '1.5rem', letterSpacing: '0.5rem' }}
              />
            </div>
            <button type="submit" className="btn btn-primary" disabled={loading || twoFactorCode.length !== 6}>
              {loading ? 'Verifying...' : 'Verify'}
            </button>
            <button 
              type="button" 
              className="btn btn-secondary" 
              onClick={() => {
                setShow2FA(false);
                setTwoFactorCode('');
                setQrCodeUrl(null);
                setTempSession(null);
              }}
              style={{ marginTop: '0.5rem' }}
            >
              Back to Login
            </button>
          </form>
        )}
        <div style={{ marginTop: 16 }}>
          <button className="btn btn-link" onClick={() => navigate('/')}>
            Back to Registration
          </button>
        </div>
      </div>
    </div>
  );
}

export default Login;


