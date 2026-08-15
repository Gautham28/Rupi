import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ApiClientError,
  createTransfer,
  fetchMyAccount,
  fetchTransactions,
  type AccountResponse,
  type TransactionResponse,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function DashboardPage() {
  const { token, logout } = useAuth()
  const navigate = useNavigate()
  const [account, setAccount] = useState<AccountResponse | null>(null)
  const [transactions, setTransactions] = useState<TransactionResponse[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [transferError, setTransferError] = useState<string | null>(null)
  const [transferSuccess, setTransferSuccess] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [toAccountId, setToAccountId] = useState('')
  const [amount, setAmount] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [submitting, setSubmitting] = useState(false)

  const refresh = useCallback(async () => {
    if (!token) {
      return
    }
    setLoading(true)
    try {
      const [accountValue, history] = await Promise.all([
        fetchMyAccount(token),
        fetchTransactions(token),
      ])
      setAccount(accountValue)
      setTransactions(history.items)
      setNextCursor(history.nextCursor)
      setHasMore(history.hasMore)
      setError(null)
    } catch (err) {
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
    } finally {
      setLoading(false)
    }
  }, [token, logout, navigate])

  useEffect(() => {
    void refresh()
  }, [refresh])

  function onLogout() {
    logout()
    navigate('/', { replace: true })
  }

  async function onTransfer(event: FormEvent) {
    event.preventDefault()
    if (!token) {
      return
    }
    setTransferError(null)
    setTransferSuccess(null)
    setSubmitting(true)
    try {
      const normalizedAmount = Number(amount).toFixed(2)
      const result = await createTransfer(
        token,
        toAccountId.trim(),
        normalizedAmount,
        idempotencyKey,
      )
      setTransferSuccess(
        `Sent ${result.amount} sandbox credits. Transfer ${result.id.slice(0, 8)}…`,
      )
      setAmount('')
      setIdempotencyKey(crypto.randomUUID())
      await refresh()
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 401) {
        logout()
        navigate('/login', { replace: true })
        return
      }
      setTransferError(
        err instanceof ApiClientError
          ? err.message
          : 'Transfer failed. Try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  async function onLoadMore() {
    if (!token || !nextCursor) {
      return
    }
    try {
      const history = await fetchTransactions(token, nextCursor)
      setTransactions((prev) => [...prev, ...history.items])
      setNextCursor(history.nextCursor)
      setHasMore(history.hasMore)
    } catch (err) {
      setError(
        err instanceof ApiClientError
          ? err.message
          : 'Could not load more transfers.',
      )
    }
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
        <>
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
              <button
                type="button"
                className="mt-2 text-sm text-ink underline"
                onClick={() => navigator.clipboard.writeText(account.accountId)}
              >
                Copy account ID
              </button>
            </div>
          </dl>

          <section className="mt-12 border-t border-line pt-8">
            <h2 className="font-display text-2xl">Send sandbox credits</h2>
            <p className="mt-2 text-sm text-mute">
              Paste another user’s account ID. Retries reuse the same idempotency
              key until this transfer succeeds.
            </p>
            <form className="mt-6 flex flex-col gap-4" onSubmit={onTransfer}>
              <label className="flex flex-col gap-1 text-sm">
                Recipient account ID
                <input
                  value={toAccountId}
                  onChange={(e) => setToAccountId(e.target.value)}
                  required
                  className="border border-line bg-transparent px-3 py-2 font-mono text-sm"
                />
              </label>
              <label className="flex flex-col gap-1 text-sm">
                Amount
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  max="1000000"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  required
                  className="border border-line bg-transparent px-3 py-2"
                />
              </label>
              {transferError ? (
                <p className="text-sm text-copper-dark">{transferError}</p>
              ) : null}
              {transferSuccess ? (
                <p className="text-sm text-ink">{transferSuccess}</p>
              ) : null}
              <button
                type="submit"
                disabled={submitting}
                className="bg-ink px-4 py-2 text-sm text-paper disabled:opacity-60"
              >
                {submitting ? 'Sending…' : 'Send transfer'}
              </button>
            </form>
          </section>

          <section className="mt-12 border-t border-line pt-8">
            <h2 className="font-display text-2xl">Recent activity</h2>
            {transactions.length === 0 ? (
              <p className="mt-4 text-sm text-mute">No transfers yet.</p>
            ) : (
              <ul className="mt-4 divide-y divide-line border border-line">
                {transactions.map((tx) => (
                  <li key={tx.id} className="flex items-start justify-between gap-4 px-3 py-3 text-sm">
                    <div>
                      <p className="font-medium">
                        {tx.direction === 'OUTGOING' ? 'Sent' : 'Received'} {tx.amount}
                      </p>
                      <p className="mt-1 break-all font-mono text-xs text-mute">
                        {tx.counterpartyAccountId}
                      </p>
                    </div>
                    <p className="shrink-0 text-xs text-mute">
                      {new Date(tx.createdAt).toLocaleString()}
                    </p>
                  </li>
                ))}
              </ul>
            )}
            {hasMore ? (
              <button
                type="button"
                onClick={() => void onLoadMore()}
                className="mt-4 border border-line px-3 py-2 text-sm"
              >
                Load more
              </button>
            ) : null}
          </section>
        </>
      ) : null}

      <p className="mt-12 text-sm text-mute">
        <Link to="/" className="text-ink underline">
          Home
        </Link>
      </p>
    </main>
  )
}
