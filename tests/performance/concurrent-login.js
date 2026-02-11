import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

import { BASE_URL, EXPECT_429, asNumber, jsonParams } from './lib/env.js';
import { setupTestData } from './lib/setup.js';
import { buildSummary } from './lib/report.js';

const http429Count = new Counter('http_429_count');

const thresholds = {
  checks: EXPECT_429 ? ['rate>0.50'] : ['rate>0.99'],
  http_req_failed: EXPECT_429 ? ['rate<0.8'] : ['rate<0.01'],
  http_429_count: EXPECT_429 ? ['count>0'] : ['count==0'],
};

export const options = {
  vus: asNumber(__ENV.CONCURRENT_LOGIN_VUS, 100),
  duration: __ENV.CONCURRENT_LOGIN_DURATION || '2m',
  thresholds,
};

export function setup() {
  return setupTestData();
}

export default function (data) {
  const payload = JSON.stringify({
    email: data.adminEmail,
    password: data.adminPassword,
  });

  const response = http.post(
    `${BASE_URL}/api/v1/admin/auth/login`,
    payload,
    jsonParams({}, [200])
  );

  if (response.status === 429) {
    http429Count.add(1);
  }

  check(response, {
    'concurrent login succeeded or throttled as expected': (res) => (
      EXPECT_429 ? (res.status === 200 || res.status === 429) : res.status === 200
    ),
  });
}

export function handleSummary(data) {
  return buildSummary(data, 'tests/performance/results/concurrent-login-summary.json');
}
