import { get, postWithStatus } from './client';
import type {
  WalletResponse,
  WalletBalanceResponse,
  WalletBalancesResponse,
  WalletTransactionResponse,
  WalletTransactionsResponse,
} from '../types/api';

export async function createWallet(
  intuitAccountId: string,
): Promise<{ wallet: WalletResponse; created: boolean }> {
  const { data, status } = await postWithStatus<WalletResponse>('/wallets', {
    intuitAccountId,
  });
  return { wallet: data, created: status === 201 };
}

export async function getWalletByUser(intuitAccountId: string): Promise<WalletResponse> {
  return get<WalletResponse>(`/wallets?intuitAccountId=${intuitAccountId}`);
}

export async function getWalletById(walletId: string): Promise<WalletResponse> {
  return get<WalletResponse>(`/wallets/${walletId}`);
}

export async function getBalances(walletId: string): Promise<WalletBalancesResponse> {
  return get<WalletBalancesResponse>(`/wallets/${walletId}/balances`);
}

export async function getBalance(
  walletId: string,
  stablecoin: string,
): Promise<WalletBalanceResponse> {
  return get<WalletBalanceResponse>(`/wallets/${walletId}/balances/${stablecoin}`);
}

export async function getTransactions(walletId: string): Promise<WalletTransactionsResponse> {
  return get<WalletTransactionsResponse>(`/wallets/${walletId}/transactions`);
}

export async function getTransaction(
  walletId: string,
  txId: string,
): Promise<WalletTransactionResponse> {
  return get<WalletTransactionResponse>(`/wallets/${walletId}/transactions/${txId}`);
}
