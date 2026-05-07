import { Link } from 'react-router-dom';
import type { WalletTransactionResponse } from '../types/api';
import { formatAmount, formatRelative } from '../utils/format';

export default function TransactionRow({ tx }: { tx: WalletTransactionResponse }) {
  const inbound = tx.direction === 'INBOUND';
  const sign = inbound ? '+' : '−';
  const colorClass = inbound ? 'credit' : 'debit';
  const iconChar = inbound ? '↓' : '↑';

  return (
    <Link to={`/wallet/transactions/${tx.txId}`} className="tx-row">
      <span className={`tx-icon ${colorClass}`}>{iconChar}</span>
      <span className="tx-main">
        <span className="tx-type">{tx.type}</span>
        <span className="tx-time">{formatRelative(tx.createdAt)}</span>
      </span>
      <span className={`tx-amount ${colorClass}`}>
        {sign}
        {formatAmount(tx.amount)} {tx.stablecoin}
      </span>
      <span className="tx-status">{tx.status}</span>
    </Link>
  );
}
