import { Link } from 'react-router-dom';
import type { WalletBalanceEntry } from '../types/api';
import { formatAmount } from '../utils/format';

export default function BalanceRow({ balance }: { balance: WalletBalanceEntry }) {
  return (
    <Link to={`/wallet/balances/${balance.stablecoin}`} className="balance-row">
      <span className="balance-coin">{balance.stablecoin}</span>
      <span className="balance-amount">{formatAmount(balance.runningAvailable)}</span>
      <span className="balance-arrow">›</span>
    </Link>
  );
}
