import { Link } from 'react-router-dom';
import type { WalletBalanceEntry } from '../types/api';

export default function BalanceRow({ balance }: { balance: WalletBalanceEntry }) {
  return (
    <Link to={`/wallet/balances/${balance.stablecoin}`} className="balance-row">
      <span className="balance-coin">{balance.stablecoin}</span>
      <span className="balance-amount">{balance.runningAvailable}</span>
      <span className="balance-arrow">›</span>
    </Link>
  );
}
