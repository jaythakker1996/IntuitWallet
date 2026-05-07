import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getBalance, getTransactions } from '../api/wallets';
import { HttpError } from '../api/client';
import type {
  WalletBalanceResponse,
  WalletTransactionResponse,
} from '../types/api';
import TransactionRow from '../components/TransactionRow';
import ErrorBox from '../components/ErrorBox';

export default function StablecoinPage() {
  const { stablecoin } = useParams<{ stablecoin: string }>();
  const [balance, setBalance] = useState<WalletBalanceResponse | null>(null);
  const [txs, setTxs] = useState<WalletTransactionResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [notEnabled, setNotEnabled] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const walletId = localStorage.getItem('walletId');
    if (!walletId || !stablecoin) return;
    (async () => {
      try {
        const b = await getBalance(walletId, stablecoin);
        setBalance(b);
        const all = await getTransactions(walletId);
        setTxs(all.transactions.filter((t) => t.stablecoin === stablecoin));
      } catch (e) {
        if (
          e instanceof HttpError &&
          e.status === 404 &&
          e.body.error === 'STABLECOIN_NOT_ENABLED'
        ) {
          setNotEnabled(true);
        } else {
          setError(e);
        }
      } finally {
        setLoading(false);
      }
    })();
  }, [stablecoin]);

  if (loading) return <div className="page">Loading…</div>;

  if (notEnabled) {
    return (
      <div className="page-narrow">
        <h1>{stablecoin}</h1>
        <p>You haven't transacted in {stablecoin} yet.</p>
        <Link to="/wallet">← Back to wallet</Link>
      </div>
    );
  }

  return (
    <div className="page">
      <Link to="/wallet">← Back to wallet</Link>
      <h1>{stablecoin}</h1>
      <ErrorBox error={error} />
      {balance && (
        <div className="balance-detail">
          <p>
            <strong>Available:</strong> {balance.runningAvailable}
          </p>
          <p>
            <strong>Pending:</strong> {balance.runningPending}
          </p>
          <p className="muted">
            Last entry sequence: {balance.lastEntrySequence}, at{' '}
            {new Date(balance.lastEntryAt).toLocaleString()}
          </p>
        </div>
      )}
      <h2>Transactions in {stablecoin}</h2>
      {txs.length === 0 ? (
        <p>No transactions in this stablecoin yet.</p>
      ) : (
        <div className="tx-list">
          {txs.map((t) => (
            <TransactionRow key={t.txId} tx={t} />
          ))}
        </div>
      )}
    </div>
  );
}
