import { check, fail, sleep } from 'k6';
import http from 'k6/http';
import { Counter } from 'k6/metrics';

/**
 * Concurrency proof for Rupi transfers.
 *
 * Two users (Alice, Bob) each start with 1,000 sandbox credits and send 1.00
 * to each other at ~8 requests/second (under the 10 rps/user token bucket).
 * A second wave then retries the same idempotency key concurrently.
 *
 * Pass = no 5xx, balances still sum to 2000.00 when the test ends.
 *
 * Usage (Docker Postgres/Redis + Spring Boot on :8090):
 *   k6 run k6/concurrent-transfers.js
 *   k6 run -e BASE_URL=http://localhost:8090 k6/concurrent-transfers.js
 */

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8090').replace(/\/$/, '');
const PASSWORD = 'correct-horse-battery';

http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

const committed = new Counter('transfers_committed');
const replayed = new Counter('transfers_replayed');
const inProgress = new Counter('transfers_in_progress');
const rateLimited = new Counter('transfers_rate_limited');
const serverErrors = new Counter('transfers_server_error');

export const options = {
  thresholds: {
    transfers_server_error: ['count==0'],
    checks: ['rate==1'],
  },
  scenarios: {
    alice_sends: {
      executor: 'constant-arrival-rate',
      exec: 'aliceSends',
      rate: 8,
      timeUnit: '1s',
      duration: '12s',
      preAllocatedVUs: 10,
      maxVUs: 16,
    },
    bob_sends: {
      executor: 'constant-arrival-rate',
      exec: 'bobSends',
      rate: 8,
      timeUnit: '1s',
      duration: '12s',
      preAllocatedVUs: 10,
      maxVUs: 16,
    },
    retry_storm: {
      executor: 'shared-iterations',
      exec: 'retrySameKey',
      vus: 12,
      iterations: 36,
      startTime: '15s',
      maxDuration: '20s',
    },
  },
};

export function setup() {
  const suffix = `${Date.now().toString(36)}${Math.floor(Math.random() * 900 + 100)}`;
  const alice = registerUser(`k6a${suffix}`.slice(0, 32));
  const bob = registerUser(`k6b${suffix}`.slice(0, 32));

  if (cents(alice.balance) !== 100000 || cents(bob.balance) !== 100000) {
    fail(`Expected signup grant 1000.00, got alice=${alice.balance} bob=${bob.balance}`);
  }

  return {
    alice: { token: alice.token, accountId: alice.accountId, username: alice.username },
    bob: { token: bob.token, accountId: bob.accountId, username: bob.username },
    stormKey: uuidv4(),
  };
}

export function aliceSends(data) {
  postTransfer(data.alice.token, data.bob.accountId, '1.00', uuidv4(), true);
}

export function bobSends(data) {
  postTransfer(data.bob.token, data.alice.accountId, '1.00', uuidv4(), true);
}

export function retrySameKey(data) {
  postTransfer(data.alice.token, data.bob.accountId, '5.00', data.stormKey, false);
}

export function teardown(data) {
  const alice = getAccount(data.alice.token);
  const bob = getAccount(data.bob.token);
  const total = cents(alice.balance) + cents(bob.balance);

  console.log(
    `\nProof: ${data.alice.username}=${alice.balance}  ${data.bob.username}=${bob.balance}  sum=${(total / 100).toFixed(2)}`,
  );

  check(null, {
    'sandbox credits conserved (sum is 2000.00)': () => total === 200000,
  });

  if (total !== 200000) {
    fail(
      `Credits were not conserved: ${data.alice.username}=${alice.balance}, ${data.bob.username}=${bob.balance}`,
    );
  }
}

function registerUser(username) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username, password: PASSWORD }),
    { headers: jsonHeaders(), tags: { name: 'register' } },
  );
  if (res.status !== 201) {
    fail(`register ${username} failed: ${res.status} ${res.body}`);
  }
  return JSON.parse(res.body);
}

function postTransfer(token, toAccountId, amount, idempotencyKey, retryOn429) {
  const payload = JSON.stringify({ toAccountId, amount });
  let res = sendTransfer(token, payload, idempotencyKey);

  if (retryOn429 && res.status === 429) {
    sleep(retryAfterSeconds(res));
    res = sendTransfer(token, payload, idempotencyKey);
  }

  recordOutcome(res);
  check(res, {
    'transfer did not 5xx': (r) => r.status < 500,
    'transfer status is expected': (r) => [200, 201, 409, 429].includes(r.status),
  });
}

function sendTransfer(token, payload, idempotencyKey) {
  return http.post(`${BASE_URL}/api/v1/transactions`, payload, {
    headers: jsonHeaders({
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': idempotencyKey,
    }),
    tags: { name: 'transfer' },
  });
}

function getAccount(token) {
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const res = http.get(`${BASE_URL}/api/v1/accounts/me`, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
      },
      tags: { name: 'accounts_me' },
    });
    if (res.status === 200) {
      return JSON.parse(res.body);
    }
    if (res.status === 429) {
      sleep(retryAfterSeconds(res));
      continue;
    }
    fail(`GET /accounts/me failed: ${res.status} ${res.body}`);
  }
  fail('GET /accounts/me still rate-limited after retries');
}

function recordOutcome(res) {
  if (res.status === 201) {
    committed.add(1);
  } else if (res.status === 200) {
    replayed.add(1);
  } else if (res.status === 409) {
    inProgress.add(1);
  } else if (res.status === 429) {
    rateLimited.add(1);
  } else if (res.status >= 500) {
    serverErrors.add(1);
  } else {
    serverErrors.add(1);
  }
}

function jsonHeaders(extra) {
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  };
  if (extra) {
    Object.assign(headers, extra);
  }
  return headers;
}

function retryAfterSeconds(res) {
  const header = res.headers['Retry-After'] || res.headers['retry-after'] || '1';
  const seconds = Number(header);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : 1;
}

function cents(value) {
  const [whole, fraction = '00'] = String(value).split('.');
  return Number(whole) * 100 + Number((fraction + '00').slice(0, 2));
}

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const rand = Math.floor(Math.random() * 16);
    const value = char === 'x' ? rand : (rand & 0x3) | 0x8;
    return value.toString(16);
  });
}
