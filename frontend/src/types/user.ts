import type { Permission, UserRole } from './auth';

export interface ManagedUser {
  id: string;
  name: string;
  email: string;
  role: UserRole;
  permissions: Permission[];
  doctorId: string | null;
  doctorName: string | null;
  doctorSpecialty: string | null;
}

export interface CreateStaffUserPayload {
  name: string;
  email: string;
  password: string;
  permissions: Permission[];
}

export interface UpdateUserPayload {
  name: string;
  email: string;
  permissions: Permission[];
}
