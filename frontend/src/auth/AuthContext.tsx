import {
  createContext,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  getStoredToken,
  setStoredToken,
  type AuthResponse,
} from '../api/client'

type AuthState = {
  token: string | null
  setToken: (token: string | null) => void
  acceptAuth: (auth: AuthResponse) => void
  logout: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getStoredToken())

  const value = useMemo(
    () => ({
      token,
      setToken: (next: string | null) => {
        setStoredToken(next)
        setTokenState(next)
      },
      acceptAuth: (auth: AuthResponse) => {
        setStoredToken(auth.token)
        setTokenState(auth.token)
      },
      logout: () => {
        setStoredToken(null)
        setTokenState(null)
      },
    }),
    [token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
