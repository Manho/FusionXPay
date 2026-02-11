import http from 'k6/http';

const defaultGatewayPort = __ENV.API_GATEWAY_PORT || '8080';

export const BASE_URL = (__ENV.BASE_URL || `http://localhost:${defaultGatewayPort}`).replace(/\/$/, '');
export const HTTP_TIMEOUT = __ENV.HTTP_TIMEOUT || '30s';
export const EXPECT_429 = __ENV.EXPECT_429 === 'true';

export function asNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function jsonParams(extraHeaders = {}, expectedStatusCodes = [200]) {
  const statusCodes = EXPECT_429
    ? [...expectedStatusCodes, 429]
    : expectedStatusCodes;

  return {
    headers: {
      'Content-Type': 'application/json',
      ...extraHeaders,
    },
    timeout: HTTP_TIMEOUT,
    responseCallback: http.expectedStatuses(...statusCodes),
  };
}

export function textParams(extraHeaders = {}, expectedStatusCodes = [200]) {
  const statusCodes = EXPECT_429
    ? [...expectedStatusCodes, 429]
    : expectedStatusCodes;

  return {
    headers: {
      ...extraHeaders,
    },
    timeout: HTTP_TIMEOUT,
    responseCallback: http.expectedStatuses(...statusCodes),
  };
}

export function uniqueSuffix(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
}
