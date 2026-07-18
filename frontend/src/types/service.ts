export interface ServiceOffering {
  id: string;
  name: string;
  description: string | null;
  durationMinutes: number;
  price: number;
  active: boolean;
  createdAt: string;
}

export interface ServiceOfferingPayload {
  name: string;
  description: string | null;
  durationMinutes: number;
  price: number;
}
