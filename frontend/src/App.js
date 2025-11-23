import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './App.css';
import UserRegistration from './components/UserRegistration';
import TargetManagement from './components/TargetManagement';
import EditTargetUser from './components/EditTargetUser';
import SubTargetUsersView from './components/SubTargetUsersView';
import ConversationView from './components/ConversationView';
import PlatformRegistration from './components/PlatformRegistration';
import Navigation from './components/Navigation';
import Login from './components/Login';
import ApiKeyManagement from './components/ApiKeyManagement';
import AnalysisDashboard from './components/AnalysisDashboard';
import Settings from './components/Settings';

function App() {
  // Load user from localStorage on mount (session persistence)
  const [currentUser, setCurrentUser] = useState(() => {
    const savedUser = localStorage.getItem('aria_user');
    return savedUser ? JSON.parse(savedUser) : null;
  });
  const [isAuthenticated, setIsAuthenticated] = useState(() => {
    return !!localStorage.getItem('aria_user');
  });

  const handleLogin = (user) => {
    setCurrentUser(user);
    setIsAuthenticated(true);
    // Persist to localStorage
    localStorage.setItem('aria_user', JSON.stringify(user));
    localStorage.setItem('aria_session', Date.now().toString());
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setIsAuthenticated(false);
    // Clear localStorage
    localStorage.removeItem('aria_user');
    localStorage.removeItem('aria_session');
  };

  return (
    <Router>
      <div className="App">
        <Navigation 
          isAuthenticated={isAuthenticated} 
          onLogout={handleLogout}
        />
        <main className="main-content">
          <Routes>
            <Route 
              path="/" 
              element={
                isAuthenticated ? (
                  <Navigate to="/targets" replace />
                ) : (
                  <UserRegistration onLogin={handleLogin} />
                )
              } 
            />
            <Route
              path="/login"
              element={
                isAuthenticated ? (
                  <Navigate to="/targets" replace />
                ) : (
                  <Login onLogin={handleLogin} />
                )
              }
            />
            <Route 
              path="/targets" 
              element={
                isAuthenticated ? (
                  <TargetManagement userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/targets/:targetId/edit" 
              element={
                isAuthenticated ? (
                  <EditTargetUser userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/targets/:targetId/subtargets" 
              element={
                isAuthenticated ? (
                  <SubTargetUsersView userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/platforms" 
              element={
                isAuthenticated ? (
                  <PlatformRegistration userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/conversations/:targetId" 
              element={
                isAuthenticated ? (
                  <ConversationView userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/analysis" 
              element={
                isAuthenticated ? (
                  <AnalysisDashboard userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/analysis/:targetId" 
              element={
                isAuthenticated ? (
                  <AnalysisDashboard userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/payments" 
              element={
                isAuthenticated ? (
                  <ApiKeyManagement userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="/api-keys" 
              element={
                <Navigate to="/payments" replace />
              } 
            />
            <Route 
              path="/settings" 
              element={
                isAuthenticated ? (
                  <Settings userId={currentUser?.id} />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
            <Route 
              path="*" 
              element={
                isAuthenticated ? (
                  <Navigate to="/targets" replace />
                ) : (
                  <Navigate to="/" replace />
                )
              } 
            />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;

