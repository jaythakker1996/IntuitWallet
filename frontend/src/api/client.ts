import type { ApiError } from '../types/api';

const BASE_URL = 'http://localhost:8081/api/v1';

export class HttpError extends Error {
  status: number;
  body: ApiError;

  constructor(status: number, body: ApiError) {
    super(body.message ?? body.error ?? `HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

async function parseBody(res: Response): Promise<unknown> {
  if (res.status === 204) return undefined;
  const text = await res.text();
  return text ? JSON.parse(text) : {};
}

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`);
  const body = (await parseBody(res)) as ApiError | T;
  if (!res.ok) throw new HttpError(res.status, body as ApiError);
  return body as T;
}

export async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = (await parseBody(res)) as ApiError | T;
  if (!res.ok) throw new HttpError(res.status, data as ApiError);
  return data as T;
}

export async function postWithStatus<T>(
  path: string,
  body: unknown,
): Promise<{ data: T; status: number }> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = (await parseBody(res)) as ApiError | T;
  if (!res.ok) throw new HttpError(res.status, data as ApiError);
  return { data: data as T, status: res.status };
}
