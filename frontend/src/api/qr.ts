import { get, postWithStatus } from './client';
import type { QrResponse } from '../types/api';

export async function getQr(walletId: string): Promise<QrResponse> {
  return get<QrResponse>(`/wallets/${walletId}/qr`);
}

export async function createQr(
  walletId: string,
): Promise<{ qr: QrResponse; created: boolean }> {
  const { data, status } = await postWithStatus<QrResponse>(`/wallets/${walletId}/qr`, {});
  return { qr: data, created: status === 201 };
}
