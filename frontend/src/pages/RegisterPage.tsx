import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiClientError, register } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function RegisterPage() {
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
      const auth = await register(username.trim(), password)
      acceptAuth(auth)
      navigate('/dashboard', { replace: true })
    } catch (err) {
      if (err instanceof ApiClientError) {
        const fieldMessage = err.body?.fieldErrors?.[0]?.message
        setError(fieldMessage ? `${err.message} (${fieldMessage})` : err.message)
      } else {
        setError('Could not create the account. Check the API and try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto flex min-h-svh max-w-md flex-col justify-center px-6">
      <h1 className="font-display text-4xl">Create account</h1>
      <p className="mt-2 text-mute">
        Registration mints <span className="text-ink">1,000 sandbox credits</span>.
        Use a username like <span className="text-ink">gautham</span> (letters,
        numbers, <code>.</code> <code>_</code> <code>-</code> — not an email).
        Password must be at least 10 characters.
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
            minLength={3}
            maxLength={32}
            pattern="[A-Za-z0-9._-]+"
            title="Use letters, numbers, dots, underscores, or hyphens — not an email address"
            className="border border-line bg-transparent px-3 py-2"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Password
          <input
            type="password"
            name="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={10}
            maxLength={72}
            className="border border-line bg-transparent px-3 py-2"
          />
        </label>
        {error ? <p className="text-sm text-copper-dark">{error}</p> : null}
        <button
          type="submit"
          disabled={submitting}
          className="bg-ink px-4 py-2 text-sm text-paper disabled:opacity-60"
        >
          {submitting ? 'Creating…' : 'Create account'}
        </button>
      </form>
      <p className="mt-6 text-sm text-mute">
        Already registered?{' '}
        <Link to="/login" className="text-ink underline">
          Sign in
        </Link>
      </p>
      <Link to="/" className="mt-3 text-sm text-mute">
        Back
      </Link>
    </main>
  )
}
