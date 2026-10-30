import api from './api';

export const analyticsService = {
  async getOverview() {
    const res = await api.get('/api/analytics/overview');
    return res.data;
  },

  async getRecentRequests(page = 0, size = 20) {
    const res = await api.get(`/api/analytics/requests?page=${page}&size=${size}`);
    return res.data;
  },

  async getBlockedRequests(page = 0, size = 20, filters = {}) {
    const params = new URLSearchParams({ page, size });
    if (filters.endpoint) params.append('endpoint', filters.endpoint);
    if (filters.algorithm && filters.algorithm !== 'ALL') params.append('algorithm', filters.algorithm);
    if (filters.search) params.append('search', filters.search);
    if (filters.fromDate) params.append('fromDate', filters.fromDate);
    if (filters.toDate) params.append('toDate', filters.toDate);
    const res = await api.get(`/api/analytics/blocked?${params.toString()}`);
    return res.data;
  },

  async getTimeseries(minutes = 60) {
    const res = await api.get(`/api/analytics/timeseries?minutes=${minutes}`);
    return res.data;
  }
};
