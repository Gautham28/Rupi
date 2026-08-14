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

export async function apiGet<T>(path: string, token?: string | null): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${apiBase()}${path}`, { headers })
  if (!response.ok) {
    throw await toError(response)
  }
  return (await response.json()) as T
}

async function toError(response: Response): Promise<ApiClientError> {
  try {
    const body = (await response.json()) as ApiError
    return new ApiClientError(response.status, body)
  } catch {
    return new ApiClientError(response.status, null)
  }
}
