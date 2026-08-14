import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ApiClientError,
  fetchMyAccount,
  type AccountResponse,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function DashboardPage() {
  const { token, logout } = useAuth()
  const navigate = useNavigate()
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!token) {
      return
    }
    let cancelled = false
    setLoading(true)
    fetchMyAccount(token)
      .then((value) => {
        if (!cancelled) {
          setAccount(value)
          setError(null)
        }
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return
        }
        if (err instanceof ApiClientError && err.status === 401) {
          logout()
          navigate('/login', { replace: true })
          return
        }
        setError(
          err instanceof ApiClientError
            ? err.message
            : 'Could not load your account.',
        )
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [token, logout, navigate])

  function onLogout() {
    logout()
    navigate('/', { replace: true })
  }

  return (
    <main className="mx-auto flex min-h-svh max-w-2xl flex-col px-6 py-12">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm tracking-[0.18em] text-mute uppercase">Dashboard</p>
          <h1 className="font-display mt-2 text-4xl">
            {account ? account.username : 'Your account'}
          </h1>
        </div>
        <button
          type="button"
          onClick={onLogout}
          className="border border-line px-3 py-2 text-sm"
        >
          Log out
        </button>
      </div>

      {loading ? <p className="mt-10 text-mute">Loading account…</p> : null}
      {error ? <p className="mt-10 text-sm text-copper-dark">{error}</p> : null}

      {account ? (
        <dl className="mt-10 grid gap-6 border-t border-line pt-8 sm:grid-cols-2">
          <div>
            <dt className="text-xs tracking-wide text-mute uppercase">
              Sandbox balance
            </dt>
            <dd className="font-display mt-1 text-3xl">{account.balance}</dd>
            <p className="mt-1 text-sm text-mute">Demo credits only</p>
          </div>
          <div>
            <dt className="text-xs tracking-wide text-mute uppercase">Account ID</dt>
            <dd className="mt-2 break-all font-mono text-sm">{account.accountId}</dd>
            <p className="mt-1 text-sm text-mute">
              Share this ID when transfers land in the next slice.
            </p>
          </div>
        </dl>
      ) : null}

      <p className="mt-12 text-sm text-mute">
        Transfers and the demo faucet are next.{' '}
        <Link to="/" className="text-ink underline">
          Home
        </Link>
      </p>
    </main>
  )
}
