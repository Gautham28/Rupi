import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiClientError, login } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const navigate = useNavigate()
  const { acceptAuth } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const auth = await login(username.trim(), password)
      acceptAuth(auth)
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(
        err instanceof ApiClientError
          ? err.message
          : 'Could not sign in. Check the API and try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto flex min-h-svh max-w-md flex-col justify-center px-6">
      <h1 className="font-display text-4xl">Sign in</h1>
      <p className="mt-2 text-mute">
        Use the username and password from registration.
      </p>
      <form className="mt-8 flex flex-col gap-4" onSubmit={onSubmit}>
        <label className="flex flex-col gap-1 text-sm">
          Username
          <input
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            className="border border-line bg-transparent px-3 py-2"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Password
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            className="border border-line bg-transparent px-3 py-2"
          />
        </label>
        {error ? <p className="text-sm text-copper-dark">{error}</p> : null}
        <button
          type="submit"
          disabled={submitting}
          className="bg-ink px-4 py-2 text-sm text-paper disabled:opacity-60"
        >
          {submitting ? 'Signing in…' : 'Continue'}
        </button>
      </form>
      <p className="mt-6 text-sm text-mute">
        No account yet?{' '}
        <Link to="/register" className="text-ink underline">
          Create one
        </Link>
      </p>
      <Link to="/" className="mt-3 text-sm text-mute">
        Back
      </Link>
    </main>
  )
}
