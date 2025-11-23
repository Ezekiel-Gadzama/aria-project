import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { targetApi, platformApi } from '../services/api';
import './AnalysisDashboard.css';

function AnalysisDashboard({ userId = 1 }) {
  const { targetId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedPlatform, setSelectedPlatform] = useState('all');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [analysisData, setAnalysisData] = useState(null);
  const [targets, setTargets] = useState([]);
  const [registeredPlatformAccounts, setRegisteredPlatformAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [categorySearch, setCategorySearch] = useState('');
  const [showCategoryDropdown, setShowCategoryDropdown] = useState(false);
  const categoryDropdownRef = useRef(null);

  useEffect(() => {
    loadRegisteredPlatformAccounts();
    loadTargets();
    loadCategories();
  }, []);

  // Check for platform account ID in URL params or location state (for auto-filtering)
  useEffect(() => {
    const urlParams = new URLSearchParams(location.search);
    const platformAccountId = urlParams.get('platformAccountId') || location.state?.platformAccountId;
    
    if (platformAccountId && registeredPlatformAccounts.length > 0) {
      // Find the account and set the platform filter
      const account = registeredPlatformAccounts.find(acc => acc.id === parseInt(platformAccountId));
      if (account) {
        // Use format: "account_<id>" to distinguish from platform name
        setSelectedPlatform(`account_${account.id}`);
      }
    }
  }, [location.search, location.state, registeredPlatformAccounts]);

  useEffect(() => {
    loadAnalysisData();
  }, [selectedPlatform, selectedCategory, targetId]);

  // Close category dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (categoryDropdownRef.current && !categoryDropdownRef.current.contains(event.target)) {
        setShowCategoryDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const loadRegisteredPlatformAccounts = async () => {
    try {
      const response = await platformApi.getAccounts(userId);
      if (response.data?.success) {
        const accounts = response.data.data || [];
        setRegisteredPlatformAccounts(accounts);
      }
    } catch (err) {
      console.error('Failed to load registered platform accounts:', err);
    }
  };

  const loadCategories = async () => {
    try {
      const response = await targetApi.getCategories();
      if (response.data?.success) {
        setCategories(response.data.data || []);
      } else {
        // Fallback: use common categories if API fails
        setCategories(['dating', 'work', 'family', 'business', 'friendship']);
      }
    } catch (err) {
      console.error('Failed to load categories:', err);
      // Fallback: use common categories
      setCategories(['dating', 'work', 'family', 'business', 'friendship']);
    }
  };

  const loadTargets = async () => {
    try {
      const response = await targetApi.getAll(userId);
      if (response.data.success) {
        setTargets(response.data.data || []);
      }
    } catch (err) {
      console.error('Failed to load targets:', err);
    }
  };

  const loadAnalysisData = async () => {
    try {
      setLoading(true);
      setError(null);
      
      // Extract platform account ID if format is "account_<id>", otherwise use platform name
      let platformFilter = null;
      let platformAccountIdFilter = null;
      
      if (selectedPlatform !== 'all') {
        if (selectedPlatform.startsWith('account_')) {
          // Extract account ID from "account_<id>" format
          platformAccountIdFilter = parseInt(selectedPlatform.replace('account_', ''));
        } else {
          // Use platform name (legacy format)
          platformFilter = selectedPlatform;
        }
      }
      
      const response = await targetApi.getAnalysis(userId, targetId ? parseInt(targetId) : null, {
        platform: platformFilter,
        platformAccountId: platformAccountIdFilter,
        category: selectedCategory !== 'all' ? selectedCategory : null
      });
      
      if (response.data?.success) {
        setAnalysisData(response.data.data);
      } else {
        setError(response.data?.error || 'Failed to load analysis data');
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message || 'Failed to load analysis data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="spinner"></div>;
  }

  const isTargetSpecific = !!targetId;
  const target = targets.find(t => t.id === parseInt(targetId));
  const targetName = target?.name || target?.alias || 'Target';

  return (
    <div className="analysis-dashboard">
      <div className="container">
        <div className="dashboard-header">
          <h1>
            {isTargetSpecific ? `Analysis: ${targetName}` : 'General Analysis'}
          </h1>
          <div className="header-actions">
            {isTargetSpecific ? (
              <>
                <button 
                  className="btn btn-secondary"
                  onClick={() => {
                    // Preserve subtargetUserId from URL params when navigating back
                    const urlParams = new URLSearchParams(location.search);
                    const subtargetUserId = urlParams.get('subtargetUserId');
                    if (subtargetUserId) {
                      navigate(`/conversations/${targetId}?subtargetUserId=${subtargetUserId}`);
                    } else {
                      navigate(`/conversations/${targetId}`);
                    }
                  }}
                  style={{ marginRight: '0.5rem' }}
                >
                  ← Back to Conversation
                </button>
                <button 
                  className="btn btn-secondary"
                  onClick={() => navigate('/analysis')}
                >
                  General Analysis
                </button>
              </>
            ) : (
              <button 
                className="btn btn-secondary"
                onClick={() => navigate('/targets')}
              >
                Back to Targets
              </button>
            )}
          </div>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

        {/* Filters */}
        <div className="filters-section">
          <div className="filter-group">
            <label>Platform:</label>
            <select 
              value={selectedPlatform} 
              onChange={(e) => setSelectedPlatform(e.target.value)}
            >
              <option value="all">All Platforms</option>
              {registeredPlatformAccounts.map((account) => (
                <option key={account.id} value={`account_${account.id}`}>
                  {account.platform} {account.username ? `(@${account.username})` : account.accountName ? `(${account.accountName})` : ''}
                </option>
              ))}
            </select>
          </div>
          <div className="filter-group" style={{ position: 'relative' }} ref={categoryDropdownRef}>
            <label>Category:</label>
            <div style={{ position: 'relative' }}>
              <input
                type="text"
                value={selectedCategory === 'all' ? categorySearch : (categorySearch || selectedCategory)}
                onChange={(e) => {
                  const val = e.target.value;
                  setCategorySearch(val);
                  setShowCategoryDropdown(true);
                  if (val === '') {
                    setSelectedCategory('all');
                  }
                }}
                onFocus={() => setShowCategoryDropdown(true)}
                placeholder={selectedCategory === 'all' ? "Search categories..." : selectedCategory.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                style={{ width: '100%', padding: '0.5rem' }}
              />
              {showCategoryDropdown && (
                <div style={{
                  position: 'absolute',
                  top: '100%',
                  left: 0,
                  right: 0,
                  maxHeight: '400px',
                  overflowY: 'auto',
                  backgroundColor: 'white',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  zIndex: 1000,
                  boxShadow: '0 2px 8px rgba(0,0,0,0.1)'
                }}>
                  <div
                    style={{ padding: '0.5rem', cursor: 'pointer', backgroundColor: selectedCategory === 'all' ? '#f0f0f0' : 'white' }}
                    onClick={() => {
                      setSelectedCategory('all');
                      setCategorySearch('');
                      setShowCategoryDropdown(false);
                    }}
                  >
                    All Categories
                  </div>
                  {categories
                    .filter(cat => {
                      const searchLower = categorySearch.toLowerCase();
                      return cat.toLowerCase().includes(searchLower) || 
                             cat.toLowerCase().replace(/_/g, ' ').includes(searchLower);
                    })
                    .map((category) => (
                      <div
                        key={category}
                        style={{
                          padding: '0.5rem',
                          cursor: 'pointer',
                          backgroundColor: selectedCategory === category ? '#e3f2fd' : 'white'
                        }}
                        onClick={() => {
                          setSelectedCategory(category);
                          setCategorySearch('');
                          setShowCategoryDropdown(false);
                        }}
                        onMouseEnter={(e) => e.target.style.backgroundColor = '#f5f5f5'}
                        onMouseLeave={(e) => e.target.style.backgroundColor = selectedCategory === category ? '#e3f2fd' : 'white'}
                      >
                        {category.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                      </div>
                    ))}
                </div>
              )}
            </div>
          </div>
        </div>

        {analysisData && (
          <>
            {/* Sentiment Analysis */}
            <div className="metric-card">
              <h2>Sentiment Analysis</h2>
              <div className="metric-grid">
                <div className="metric-item">
                  <div className="metric-label">Average Sentiment</div>
                  <div className="metric-value">{analysisData.sentiment.average.toFixed(2)}</div>
                  <div className="metric-trend positive">↑ {analysisData.sentiment.trend}</div>
                </div>
                <div className="metric-item">
                  <div className="metric-label">With Targets</div>
                  <div className="metric-value">{analysisData.sentiment.withTargets.toFixed(2)}</div>
                </div>
                <div className="metric-item">
                  <div className="metric-label">Without Targets</div>
                  <div className="metric-value">{analysisData.sentiment.withoutTargets.toFixed(2)}</div>
                </div>
                <div className="metric-item highlight">
                  <div className="metric-label">Improvement Delta</div>
                  <div className="metric-value">+{analysisData.sentiment.improvement.toFixed(2)}</div>
                  <div className="metric-description">Better performance with targets!</div>
                </div>
              </div>
            </div>

            {/* Engagement Score */}
            <div className="metric-card">
              <h2>Engagement Score</h2>
              <div className="metric-grid">
                <div className="metric-item">
                  <div className="metric-label">Overall Score</div>
                  <div className="metric-value">{analysisData.engagement.score.toFixed(2)}</div>
                </div>
                <div className="metric-item">
                  <div className="metric-label">Responsiveness</div>
                  <div className="metric-value">{analysisData.engagement.responsiveness.toFixed(2)}</div>
                </div>
                <div className="metric-item">
                  <div className="metric-label">Avg Message Length</div>
                  <div className="metric-value">{analysisData.engagement.messageLength} chars</div>
                </div>
                <div className="metric-item">
                  <div className="metric-label">Initiation Frequency</div>
                  <div className="metric-value">{analysisData.engagement.initiationFrequency.toFixed(2)}</div>
                </div>
              </div>
            </div>

            {/* Disinterest Detection */}
            <div className="metric-card">
              <h2>Disinterest Detection</h2>
              <div className={`disinterest-status ${analysisData.disinterest.detected ? 'detected' : 'none'}`}>
                {analysisData.disinterest.detected ? (
                  <>
                    <span className="status-icon">⚠️</span>
                    <span>Signs of disinterest detected</span>
                    <ul>
                      {analysisData.disinterest.signs.map((sign, idx) => (
                        <li key={idx}>{sign}</li>
                      ))}
                    </ul>
                  </>
                ) : (
                  <>
                    <span className="status-icon">✓</span>
                    <span>No signs of disinterest detected</span>
                  </>
                )}
              </div>
            </div>

            {/* Conversation Flow */}
            <div className="metric-card">
              <h2>Conversation Flow</h2>
              <div className="metric-grid">
                <div className="metric-item">
                  <div className="metric-label">Avg Response Time</div>
                  <div className="metric-value">{analysisData.conversationFlow.avgResponseTime}s</div>
                </div>
                <div className="metric-item">
                  <div className="metric-label">Turn-Taking Score</div>
                  <div className="metric-value">{analysisData.conversationFlow.turnTaking.toFixed(2)}</div>
                </div>
              </div>
            </div>

            {/* Goal Progression */}
            <div className="metric-card">
              <h2>Goal Progression</h2>
              <div className="goal-progress">
                <div className="progress-bar">
                  <div 
                    className="progress-fill" 
                    style={{ width: `${analysisData.goalProgression.score * 100}%` }}
                  ></div>
                </div>
                <div className="progress-text">
                  {Math.round(analysisData.goalProgression.score * 100)}% - {analysisData.goalProgression.status}
                </div>
              </div>
            </div>

            {/* Top Target Users Leaderboard (only for general analysis) */}
            {!isTargetSpecific && analysisData.topTargets && analysisData.topTargets.length > 0 && (
              <div className="metric-card">
                <h2>
                  {analysisData.topTargets.length >= 5 
                    ? 'Top 5 Target Users' 
                    : 'Top Target Users'}
                </h2>
                <div className="leaderboard">
                  {analysisData.topTargets.map((targetItem, idx) => {
                    // Find the actual target from the targets list to get the name
                    const actualTarget = targets.find(t => t.id === targetItem.id);
                    const displayName = actualTarget?.name || actualTarget?.alias || targetItem.name || `Target ${idx + 1}`;
                    
                    return (
                      <div key={targetItem.id} className="leaderboard-item">
                        <span className="rank">#{idx + 1}</span>
                        <span className="name">{displayName}</span>
                        <span className="score">{targetItem.score.toFixed(2)}</span>
                        <button 
                          className="btn btn-sm btn-secondary"
                          onClick={() => navigate(`/analysis/${targetItem.id}`)}
                        >
                          View Details
                        </button>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default AnalysisDashboard;

