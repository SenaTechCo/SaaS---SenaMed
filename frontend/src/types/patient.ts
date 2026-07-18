export interface Patient {
  id: string;
  name: string;
  socialName: string | null;
  birthDate: string | null;
  sex: string | null;
  cpf: string | null;
  email: string | null;
  phone: string | null;
  zipCode: string | null;
  street: string | null;
  number: string | null;
  complement: string | null;
  neighborhood: string | null;
  city: string | null;
  state: string | null;
  referralSource: string | null;
  notes: string | null;
  lgpdConsent: boolean;
  lgpdConsentAt: string | null;
  active: boolean;
  createdAt: string;
}

export interface PatientPayload {
  name: string;
  socialName: string | null;
  birthDate: string | null;
  sex: string | null;
  cpf: string | null;
  email: string | null;
  phone: string | null;
  zipCode: string | null;
  street: string | null;
  number: string | null;
  complement: string | null;
  neighborhood: string | null;
  city: string | null;
  state: string | null;
  referralSource: string | null;
  notes: string | null;
  lgpdConsent: boolean;
}
