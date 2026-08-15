export type ApiError = {
  code: string
  message: string
  requestId?: string
  fieldErrors?: { field: string; message: string }[]
}

export type StatusResponse = {
  service: string
  status: string
  demoMode: boolean
}

export type AuthResponse = {
  token: string
  userId: string
  username: string
  accountId: string
  balance: string
}

export type AccountResponse = {
  accountId: string
  userId: string
  username: string
  balance: string
  demoMode: boolean
}

export type TransactionResponse = {
  id: string
  fromAccountId: string
  toAccountId: string
  counterpartyAccountId: string
  amount: string
  direction: 'INCOMING' | 'OUTGOING' | string
  status: string
  createdAt: string
}

export type TransactionPageResponse = {
  items: TransactionResponse[]
  nextCursor: string | null
  hasMore: boolean
}

export class ApiClientError extends Error {
  readonly status: number
  readonly body: ApiError | null

  constructor(status: number, body: ApiError | null) {
    super(body?.message ?? `Request failed (${status})`)
    this.status = status
    this.body = body
  }
}

const TOKEN_KEY = 'rupi.token'

export function getStoredToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setStoredToken(token: string | null): void {
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(TOKEN_KEY)
  }
}

function apiBase(): string {
  return import.meta.env.VITE_API_BASE_URL ?? ''
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  token?: string | null,
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${apiBase()}${path}`, { ...init, headers })
  if (!response.ok) {
    throw await toError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function apiGet<T>(path: string, token?: string | null): Promise<T> {
  return request<T>(path, { method: 'GET' }, token)
}

export async function apiPost<T>(
  path: string,
  body: unknown,
  token?: string | null,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const headers = new Headers()
  if (extraHeaders) {
    for (const [key, value] of Object.entries(extraHeaders)) {
      headers.set(key, value)
    }
  }
  return request<T>(
    path,
    {
      method: 'POST',
      body: JSON.stringify(body),
      headers,
    },
    token,
  )
}

export function register(
  username: string,
  password: string,
): Promise<AuthResponse> {
  return apiPost<AuthResponse>('/api/v1/auth/register', { username, password })
}

export function login(username: string, password: string): Promise<AuthResponse> {
  return apiPost<AuthResponse>('/api/v1/auth/login', { username, password })
}

export function fetchMyAccount(token: string): Promise<AccountResponse> {
  return apiGet<AccountResponse>('/api/v1/accounts/me', token)
}

export function createTransfer(
  token: string,
  toAccountId: string,
  amount: string,
  idempotencyKey: string,
): Promise<TransactionResponse> {
  return apiPost<TransactionResponse>(
    '/api/v1/transactions',
    { toAccountId, amount },
    token,
    { 'Idempotency-Key': idempotencyKey },
  )
}

export function fetchTransactions(
  token: string,
  cursor?: string | null,
): Promise<TransactionPageResponse> {
  const query = cursor ? `?limit=20&cursor=${encodeURIComponent(cursor)}` : '?limit=20'
  return apiGet<TransactionPageResponse>(`/api/v1/transactions${query}`, token)
}

async function toError(response: Response): Promise<ApiClientError> {
  try {
    const body = (await response.json()) as ApiError
    return new ApiClientError(response.status, body)
  } catch {
    if (response.status === 400) {
      return new ApiClientError(response.status, {
        code: 'VALIDATION_ERROR',
        message:
          'Request was rejected before it reached the API. Try a shorter username (not an email), or restart the backend after the latest config change.',
      })
    }
    return new ApiClientError(response.status, null)
  }
}
