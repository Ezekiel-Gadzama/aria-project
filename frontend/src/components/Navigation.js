import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import './Navigation.css';

function Navigation({ isAuthenticated, onLogout }) {
  const location = useLocation();

  if (!isAuthenticated) {
    return null;
  }

  return (
    <nav className="nav">
      <Link to="/" className="nav-brand">
        ARIA
      </Link>
      <div className="nav-links">
        <Link 
          to="/targets" 
          className={`nav-link ${location.pathname.startsWith('/targets') ? 'active' : ''}`}
        >
          Targets
        </Link>
        <Link 
          to="/platforms" 
          className={`nav-link ${location.pathname.startsWith('/platforms') ? 'active' : ''}`}
        >
          Platforms
        </Link>
        <button onClick={onLogout} className="btn btn-secondary">
          Logout
        </button>
      </div>
    </nav>
  );
}

export default Navigation;

