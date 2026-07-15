export interface GoogleCalendarStatus {
  connected: boolean;
  googleEmail: string | null;
}

export interface ConnectUrlResponse {
  authorizationUrl: string;
}
