import api from './api';

export const policyService = {
  async getPolicies() {
    const res = await api.get('/api/policies');
    return res.data;
  },

  async getPolicy(id) {
    const res = await api.get(`/api/policies/${id}`);
    return res.data;
  },

  async createPolicy(policyData) {
    const res = await api.post('/api/policies', policyData);
    return res.data;
  },

  async updatePolicy(id, policyData) {
    const res = await api.put(`/api/policies/${id}`, policyData);
    return res.data;
  },

  async deletePolicy(id) {
    const res = await api.delete(`/api/policies/${id}`);
    return res.data;
  },

  async toggleStatus(id, active) {
    const res = await api.patch(`/api/policies/${id}/status?active=${active}`);
    return res.data;
  }
};
