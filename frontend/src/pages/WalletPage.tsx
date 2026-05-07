import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createWallet, getBalances, getWalletByUser } from '../api/wallets';
import { HttpError } from '../api/client';
import type { WalletBalanceEntry, WalletResponse } from '../types/api';
import BalanceRow from '../components/BalanceRow';
import Button from '../components/Button';
import ErrorBox from '../components/ErrorBox';

export default function WalletPage() {
  const [wallet, setWallet] = useState<WalletResponse | null>(null);
  const [balances, setBalances] = useState<WalletBalanceEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [hasWallet, setHasWallet] = useState<boolean | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    const intuitAccountId = localStorage.getItem('intuitAccountId');
    if (!intuitAccountId) {
      setError(new Error('No user in localStorage'));
      setLoading(false);
      return;
    }
    try {
      const w = await getWalletByUser(intuitAccountId);
      setWallet(w);
      localStorage.setItem('walletId', w.walletId);
      setHasWallet(true);
      const b = await getBalances(w.walletId);
      setBalances(b.balances);
    } catch (e) {
      if (e instanceof HttpError && e.status === 404) {
        setHasWallet(false);
      } else {
        setError(e);
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleCreate() {
    setError(null);
    const intuitAccountId = localStorage.getItem('intuitAccountId');
    if (!intuitAccountId) return;
    try {
      await createWallet(intuitAccountId);
      await load();
    } catch (e) {
      setError(e);
    }
  }

  if (loading) return <div className="page">Loading…</div>;

  if (hasWallet === false) {
    return (
      <div className="page-narrow">
        <h1>Create your wallet</h1>
        <p>You don't have a wallet yet.</p>
        <Button onClick={handleCreate}>Create wallet</Button>
        <ErrorBox error={error} />
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Wallet</h1>
      <p className="muted">{wallet?.walletId}</p>
      <ErrorBox error={error} />
      {balances.length === 0 ? (
        <p>
          No balances yet. <Link to="/fund">Fund your wallet</Link>.
        </p>
      ) : (
        <div className="balance-list">
          {balances.map((b) => (
            <BalanceRow key={b.stablecoin} balance={b} />
          ))}
        </div>
      )}
      <Link to="/wallet/transactions" className="link">
        View transaction history →
      </Link>
    </div>
  );
}
