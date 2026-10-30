import api from './api';

const RAW_KEYS_STORAGE_KEY = 'arl_known_raw_keys';
const DEMO_KEY = 'arl_live_demo1234567890abcdef1234';

export const saveKnownRawKey = (keyId, keyPrefix, rawApiKey) => {
  if (!rawApiKey) return;
  try {
    const map = JSON.parse(localStorage.getItem(RAW_KEYS_STORAGE_KEY) || '{}');
    if (keyId) map[`id_${keyId}`] = rawApiKey.trim();
    if (keyPrefix) map[`prefix_${keyPrefix}`] = rawApiKey.trim();
    map[rawApiKey.trim()] = rawApiKey.trim();
    localStorage.setItem(RAW_KEYS_STORAGE_KEY, JSON.stringify(map));
  } catch (e) {
    console.warn('Failed to cache raw key in localStorage:', e);
  }
};

export const getKnownRawKey = (keyId, keyPrefix) => {
  // If it's the demo key prefix, always return the demo raw key
  if (keyPrefix && keyPrefix.startsWith('arl_live_demo')) {
    return DEMO_KEY;
  }
  try {
    const map = JSON.parse(localStorage.getItem(RAW_KEYS_STORAGE_KEY) || '{}');
    if (keyId && map[`id_${keyId}`]) return map[`id_${keyId}`];
    if (keyPrefix && map[`prefix_${keyPrefix}`]) return map[`prefix_${keyPrefix}`];
    return null;
  } catch (e) {
    return null;
  }
};

export const apiKeyService = {
  async getKeys() {
    const res = await api.get('/api/keys');
    return res.data;
  },

  async createKey(name, policyId, expiresAt) {
    const res = await api.post('/api/keys', { name, policyId, expiresAt });
    if (res.data?.rawApiKey && res.data?.apiKey) {
      saveKnownRawKey(res.data.apiKey.id, res.data.apiKey.keyPrefix, res.data.rawApiKey);
    }
    return res.data;
  },

  async revokeKey(id) {
    const res = await api.delete(`/api/keys/${id}`);
    return res.data;
  }
};
