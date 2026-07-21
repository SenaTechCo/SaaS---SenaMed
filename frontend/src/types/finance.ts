export interface Receivable {
  id: number;
  appointmentId: number;
  patientName: string;
  doctorName: string;
  description: string;
  amount: number;
  status: 'PENDING' | 'PAID';
  paidAt: string | null;
  createdAt: string;
}

export interface CommissionConfig {
  doctorId: number;
  percentage: number;
  active: boolean;
}

export interface CommissionCalculation {
  doctorId: number;
  year: number;
  month: number;
  percentage: number;
  totalBilled: number;
  commissionAmount: number;
}

export interface FinanceSummary {
  pendingTotal: number;
  paidThisMonthTotal: number;
}
