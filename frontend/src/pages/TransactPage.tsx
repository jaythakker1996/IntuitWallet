import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { sendPayment } from '../api/payments';
import Field from '../components/Field';
import Button from '../components/Button';
import ErrorBox from '../components/ErrorBox';

export default function TransactPage() {
  const [toWalletId, setToWalletId] = useState('');
  const [amount, setAmount] = useState('');
  const [stablecoin, setStablecoin] = useState('USDC');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    const fromWalletId = localStorage.getItem('walletId');
    if (!fromWalletId) {
      setError(new Error('No wallet found. Create one from the Wallet page first.'));
      setLoading(false);
      return;
    }
    try {
      const { tx } = await sendPayment({
        fromWalletId,
        toWalletId,
        amount,
        stablecoin,
        idempotencyKey,
      });
      navigate(`/wallet/transactions/${tx.txId}`);
    } catch (e) {
      setError(e);
      setIdempotencyKey(crypto.randomUUID());
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1>Send</h1>
      <form onSubmit={handleSubmit}>
        <Field
          label="To wallet ID"
          name="toWalletId"
          value={toWalletId}
          onChange={setToWalletId}
          required
          placeholder="UUID of recipient wallet"
        />
        <Field
          label="Amount"
          name="amount"
          type="number"
          value={amount}
          onChange={setAmount}
          required
          step="0.00000001"
        />
        <Field
          label="Stablecoin"
          name="stablecoin"
          type="select"
          value={stablecoin}
          onChange={setStablecoin}
          options={[
            { value: 'USDC', label: 'USDC' },
            { value: 'USDT', label: 'USDT' },
            { value: 'EURC', label: 'EURC' },
          ]}
        />
        <Button type="submit" disabled={loading}>
          {loading ? 'Sending…' : 'Send'}
        </Button>{' '}
        <Button variant="secondary" disabled>
          Scan QR (coming soon)
        </Button>
      </form>
      <ErrorBox error={error} />
    </div>
  );
}
