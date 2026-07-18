import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { AuthResponse, Clinic, User } from '../types/auth';
import { apiFetch } from '../lib/http';
import {
  clearStoredSession,
  getStoredClinic,
  getStoredToken,
  getStoredUser,
  setStoredClinic,
  setStoredSession,
  setStoredUser,
} from '../lib/storage';

interface AuthContextValue {
  token: string | null;
  user: User | null;
  clinic: Clinic | null;
  isAuthenticated: boolean;
  login: (auth: AuthResponse) => void;
  logout: () => void;
  refreshClinic: () => Promise<void>;
  updateUser: (user: User) => void;
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

  const refreshClinic = useCallback(async () => {
    const fresh = await apiFetch<Clinic>('/api/clinics/me');
    setStoredClinic(fresh);
    setClinic(fresh);
  }, []);

  const updateUser = useCallback((updated: User) => {
    setStoredUser(updated);
    setUser(updated);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      user,
      clinic,
      isAuthenticated: !!token,
      login,
      logout,
      refreshClinic,
      updateUser,
    }),
    [token, user, clinic, login, logout, refreshClinic, updateUser],
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
