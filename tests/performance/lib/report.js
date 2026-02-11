function metricValue(data, metricName, statName, fallback = 'n/a') {
  if (!data.metrics[metricName] || !data.metrics[metricName].values) {
    return fallback;
  }
  const value = data.metrics[metricName].values[statName];
  return value === undefined ? fallback : value;
}

export function buildSummary(data, defaultFile) {
  const outputFile = __ENV.SUMMARY_FILE || defaultFile;
  const p95 = metricValue(data, 'http_req_duration', 'p(95)');
  const reqFailed = metricValue(data, 'http_req_failed', 'rate');
  const checks = metricValue(data, 'checks', 'rate');
  const throttled = metricValue(data, 'http_429_count', 'count', 0);

  const summary = [
    '',
    `summary_file: ${outputFile}`,
    `http_req_duration_p95_ms: ${p95}`,
    `http_req_failed_rate: ${reqFailed}`,
    `checks_rate: ${checks}`,
    `http_429_count: ${throttled}`,
    '',
  ].join('\n');

  return {
    stdout: summary,
    [outputFile]: JSON.stringify(data, null, 2),
  };
}
