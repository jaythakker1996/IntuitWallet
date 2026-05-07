import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getTransaction } from '../api/wallets';
import { HttpError } from '../api/client';
import type { WalletTransactionResponse } from '../types/api';
import ErrorBox from '../components/ErrorBox';

export default function TransactionDetailPage() {
  const { txId } = useParams<{ txId: string }>();
  const [tx, setTx] = useState<WalletTransactionResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    const walletId = localStorage.getItem('walletId');
    if (!walletId || !txId) return;
    getTransaction(walletId, txId)
      .then(setTx)
      .catch((e) => {
        if (e instanceof HttpError && e.status === 404) setNotFound(true);
        else setError(e);
      });
  }, [txId]);

  if (notFound) {
    return (
      <div className="page-narrow">
        <h1>Transaction not found</h1>
        <Link to="/wallet/transactions">← Back to transactions</Link>
      </div>
    );
  }
  if (!tx && !error) return <div className="page">Loading…</div>;

  return (
    <div className="page">
      <Link to="/wallet/transactions" className="back-link">← Back to transactions</Link>
      <h1>Transaction</h1>
      <ErrorBox error={error} />
      {tx && (
        <dl className="detail-list">
          {Object.entries(tx).map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{String(v)}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}
