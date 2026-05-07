import { Link } from 'react-router-dom';
import type { WalletTransactionResponse } from '../types/api';

export default function TransactionRow({ tx }: { tx: WalletTransactionResponse }) {
  const arrow = tx.direction === 'OUTBOUND' ? '↗' : '↙';
  return (
    <Link to={`/wallet/transactions/${tx.txId}`} className="tx-row">
      <span className="tx-arrow">{arrow}</span>
      <span className="tx-type">{tx.type}</span>
      <span className="tx-amount">
        {tx.amount} {tx.stablecoin}
      </span>
      <span className="tx-status">{tx.status}</span>
      <span className="tx-time">{new Date(tx.createdAt).toLocaleString()}</span>
    </Link>
  );
}
