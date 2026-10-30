import api from './api';

export const authService = {
  async login(username, password) {
    const res = await api.post('/api/auth/login', { username, password });
    return res.data;
  },

  async register(username, email, password) {
    const res = await api.post('/api/auth/register', { username, email, password });
    return res.data;
  },

  async getMe() {
    const res = await api.get('/api/auth/me');
    return res.data;
  }
};
