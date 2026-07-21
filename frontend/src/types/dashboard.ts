import type { Appointment } from './appointment';

export interface DashboardSummary {
  todayCount: number;
  upcoming: Appointment[];
  activeDoctorCount: number | null;
  pendingReceivablesTotal: number | null;
  paidThisMonthTotal: number | null;
}

export interface DashboardReportsDailyPoint {
  date: string; // YYYY-MM-DD
  received: number;
  receivable: number;
  attended: number;
  cancelled: number;
  noShow: number;
}

export interface DashboardReports {
  dailySeries: DashboardReportsDailyPoint[];
  attendedCount: number;
  cancelledCount: number;
  noShowCount: number;
  grossRevenue: number;
  directCost: number;
  grossProfit: number;
}
