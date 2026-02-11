import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

import { BASE_URL, EXPECT_429, asNumber, textParams } from './lib/env.js';
import { setupTestData } from './lib/setup.js';
import { buildSummary } from './lib/report.js';

const http429Count = new Counter('http_429_count');

const p95TargetMs = asNumber(__ENV.ORDER_LIST_P95_TARGET_MS, 300);
const thresholds = {
  checks: ['rate>0.99'],
  http_req_duration: [`p(95)<${p95TargetMs}`],
  http_req_failed: EXPECT_429 ? ['rate<0.6'] : ['rate<0.01'],
  http_429_count: EXPECT_429 ? ['count>0'] : ['count==0'],
};

export const options = {
  vus: asNumber(__ENV.ORDER_LIST_VUS, 30),
  duration: __ENV.ORDER_LIST_DURATION || '1m',
  thresholds,
};

export function setup() {
  return setupTestData();
}

export default function (data) {
  const response = http.get(
    `${BASE_URL}/api/v1/orders?page=0&size=20`,
    textParams({ 'X-API-Key': data.apiKey }, [200])
  );

  if (response.status === 429) {
    http429Count.add(1);
  }

  check(response, {
    'order list succeeded or throttled as expected': (res) => (
      EXPECT_429 ? (res.status === 200 || res.status === 429) : res.status === 200
    ),
  });
}

export function handleSummary(data) {
  return buildSummary(data, 'tests/performance/results/order-list-summary.json');
}
