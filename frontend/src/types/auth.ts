export interface Clinic {
  id: string;
  name: string;
  slug: string;
  status: string;
  timezone: string;
  trialEndsAt: string;
}

export type UserRole = 'ADMIN' | 'DOCTOR';

export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  doctorId: string | null;
}

export interface AuthResponse {
  token: string;
  clinic: Clinic;
  user: User;
}

export interface RegisterClinicPayload {
  clinicName: string;
  adminName: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}
