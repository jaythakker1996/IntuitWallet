import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { createUser } from '../api/users';
import Field from '../components/Field';
import Button from '../components/Button';
import ErrorBox from '../components/ErrorBox';

export default function SignupPage() {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('CONSUMER');
  const [homeRegion, setHomeRegion] = useState('us-east-1');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const { user } = await createUser(email, role, homeRegion);
      localStorage.setItem('intuitAccountId', user.intuitAccountId);
      navigate('/home');
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-narrow">
      <h1>Sign up</h1>
      <form onSubmit={handleSubmit}>
        <Field
          label="Email"
          name="email"
          type="email"
          value={email}
          onChange={setEmail}
          required
        />
        <Field
          label="Role"
          name="role"
          type="select"
          value={role}
          onChange={setRole}
          options={[
            { value: 'CONSUMER', label: 'Consumer' },
            { value: 'MERCHANT', label: 'Merchant' },
          ]}
        />
        <Field
          label="Home region"
          name="homeRegion"
          type="select"
          value={homeRegion}
          onChange={setHomeRegion}
          options={[
            { value: 'us-east-1', label: 'us-east-1' },
            { value: 'us-west-2', label: 'us-west-2' },
          ]}
        />
        <Button type="submit" disabled={loading}>
          {loading ? 'Creating…' : 'Create account'}
        </Button>
      </form>
      <ErrorBox error={error} />
    </div>
  );
}
