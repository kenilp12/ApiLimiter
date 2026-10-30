import axios from 'axios';

const gatewayClient = axios.create({
  baseURL: import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8081',
  timeout: 10000,
});

export const gatewayApi = {
  async sendRequest({ endpoint, method = 'GET', apiKey, payload = null }) {
    const startTime = performance.now();
    const headers = {};
    if (apiKey) {
      headers['X-API-Key'] = apiKey;
    }

    try {
      const response = await gatewayClient({
        url: endpoint,
        method,
        headers,
        data: payload,
        validateStatus: () => true, // Don't throw for 429 or 4xx, so playground captures it accurately
      });

      const endTime = performance.now();
      const latencyMs = Math.round(endTime - startTime);

      return {
        status: response.status,
        statusText: response.statusText,
        data: response.data,
        headers: response.headers,
        latencyMs,
        isBlocked: response.status === 429,
        rateLimit: {
          limit: response.headers['x-ratelimit-limit'],
          remaining: response.headers['x-ratelimit-remaining'],
          reset: response.headers['x-ratelimit-reset'],
          algorithm: response.headers['x-ratelimit-algorithm'],
          retryAfter: response.headers['retry-after'],
          correlationId: response.headers['x-correlation-id'],
        }
      };
    } catch (error) {
      const endTime = performance.now();
      return {
        status: error.response?.status || 500,
        statusText: error.message || 'Request Failed',
        data: error.response?.data || { error: error.message },
        headers: error.response?.headers || {},
        latencyMs: Math.round(endTime - startTime),
        isBlocked: error.response?.status === 429,
        rateLimit: {}
      };
    }
  }
};
