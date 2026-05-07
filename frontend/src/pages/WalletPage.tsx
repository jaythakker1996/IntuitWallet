import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { createWallet, getBalances, getWalletByUser } from '../api/wallets';
import { HttpError } from '../api/client';
import type { WalletBalanceEntry, WalletResponse } from '../types/api';
import BalanceRow from '../components/BalanceRow';
import Button from '../components/Button';
import ErrorBox from '../components/ErrorBox';
import { shortId } from '../utils/format';

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
        <p className="muted">You don't have a wallet yet. Create one to start funding and sending.</p>
        <div style={{ marginTop: 16 }}>
          <Button onClick={handleCreate}>Create wallet</Button>
        </div>
        <ErrorBox error={error} />
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Wallet</h1>
      <p className="muted" title={wallet?.walletId}>
        {wallet ? shortId(wallet.walletId) : ''}
      </p>
      <ErrorBox error={error} />
      {balances.length === 0 ? (
        <div className="empty">
          <span className="empty-icon">💰</span>
          <p>No balances yet.</p>
          <p>
            <Link to="/fund">Fund your wallet</Link> to get started.
          </p>
        </div>
      ) : (
        <>
          <h2>Balances</h2>
          <div className="balance-list">
            {balances.map((b) => (
              <BalanceRow key={b.stablecoin} balance={b} />
            ))}
          </div>
        </>
      )}
      <Link to="/wallet/transactions" className="link">
        View transaction history →
      </Link>
    </div>
  );
}
