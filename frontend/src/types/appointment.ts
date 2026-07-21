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
  services?: { serviceId: number; quantity: number }[];
}

export interface AppointmentReschedulePayload {
  date: string;
  startTime: string;
}

export type AppointmentStatus = 'CONFIRMED' | 'ATTENDED' | 'NO_SHOW' | 'CANCELLED';

export interface ServiceLine {
  id: number;
  serviceId: number;
  serviceName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

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
  services: ServiceLine[];
  price: number | null;
}
