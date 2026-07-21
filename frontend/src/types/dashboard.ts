import type { Appointment } from './appointment';

export interface DashboardSummary {
  todayCount: number;
  upcoming: Appointment[];
  activeDoctorCount: number | null;
  pendingReceivablesTotal: number | null;
  paidThisMonthTotal: number | null;
}
