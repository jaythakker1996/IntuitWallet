import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getTransactions } from '../api/wallets';
import type { WalletTransactionResponse } from '../types/api';
import TransactionRow from '../components/TransactionRow';
import ErrorBox from '../components/ErrorBox';

export default function TransactionsPage() {
  const [txs, setTxs] = useState<WalletTransactionResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const walletId = localStorage.getItem('walletId');
    if (!walletId) {
      setError(new Error('No wallet found. Visit /wallet first.'));
      setLoading(false);
      return;
    }
    getTransactions(walletId)
      .then((r) => setTxs(r.transactions))
      .catch((e) => setError(e))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="page">Loading…</div>;

  return (
    <div className="page">
      <Link to="/wallet" className="back-link">← Back to wallet</Link>
      <h1>Transaction history</h1>
      <ErrorBox error={error} />
      {txs.length === 0 ? (
        <div className="empty">
          <span className="empty-icon">📭</span>
          <p>No transactions yet.</p>
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
