export type DayOfWeek = 1 | 2 | 3 | 4 | 5 | 6 | 7;

export interface Doctor {
  id: string;
  name: string;
  specialty: string;
  email: string;
  phone: string;
  active: boolean;
}

export interface DoctorPayload {
  name: string;
  specialty: string;
  email: string;
  phone: string;
}

export interface AvailabilitySlot {
  id: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}

export interface AvailabilitySlotPayload {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}

export interface TimeOff {
  id: string;
  startDate: string;
  endDate: string | null;
  reason: string | null;
}

export interface TimeOffPayload {
  startDate: string;
  endDate?: string;
  reason?: string;
}
