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
        // Use userId=1 for now; backend returns DTO without id
        onLogin({ id: 1, ...resp.data.data });
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

  return (
    <div className="user-registration">
      <div className="container">
        <h1>Login</h1>
        {error && <div className="alert alert-error">{error}</div>}
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


