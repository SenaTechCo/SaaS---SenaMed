import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { AuthResponse, Clinic, User } from '../types/auth';
import {
  clearStoredSession,
  getStoredClinic,
  getStoredToken,
  getStoredUser,
  setStoredSession,
} from '../lib/storage';

interface AuthContextValue {
  token: string | null;
  user: User | null;
  clinic: Clinic | null;
  isAuthenticated: boolean;
  login: (auth: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [user, setUser] = useState<User | null>(() => getStoredUser());
  const [clinic, setClinic] = useState<Clinic | null>(() => getStoredClinic());

  const login = useCallback((auth: AuthResponse) => {
    setStoredSession(auth.token, auth.user, auth.clinic);
    setToken(auth.token);
    setUser(auth.user);
    setClinic(auth.clinic);
  }, []);

  const logout = useCallback(() => {
    clearStoredSession();
    setToken(null);
    setUser(null);
    setClinic(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      user,
      clinic,
      isAuthenticated: !!token,
      login,
      logout,
    }),
    [token, user, clinic, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
