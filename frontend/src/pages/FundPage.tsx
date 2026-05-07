import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { fundWallet } from '../api/payments';
import Field from '../components/Field';
import Button from '../components/Button';
import ErrorBox from '../components/ErrorBox';

export default function FundPage() {
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
    const walletId = localStorage.getItem('walletId');
    if (!walletId) {
      setError(new Error('No wallet found. Create one from the Wallet page first.'));
      setLoading(false);
      return;
    }
    try {
      await fundWallet(walletId, { amount, stablecoin, idempotencyKey });
      navigate('/wallet');
    } catch (e) {
      setError(e);
      setIdempotencyKey(crypto.randomUUID());
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1>Fund</h1>
      <form onSubmit={handleSubmit}>
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
        <Field
          label="Amount"
          name="amount"
          type="number"
          value={amount}
          onChange={setAmount}
          required
          step="0.00000001"
        />
        <Button type="submit" disabled={loading}>
          {loading ? 'Funding…' : 'Fund wallet'}
        </Button>
      </form>
      <ErrorBox error={error} />
    </div>
  );
}
