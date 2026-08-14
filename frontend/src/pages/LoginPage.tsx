import { Link } from 'react-router-dom'

export function LoginPage() {
  return (
    <main className="mx-auto flex min-h-svh max-w-md flex-col justify-center px-6">
      <h1 className="font-display text-4xl">Sign in</h1>
      <p className="mt-2 text-mute">Auth wiring lands in the next slice.</p>
      <form className="mt-8 flex flex-col gap-4" onSubmit={(e) => e.preventDefault()}>
        <label className="flex flex-col gap-1 text-sm">
          Username
          <input
            name="username"
            autoComplete="username"
            className="border border-line bg-transparent px-3 py-2"
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Password
          <input
            type="password"
            name="password"
            autoComplete="current-password"
            className="border border-line bg-transparent px-3 py-2"
          />
        </label>
        <button
          type="submit"
          disabled
          className="bg-ink px-4 py-2 text-sm text-paper opacity-60"
        >
          Continue
        </button>
      </form>
      <Link to="/" className="mt-6 text-sm text-mute">
        Back
      </Link>
    </main>
  )
}
