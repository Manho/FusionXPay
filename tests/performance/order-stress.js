import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

import { BASE_URL, EXPECT_429, asNumber, jsonParams } from './lib/env.js';
import { setupTestData } from './lib/setup.js';
import { buildSummary } from './lib/report.js';

const http429Count = new Counter('http_429_count');

const targetTps = asNumber(__ENV.ORDER_STRESS_TPS, 50);
const duration = __ENV.ORDER_STRESS_DURATION || '5m';

const thresholds = {
  checks: EXPECT_429 ? ['rate>0.50'] : ['rate>0.99'],
  http_req_failed: EXPECT_429 ? ['rate<0.8'] : ['rate<0.01'],
  http_429_count: EXPECT_429 ? ['count>0'] : ['count==0'],
};

export const options = {
  scenarios: {
    order_creation_stress: {
      executor: 'constant-arrival-rate',
      rate: targetTps,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: asNumber(__ENV.ORDER_STRESS_PRE_ALLOCATED_VUS, 100),
      maxVUs: asNumber(__ENV.ORDER_STRESS_MAX_VUS, 300),
    },
  },
  thresholds,
};

export function setup() {
  return setupTestData();
}

export default function (data) {
  const payload = JSON.stringify({
    userId: ((__ITER % 100000) + 1),
    amount: (10 + Math.random() * 490).toFixed(2),
    currency: 'USD',
  });

  const response = http.post(
    `${BASE_URL}/api/v1/orders`,
    payload,
    jsonParams({ 'X-API-Key': data.apiKey }, [201])
  );

  if (response.status === 429) {
    http429Count.add(1);
  }

  check(response, {
    'order creation succeeded or throttled as expected': (res) => (
      EXPECT_429 ? (res.status === 201 || res.status === 429) : res.status === 201
    ),
  });
}

export function handleSummary(data) {
  return buildSummary(data, 'tests/performance/results/order-stress-summary.json');
}
