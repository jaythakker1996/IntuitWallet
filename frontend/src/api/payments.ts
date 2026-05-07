import { postWithStatus } from './client';
import type { TransactionResponse } from '../types/api';

export async function sendPayment(args: {
  fromWalletId: string;
  toWalletId: string;
  amount: string;
  stablecoin: string;
  idempotencyKey: string;
}): Promise<{ tx: TransactionResponse; created: boolean }> {
  const { data, status } = await postWithStatus<TransactionResponse>('/payments', args);
  return { tx: data, created: status === 201 };
}

export async function fundWallet(
  walletId: string,
  args: { amount: string; stablecoin: string; idempotencyKey: string },
): Promise<{ tx: TransactionResponse; created: boolean }> {
  const { data, status } = await postWithStatus<TransactionResponse>(
    `/wallets/${walletId}/fund`,
    args,
  );
  return { tx: data, created: status === 201 };
}
