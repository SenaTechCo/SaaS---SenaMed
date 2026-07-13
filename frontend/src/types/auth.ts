export interface Clinic {
  id: string;
  name: string;
  slug: string;
  status: string;
  timezone: string;
  trialEndsAt: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: string;
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
