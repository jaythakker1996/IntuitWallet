// TS interfaces mirroring the Java DTOs from the backend (specs 002-009).
// Field names match Java exactly — no camelCase/snake_case translation.

export type Direction = 'OUTBOUND' | 'INBOUND';
export type EntryType = 'DEBIT' | 'CREDIT' | 'SETTLEMENT' | 'REVERSAL';
export type TransactionType =
  | 'SEND' | 'RECEIVE' | 'QR_PAY' | 'FUND' | 'WITHDRAW'
  | 'BATCH_DEBIT' | 'SETTLEMENT' | 'REVERSAL';
export type TxStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REVERSED';
export type WalletType = 'USER' | 'SYSTEM';
export type WalletStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface UserResponse {
  intuitAccountId: string;
  email: string;
  role: string;
  homeRegion: string;
  createdAt: string;
  updatedAt: string;
}

export interface WalletResponse {
  walletId: string;
  intuitAccountId: string;
  type: WalletType;
  status: WalletStatus;
  createdAt: string;
  updatedAt: string;
}

export interface WalletBalanceResponse {
  walletId: string;
  stablecoin: string;
  runningAvailable: string;
  runningPending: string;
  lastEntrySequence: number;
  lastEntryAt: string;
}

export interface WalletBalanceEntry {
  stablecoin: string;
  runningAvailable: string;
  runningPending: string;
  lastEntrySequence: number;
  lastEntryAt: string;
}

export interface WalletBalancesResponse {
  walletId: string;
  balances: WalletBalanceEntry[];
}

export interface WalletTransactionResponse {
  txId: string;
  type: TransactionType;
  direction: Direction;
  entryType: EntryType;
  fromWalletId: string;
  toWalletId: string;
  amount: string;
  stablecoin: string;
  fee: string;
  status: TxStatus;
  runningAvailableAfter: string;
  runningPendingAfter: string;
  entrySequence: number;
  createdAt: string;
}

export interface WalletTransactionsResponse {
  walletId: string;
  transactions: WalletTransactionResponse[];
}

export interface TransactionResponse {
  txId: string;
  type: TransactionType;
  fromWalletId: string;
  toWalletId: string;
  amount: string;
  stablecoin: string;
  fee: string;
  status: TxStatus;
  createdAt: string;
}

export interface QrResponse {
  qrCodeId: string;
  walletId: string;
  payload: string;
  type: 'STATIC' | 'DYNAMIC';
  status: 'ACTIVE' | 'REVOKED';
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ApiError {
  error: string;
  message?: string;
}
