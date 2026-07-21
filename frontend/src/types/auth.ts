export interface Clinic {
  id: string;
  name: string;
  slug: string;
  status: string;
  timezone: string;
  trialEndsAt: string;
}

export type UserRole = 'ADMIN' | 'DOCTOR' | 'STAFF';

export type Permission =
  | 'MANAGE_PATIENTS'
  | 'MANAGE_APPOINTMENTS'
  | 'MANAGE_FINANCE'
  | 'MANAGE_SERVICES'
  | 'MANAGE_USERS'
  | 'VIEW_REPORTS';

export interface User {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  doctorId: string | null;
  permissions: Permission[];
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
