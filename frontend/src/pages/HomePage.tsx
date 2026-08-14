import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiClientError, apiGet, type StatusResponse } from '../api/client'

export function HomePage() {
  const [status, setStatus] = useState<StatusResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    apiGet<StatusResponse>('/api/v1/status')
      .then((value) => {
        if (!cancelled) {
          setStatus(value)
          setError(null)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(
            err instanceof ApiClientError
              ? err.message
              : 'API is not reachable yet. Start the backend on port 8090.',
          )
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="mx-auto flex min-h-svh max-w-3xl flex-col justify-center px-6 py-16">
      <p className="mb-3 text-sm tracking-[0.18em] text-mute uppercase">
        Sandbox ledger
      </p>
      <h1 className="font-display text-5xl leading-tight font-medium tracking-tight">
        Rupi
      </h1>
      <p className="mt-4 max-w-xl text-lg text-mute">
        Transfer sandbox credits between accounts. Retries cannot double-debit.
        This is demo money only — nothing here is redeemable.
      </p>

      <div className="mt-8 flex gap-3">
        <Link
          to="/register"
          className="rounded-sm bg-copper px-4 py-2 text-sm font-medium text-paper"
        >
          Create account
        </Link>
        <Link
          to="/login"
          className="rounded-sm border border-line px-4 py-2 text-sm font-medium"
        >
          Sign in
        </Link>
      </div>

      <dl className="mt-12 grid gap-4 border-t border-line pt-8 sm:grid-cols-3">
        <div>
          <dt className="text-xs tracking-wide text-mute uppercase">Signup grant</dt>
          <dd className="mt-1 font-display text-2xl">1,000.00</dd>
        </div>
        <div>
          <dt className="text-xs tracking-wide text-mute uppercase">API</dt>
          <dd className="mt-1 font-display text-2xl">
            {status ? status.status : error ? 'down' : '…'}
          </dd>
        </div>
        <div>
          <dt className="text-xs tracking-wide text-mute uppercase">Demo faucet</dt>
          <dd className="mt-1 font-display text-2xl">
            {status ? (status.demoMode ? 'on' : 'off') : '—'}
          </dd>
        </div>
      </dl>
      {error ? <p className="mt-6 text-sm text-copper-dark">{error}</p> : null}
    </main>
  )
}
