import axios from 'axios';

const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

export function fetchProducts() {
  return client.get('/products').then((res) => res.data);
}

export function fetchProduct(id) {
  return client.get(`/products/${id}`).then((res) => res.data);
}

export function purchase(strategy, payload) {
  return client.post(`/flash-sale/purchase/${strategy}`, payload).then((res) => res.data);
}

export function purchaseWithRedisLua(payload) {
  return purchase('redis-lua', payload);
}

export function resetInventory() {
  return client.post('/products/reset').then((res) => res.data);
}

export function extractErrorMessage(error) {
  if (error.response && error.response.data && error.response.data.message) {
    return error.response.data.message;
  }
  if (error.response) {
    return `Request failed with status ${error.response.status}.`;
  }
  return 'Could not reach the server. Is the backend running?';
}

export default client;