import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// User API
export const userApi = {
  register: (userData) => api.post('/users/register', userData),
  login: (data) => api.post('/users/login', data),
  deleteAll: () => api.delete('/users'),
  getCurrentUser: (userId) => api.get(`/users/me?userId=${userId}`),
};

// Target API
export const targetApi = {
  getAll: (userId) => api.get(`/targets?userId=${userId}`),
  getById: (id, userId) => api.get(`/targets/${id}?userId=${userId}`),
  create: (targetData, userId) => api.post(`/targets?userId=${userId}`, targetData),
  update: (id, targetData, userId) => api.put(`/targets/${id}?userId=${userId}`, targetData),
  delete: (id, userId) => api.delete(`/targets/${id}?userId=${userId}`),
  checkOnlineStatus: (id, userId) => api.get(`/targets/${id}/online?userId=${userId}`),
};

// Conversation API
export const conversationApi = {
  initialize: (targetUserId, goal, userId) => 
    api.post(`/conversations/initialize?targetUserId=${targetUserId}&userId=${userId}`, goal),
  respond: (targetUserId, message, userId, referenceId) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (referenceId) {
      params.append('referenceId', referenceId);
    }
    return api.post(
      `/conversations/respond?${params.toString()}`,
      message,
      { headers: { 'Content-Type': 'text/plain' } }
    );
  },
  getMessages: (targetUserId, userId, limit = 100) =>
    api.get(`/conversations/messages?targetUserId=${targetUserId}&userId=${userId}&limit=${limit}`),
  downloadMediaUrl: (targetUserId, userId, messageId) =>
    `${API_BASE_URL}/conversations/media/download?targetUserId=${targetUserId}&userId=${userId}&messageId=${messageId}`,
  ingest: (platform, userId) => 
    api.post(`/conversations/ingest?platform=${platform}&userId=${userId}`),
  ingestTarget: (targetUserId, userId) =>
    api.post(`/conversations/ingestTarget?targetUserId=${targetUserId}&userId=${userId}`),
  end: (targetUserId, userId) =>
    api.post(`/conversations/end?targetUserId=${targetUserId}&userId=${userId}`),
  isActive: (targetUserId, userId) =>
    api.get(`/conversations/active?targetUserId=${targetUserId}&userId=${userId}`),
  editLast: (targetUserId, userId, newText) =>
    api.post(
      `/conversations/editLast?targetUserId=${targetUserId}&userId=${userId}`,
      newText,
      { headers: { 'Content-Type': 'text/plain' } }
    ),
  edit: (targetUserId, userId, messageId, newText) =>
    api.post(
      `/conversations/edit?targetUserId=${targetUserId}&userId=${userId}&messageId=${messageId}`,
      newText,
      { headers: { 'Content-Type': 'text/plain' } }
    ),
  delete: (targetUserId, userId, messageId, revoke) =>
    api.delete(`/conversations/message?targetUserId=${targetUserId}&userId=${userId}&messageId=${messageId}&revoke=${revoke !== false}`),
  sendMedia: (targetUserId, userId, file) => {
    const form = new FormData();
    form.append('file', file);
    return api.post(`/conversations/sendMedia?targetUserId=${targetUserId}&userId=${userId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  sendMediaWithText: (targetUserId, userId, file, caption, referenceId) => {
    const form = new FormData();
    form.append('file', file);
    if (caption) {
      form.append('caption', caption);
    }
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (referenceId) {
      params.append('referenceId', referenceId);
    }
    return api.post(`/conversations/sendMedia?${params.toString()}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  replaceMedia: (targetUserId, userId, messageId, file, caption) => {
    const form = new FormData();
    form.append('file', file);
    if (caption) {
      form.append('caption', caption);
    }
    return api.post(`/conversations/replaceMedia?targetUserId=${targetUserId}&userId=${userId}&messageId=${messageId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

// Platform API
export const platformApi = {
  getAll: () => api.get('/platforms'),
  getAccounts: (userId) => api.get(`/platforms/accounts?userId=${userId}`),
  register: (platform, credentials, userId) => 
    api.post(`/platforms/register?platform=${platform}&userId=${userId}`, credentials),
  sendTelegramOtp: ({ apiId, apiHash, phoneNumber, username }) =>
    api.post(`/platforms/telegram/sendOtp?apiId=${encodeURIComponent(apiId)}&apiHash=${encodeURIComponent(apiHash)}&phoneNumber=${encodeURIComponent(phoneNumber)}&username=${encodeURIComponent(username || '')}`),
  verifyTelegramOtp: ({ apiId, apiHash, phoneNumber, username, code, password }) =>
    api.post(`/platforms/telegram/verifyOtp?apiId=${encodeURIComponent(apiId)}&apiHash=${encodeURIComponent(apiHash)}&phoneNumber=${encodeURIComponent(phoneNumber)}&username=${encodeURIComponent(username || '')}&code=${encodeURIComponent(code)}${password ? `&password=${encodeURIComponent(password)}` : ''}`),
  deleteAccount: (id, userId) =>
    api.delete(`/platforms/accounts/${id}?userId=${userId}`),
};

export default api;

