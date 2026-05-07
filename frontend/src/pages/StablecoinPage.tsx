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
import { formatAbsolute, formatAmount } from '../utils/format';

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
        <p className="muted">You haven't transacted in {stablecoin} yet.</p>
        <Link to="/wallet" className="link">← Back to wallet</Link>
      </div>
    );
  }

  return (
    <div className="page">
      <Link to="/wallet" className="back-link">← Back to wallet</Link>
      <h1>{stablecoin}</h1>
      <ErrorBox error={error} />
      {balance && (
        <div className="balance-detail">
          <div className="label">Available</div>
          <div className="big-amount">{formatAmount(balance.runningAvailable)}</div>
          <div className="muted">
            Pending: {formatAmount(balance.runningPending)} · entry #{balance.lastEntrySequence} · {formatAbsolute(balance.lastEntryAt)}
          </div>
        </div>
      )}
      <h2>Transactions</h2>
      {txs.length === 0 ? (
        <div className="empty">
          <p>No transactions in {stablecoin} yet.</p>
        </div>
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
