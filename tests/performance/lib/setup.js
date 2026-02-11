import http from 'k6/http';
import { fail, sleep } from 'k6';

import { BASE_URL, jsonParams, textParams, uniqueSuffix, asNumber } from './env.js';

function parseJsonOrFail(response, context) {
  try {
    return response.json();
  } catch (error) {
    fail(`${context} returned invalid JSON. status=${response.status} body=${response.body}`);
  }
}

function callWithRetry(requestFn, context, expectedStatusCodes, maxAttempts = 3) {
  let lastResponse = null;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    lastResponse = requestFn();

    if (expectedStatusCodes.includes(lastResponse.status)) {
      return lastResponse;
    }

    if (lastResponse.status === 429 || lastResponse.status >= 500) {
      sleep(attempt);
      continue;
    }

    break;
  }

  fail(`${context} failed. status=${lastResponse && lastResponse.status} body=${lastResponse && lastResponse.body}`);
}

function registerApiUser() {
  const username = uniqueSuffix('k6-user');
  const password = __ENV.API_USER_PASSWORD || 'k6-password-123';
  const payload = JSON.stringify({ username, password });

  const response = callWithRetry(
    () => http.post(`${BASE_URL}/api/v1/auth/register`, payload, jsonParams({}, [201])),
    'API user registration',
    [201]
  );

  const body = parseJsonOrFail(response, 'API user registration');
  if (!body.apiKey) {
    fail(`API user registration did not return apiKey. body=${response.body}`);
  }

  return {
    username,
    password,
    apiKey: body.apiKey,
  };
}

function registerAdminMerchant() {
  const suffix = uniqueSuffix('k6-admin');
  const email = `${suffix}@example.com`;
  const password = __ENV.ADMIN_PASSWORD || 'Admin123!';
  const merchantCode = `MC-${Math.floor(Math.random() * 1000000000)}`;
  const payload = JSON.stringify({
    merchantName: `Merchant ${suffix}`,
    email,
    password,
    merchantCode,
  });

  callWithRetry(
    () => http.post(`${BASE_URL}/api/v1/admin/auth/register`, payload, jsonParams({}, [200])),
    'Admin merchant registration',
    [200]
  );

  return {
    email,
    password,
  };
}

function loginAdmin(adminAccount) {
  const payload = JSON.stringify({
    email: adminAccount.email,
    password: adminAccount.password,
  });

  const response = callWithRetry(
    () => http.post(`${BASE_URL}/api/v1/admin/auth/login`, payload, jsonParams({}, [200])),
    'Admin login',
    [200]
  );

  const body = parseJsonOrFail(response, 'Admin login');
  if (!body.token) {
    fail(`Admin login did not return token. body=${response.body}`);
  }

  return body.token;
}

function createOrder(apiKey, userId) {
  const amount = (10 + Math.random() * 490).toFixed(2);
  const payload = JSON.stringify({
    userId,
    amount,
    currency: __ENV.ORDER_CURRENCY || 'USD',
  });

  const response = callWithRetry(
    () => http.post(
      `${BASE_URL}/api/v1/orders`,
      payload,
      jsonParams({ 'X-API-Key': apiKey }, [201])
    ),
    'Order creation',
    [201]
  );

  const body = parseJsonOrFail(response, 'Order creation');
  if (!body.orderId) {
    fail(`Order creation did not return orderId. body=${response.body}`);
  }

  return body.orderId;
}

export function setupTestData(config = {}) {
  const orderPoolSize = asNumber(config.orderPoolSize || __ENV.ORDER_POOL_SIZE, 1);
  const apiUser = registerApiUser();
  const adminAccount = registerAdminMerchant();
  const adminToken = loginAdmin(adminAccount);

  const orderIds = [];
  for (let i = 0; i < orderPoolSize; i += 1) {
    orderIds.push(createOrder(apiUser.apiKey, i + 1));
  }

  return {
    baseUrl: BASE_URL,
    apiKey: apiUser.apiKey,
    adminEmail: adminAccount.email,
    adminPassword: adminAccount.password,
    adminToken,
    orderIds,
  };
}

export function listOrders(apiKey) {
  return http.get(
    `${BASE_URL}/api/v1/orders?page=0&size=20`,
    textParams({ 'X-API-Key': apiKey }, [200])
  );
}
