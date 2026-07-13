export interface ClinicProfile {
  id: string;
  name: string;
  description: string | null;
  phone: string | null;
  email: string | null;
  timezone: string;
  maxDoctors: number;
  logoUrl: string | null;
  coverImageUrl: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
}

export interface ClinicProfilePayload {
  name: string;
  description?: string | null;
  phone?: string | null;
  email?: string | null;
  timezone: string;
  logoUrl?: string | null;
  coverImageUrl?: string | null;
  primaryColor?: string | null;
  secondaryColor?: string | null;
}
