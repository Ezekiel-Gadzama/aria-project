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
  get2FAQRCode: (tempSession) => api.get(`/users/2fa/qrcode?session=${tempSession}`),
  verify2FA: (tempSession, code) => api.post(`/users/2fa/verify?session=${tempSession}`, { code }),
  setup2FA: (userId) => api.post(`/users/2fa/setup?userId=${userId}`),
  getApiKeys: (userId) => api.get(`/users/api-keys?userId=${userId}`),
  createApiKey: (userId, data) => api.post(`/users/api-keys?userId=${userId}`, data),
  deleteApiKey: (userId, keyId) => api.delete(`/users/api-keys/${keyId}?userId=${userId}`),
  getCredits: (userId) => api.get(`/users/credits?userId=${userId}`),
  addCredits: (userId, amount) => api.post(`/users/credits?userId=${userId}`, { amount }),
  getSubscription: (userId) => api.get(`/users/subscription?userId=${userId}`),
  subscribe: (userId) => api.post(`/users/subscribe?userId=${userId}`),
  getAdminMode: (userId) => api.get(`/users/admin-mode?userId=${userId}`),
  updateAdminMode: (userId, adminModeEnabled) => api.put(`/users/admin-mode?userId=${userId}`, { adminModeEnabled }),
};

// Target API
export const targetApi = {
  getAll: (userId) => api.get(`/targets?userId=${userId}`),
  getCategories: () => api.get('/targets/categories'),
  getTargetCategories: (id, userId) => api.get(`/targets/${id}/categories?userId=${userId}`),
  getAnalysis: (userId, targetId, filters) => {
    const params = new URLSearchParams({ userId });
    if (targetId) params.append('targetId', targetId);
    if (filters?.platform) params.append('platform', filters.platform);
    if (filters?.platformAccountId) params.append('platformAccountId', filters.platformAccountId);
    if (filters?.category) params.append('category', filters.category);
    return api.get(`/targets/analysis?${params.toString()}`);
  },
  getById: (id, userId) => api.get(`/targets/${id}?userId=${userId}`),
  create: (targetData, userId) => api.post(`/targets?userId=${userId}`, targetData),
  update: (id, targetData, userId) => api.put(`/targets/${id}?userId=${userId}`, targetData),
  delete: (id, userId) => api.delete(`/targets/${id}?userId=${userId}`),
  checkOnlineStatus: (id, userId, subtargetUserId = null) => {
    const params = new URLSearchParams({ userId: userId || 1 });
    if (subtargetUserId) params.append('subtargetUserId', subtargetUserId);
    return api.get(`/targets/${id}/online?${params.toString()}`);
  },
  uploadProfilePicture: (id, file, userId) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/targets/${id}/profile-picture?userId=${userId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },
  deleteProfilePicture: (id, userId) => {
    return api.delete(`/targets/${id}/profile-picture?userId=${userId}`);
  },
};

// Conversation API
export const conversationApi = {
  initialize: (targetUserId, goal, userId, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    return api.post(`/conversations/initialize?${params.toString()}`, goal);
  },
  respond: (targetUserId, message, userId, referenceId, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (referenceId) {
      params.append('referenceId', referenceId);
    }
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    return api.post(
      `/conversations/respond?${params.toString()}`,
      message,
      { headers: { 'Content-Type': 'text/plain' } }
    );
  },
  getMessages: (targetUserId, userId, limit = 100, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
      limit: limit.toString(),
    });
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    return api.get(`/conversations/messages?${params.toString()}`);
  },
  getSuggestion: (targetUserId, userId, subtargetUserId = null, multiple = false) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    if (multiple) {
      params.append('multiple', 'true');
    }
    return api.get(`/conversations/suggest?${params.toString()}`);
  },
  getReferenceContext: (dialogId, messageId, before = 50, after = 50, userId = 1) => {
    const params = new URLSearchParams({
      dialogId,
      messageId,
      before,
      after,
      userId,
    });
    return api.get(`/conversations/reference?${params.toString()}`);
  },
  downloadMediaUrl: (targetUserId, userId, messageId) =>
    `${API_BASE_URL}/conversations/media/download?targetUserId=${targetUserId}&userId=${userId}&messageId=${messageId}`,
  ingest: (platform, userId) => 
    api.post(`/conversations/ingest?platform=${platform}&userId=${userId}`),
  ingestTarget: (targetUserId, userId, subtargetUserId = null) => {
    const params = new URLSearchParams({ targetUserId, userId: userId || 1 });
    if (subtargetUserId) params.append('subtargetUserId', subtargetUserId);
    return api.post(`/conversations/ingestTarget?${params.toString()}`);
  },
  end: (targetUserId, userId) =>
    api.post(`/conversations/end?targetUserId=${targetUserId}&userId=${userId}`),
  isActive: (targetUserId, userId) =>
    api.get(`/conversations/active?targetUserId=${targetUserId}&userId=${userId}`),
  editLast: (targetUserId, userId, newText, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (subtargetUserId) params.append('subtargetUserId', subtargetUserId);
    return api.post(
      `/conversations/editLast?${params.toString()}`,
      newText,
      { headers: { 'Content-Type': 'text/plain' } }
    );
  },
  edit: (targetUserId, userId, messageId, newText, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
      messageId,
    });
    if (subtargetUserId) params.append('subtargetUserId', subtargetUserId);
    return api.post(
      `/conversations/edit?${params.toString()}`,
      newText,
      { headers: { 'Content-Type': 'text/plain' } }
    );
  },
  delete: (targetUserId, userId, messageId, revoke, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
      messageId,
      revoke: revoke !== false,
    });
    if (subtargetUserId) params.append('subtargetUserId', subtargetUserId);
    return api.delete(`/conversations/message?${params.toString()}`);
  },
  sendMedia: (targetUserId, userId, file, subtargetUserId) => {
    const form = new FormData();
    form.append('file', file);
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
    });
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    return api.post(`/conversations/sendMedia?${params.toString()}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  sendMediaWithText: (targetUserId, userId, file, caption, referenceId, subtargetUserId) => {
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
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    return api.post(`/conversations/sendMedia?${params.toString()}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  replaceMedia: (targetUserId, userId, messageId, file, caption, subtargetUserId) => {
    const form = new FormData();
    form.append('file', file);
    if (caption) {
      form.append('caption', caption);
    }
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
      messageId,
    });
    if (subtargetUserId) {
      params.append('subtargetUserId', subtargetUserId);
    }
    return api.post(`/conversations/replaceMedia?${params.toString()}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  pin: (targetUserId, userId, messageId, pin, subtargetUserId = null) => {
    const params = new URLSearchParams({
      targetUserId,
      userId: userId || 1,
      messageId,
      pin: pin.toString()
    });
    if (subtargetUserId) params.append('subtargetUserId', subtargetUserId);
    return api.post(`/conversations/pin?${params.toString()}`);
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

// Business API
export const businessApi = {
  getAll: (userId) => api.get(`/businesses?userId=${userId}`),
  getById: (id, userId) => api.get(`/businesses/${id}?userId=${userId}`),
  create: (businessData, userId) => api.post(`/businesses?userId=${userId}`, businessData),
  update: (id, businessData, userId) => api.put(`/businesses/${id}?userId=${userId}`, businessData),
  delete: (id, userId) => api.delete(`/businesses/${id}?userId=${userId}`),
  getSubTargets: (businessId, userId) => api.get(`/businesses/${businessId}/subtargets?userId=${userId}`),
  getSubTarget: (businessId, id, userId) => api.get(`/businesses/${businessId}/subtargets/${id}?userId=${userId}`),
  addSubTarget: (businessId, subTargetData, userId) => api.post(`/businesses/${businessId}/subtargets?userId=${userId}`, subTargetData),
  updateSubTarget: (businessId, id, subTargetData, userId) => api.put(`/businesses/${businessId}/subtargets/${id}?userId=${userId}`, subTargetData),
  deleteSubTarget: (businessId, id, userId) => api.delete(`/businesses/${businessId}/subtargets/${id}?userId=${userId}`),
  botChat: (businessId, message, userId) => api.post(`/businesses/${businessId}/bot/chat?userId=${userId}`, { message }),
  getBotHistory: (businessId, userId) => api.get(`/businesses/${businessId}/bot/history?userId=${userId}`),
};

export default api;

