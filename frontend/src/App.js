import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './App.css';
import UserRegistration from './components/UserRegistration';
import TargetManagement from './components/TargetManagement';
import ConversationView from './components/ConversationView';
import PlatformRegistration from './components/PlatformRegistration';
import Navigation from './components/Navigation';
import Login from './components/Login';

function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const handleLogin = (user) => {
    setCurrentUser(user);
    setIsAuthenticated(true);
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setIsAuthenticated(false);
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
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;

