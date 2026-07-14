export interface Plan {
  id: number;
  name: string;
  priceAmount: number;
  maxDoctors: number;
}

export type SubscriptionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Subscription {
  id: number;
  planId: number;
  planName: string;
  status: SubscriptionStatus;
  periodMonths: number;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
}

export interface CheckoutPayload {
  planId: number;
  periodMonths: number;
}

export interface CheckoutResponse {
  subscriptionId: number;
  checkoutUrl: string;
}
