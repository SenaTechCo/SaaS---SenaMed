export interface PublicDoctor {
  id: number;
  name: string;
  specialty: string | null;
}

export interface PublicClinic {
  slug: string;
  name: string;
  description: string | null;
  phone: string | null;
  email: string | null;
  timezone: string;
  logoUrl: string | null;
  coverImageUrl: string | null;
  primaryColor: string | null;
  secondaryColor: string | null;
  doctors: PublicDoctor[];
}

export interface AvailableSlotsResponse {
  date: string;
  slots: string[];
}

export interface AppointmentPayload {
  doctorId: number;
  date: string;
  startTime: string;
  patientId?: number | null;
  patientName: string;
  patientEmail: string;
  patientPhone: string | null;
  lgpdConsent: boolean;
  serviceId?: number | null;
}

export interface AppointmentReschedulePayload {
  date: string;
  startTime: string;
}

export type AppointmentStatus = 'CONFIRMED' | 'ATTENDED' | 'CANCELLED';

export interface Appointment {
  id: number;
  doctorId: number;
  doctorName: string;
  clinicName: string;
  date: string;
  startTime: string;
  endTime: string;
  patientId: number | null;
  patientName: string;
  status: AppointmentStatus;
  cancelToken: string;
  confirmedAt: string | null;
  serviceId: number | null;
  serviceName: string | null;
  price: number | null;
}
