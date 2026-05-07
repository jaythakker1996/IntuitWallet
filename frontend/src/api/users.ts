import { get, postWithStatus } from './client';
import type { UserResponse } from '../types/api';

// Login uses the existing idempotent POST /api/v1/users with default role/region
// per ADR 005 §4. New users get 201; returning users get 200 with their stored
// role/region (defaults are ignored on dedup).
export async function loginOrCreate(
  email: string,
): Promise<{ user: UserResponse; created: boolean }> {
  const { data, status } = await postWithStatus<UserResponse>('/users', {
    email,
    role: 'CONSUMER',
    homeRegion: 'us-east-1',
  });
  return { user: data, created: status === 201 };
}

export async function createUser(
  email: string,
  role: string,
  homeRegion: string,
): Promise<{ user: UserResponse; created: boolean }> {
  const { data, status } = await postWithStatus<UserResponse>('/users', {
    email,
    role,
    homeRegion,
  });
  return { user: data, created: status === 201 };
}

export async function getUser(intuitAccountId: string): Promise<UserResponse> {
  return get<UserResponse>(`/users/${intuitAccountId}`);
}
