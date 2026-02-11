import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

import { BASE_URL, EXPECT_429, asNumber, jsonParams } from './lib/env.js';
import { setupTestData } from './lib/setup.js';
import { buildSummary } from './lib/report.js';

const http429Count = new Counter('http_429_count');

const p95TargetMs = asNumber(__ENV.LOGIN_P95_TARGET_MS, 200);
const thresholds = {
  checks: ['rate>0.99'],
  http_req_duration: [`p(95)<${p95TargetMs}`],
  http_req_failed: EXPECT_429 ? ['rate<0.6'] : ['rate<0.01'],
  http_429_count: EXPECT_429 ? ['count>0'] : ['count==0'],
};

export const options = {
  vus: asNumber(__ENV.LOGIN_VUS, 20),
  duration: __ENV.LOGIN_DURATION || '1m',
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
    'login succeeded or throttled as expected': (res) => (
      EXPECT_429 ? (res.status === 200 || res.status === 429) : res.status === 200
    ),
  });
}

export function handleSummary(data) {
  return buildSummary(data, 'tests/performance/results/login-summary.json');
}
