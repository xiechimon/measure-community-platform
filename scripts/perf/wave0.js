import http from 'k6/http';
import { check, fail } from 'k6';

export const options = {
  vus: 20,
  duration: '2m',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const base = __ENV.BASE_URL;
  const response = http.post(`${base}/api/v1/auth/login`, JSON.stringify({
    account: __ENV.ADMIN_ACCOUNT,
    password: __ENV.ADMIN_PASSWORD,
  }), { headers: { 'Content-Type': 'application/json' } });
  const ok = check(response, {
    'login http 200': r => r.status === 200,
    'login body code 200': r => r.json('code') === 200,
    'login token exists': r => Boolean(r.json('data.token')),
  });
  if (!ok) fail(`login failed: ${response.status} ${response.body}`);
  return { base, token: response.json('data.token') };
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` } };
  const user = http.get(`${data.base}/api/v1/auth/getUserName`, params);
  const population = http.get(`${data.base}/api/v1/population/persons?page=1&size=10`, params);
  check(user, { 'user query succeeds': r => r.status === 200 && r.json('code') === 200 });
  check(population, {
    'population query succeeds': r => r.status === 200 && r.json('code') === 200,
  });
}
