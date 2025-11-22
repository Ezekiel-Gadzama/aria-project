import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { targetApi } from '../services/api';
import './AnalysisDashboard.css';

function AnalysisDashboard({ userId = 1 }) {
  const { targetId } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedPlatform, setSelectedPlatform] = useState('all');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [analysisData, setAnalysisData] = useState(null);
  const [targets, setTargets] = useState([]);
  const [platforms, setPlatforms] = useState([]);

  useEffect(() => {
    loadAnalysisData();
    loadTargets();
  }, [selectedPlatform, selectedCategory, targetId]);

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
      // TODO: Implement API endpoint
      // const response = await targetApi.getAnalysis(userId, targetId, {
      //   platform: selectedPlatform,
      //   category: selectedCategory
      // });
      
      // Mock data
      const mockData = {
        sentiment: {
          average: 0.65,
          trend: 'positive',
          withTargets: 0.72,
          withoutTargets: 0.58,
          improvement: 0.14
        },
        engagement: {
          score: 0.78,
          responsiveness: 0.82,
          messageLength: 45,
          initiationFrequency: 0.65
        },
        disinterest: {
          detected: false,
          signs: []
        },
        conversationFlow: {
          avgResponseTime: 120,
          turnTaking: 0.75
        },
        goalProgression: {
          score: 0.68,
          status: 'in_progress'
        },
        topTargets: [
          { id: 1, name: 'Target 1', score: 0.85 },
          { id: 2, name: 'Target 2', score: 0.82 },
          { id: 3, name: 'Target 3', score: 0.79 },
          { id: 4, name: 'Target 4', score: 0.76 },
          { id: 5, name: 'Target 5', score: 0.73 }
        ]
      };
      setAnalysisData(mockData);
    } catch (err) {
      setError(err.message || 'Failed to load analysis data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="spinner"></div>;
  }

  const isTargetSpecific = !!targetId;
  const target = targets.find(t => t.id === parseInt(targetId));

  return (
    <div className="analysis-dashboard">
      <div className="container">
        <div className="dashboard-header">
          <h1>
            {isTargetSpecific ? `Analysis: ${target?.name || 'Target'}` : 'General Analysis'}
          </h1>
          <div className="header-actions">
            {isTargetSpecific ? (
              <button 
                className="btn btn-secondary"
                onClick={() => navigate('/analysis')}
              >
                General Analysis
              </button>
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
              <option value="TELEGRAM">Telegram</option>
              <option value="WHATSAPP">WhatsApp</option>
              <option value="INSTAGRAM">Instagram</option>
            </select>
          </div>
          <div className="filter-group">
            <label>Category:</label>
            <select 
              value={selectedCategory} 
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              <option value="all">All Categories</option>
              <option value="DATING">Dating</option>
              <option value="WORK">Work</option>
              <option value="FAMILY">Family</option>
            </select>
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

            {/* Top 5 Leaderboard (only for general analysis) */}
            {!isTargetSpecific && (
              <div className="metric-card">
                <h2>Top 5 Target Users</h2>
                <div className="leaderboard">
                  {analysisData.topTargets.map((target, idx) => (
                    <div key={target.id} className="leaderboard-item">
                      <span className="rank">#{idx + 1}</span>
                      <span className="name">{target.name}</span>
                      <span className="score">{target.score.toFixed(2)}</span>
                      <button 
                        className="btn btn-sm btn-secondary"
                        onClick={() => navigate(`/analysis/${target.id}`)}
                      >
                        View Details
                      </button>
                    </div>
                  ))}
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

