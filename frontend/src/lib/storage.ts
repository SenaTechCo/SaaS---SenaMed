import type { Clinic, User } from '../types/auth';

const TOKEN_KEY = 'senamed.token';
const USER_KEY = 'senamed.user';
const CLINIC_KEY = 'senamed.clinic';

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function getStoredClinic(): Clinic | null {
  const raw = localStorage.getItem(CLINIC_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Clinic;
  } catch {
    return null;
  }
}

export function setStoredSession(token: string, user: User, clinic: Clinic): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  localStorage.setItem(CLINIC_KEY, JSON.stringify(clinic));
}

export function clearStoredSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(CLINIC_KEY);
}
