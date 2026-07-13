import { getStoredToken } from './storage';

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(status: number, message: string, body: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

async function parseErrorMessage(response: Response): Promise<{ message: string; body: unknown }> {
  const fallback = `Request failed with status ${response.status}`;
  try {
    const body = await response.json();
    if (body && typeof body === 'object' && 'message' in body && typeof (body as Record<string, unknown>).message === 'string') {
      return { message: (body as Record<string, unknown>).message as string, body };
    }
    return { message: fallback, body };
  } catch {
    return { message: fallback, body: null };
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = getStoredToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    const { message, body } = await parseErrorMessage(response);
    throw new ApiError(response.status, message, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
