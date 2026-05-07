import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginOrCreate } from '../api/users';
import Field from '../components/Field';
import Button from '../components/Button';
import ErrorBox from '../components/ErrorBox';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const [createdToast, setCreatedToast] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const { user, created } = await loginOrCreate(email);
      localStorage.setItem('intuitAccountId', user.intuitAccountId);
      if (created) {
        setCreatedToast(true);
        setTimeout(() => navigate('/home'), 1500);
      } else {
        navigate('/home');
      }
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1>Sign in</h1>
      <form onSubmit={handleSubmit}>
        <Field label="Email" name="email" type="email" value={email} onChange={setEmail} required />
        <Button type="submit" disabled={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
      <ErrorBox error={error} />
      {createdToast && (
        <div className="toast">Account created with default role/region. Redirecting…</div>
      )}
      <p className="muted">
        Want to choose your role / region? <Link to="/signup">Sign up</Link>
      </p>
    </div>
  );
}
