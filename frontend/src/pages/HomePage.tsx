import { Link } from 'react-router-dom';

export default function HomePage() {
  return (
    <div className="page">
      <h1>Welcome</h1>
      <p className="muted">What would you like to do?</p>
      <div className="card-grid">
        <Link to="/wallet" className="card">
          <span className="card-icon">👛</span>
          <h2>Wallet</h2>
          <p>View balances and transaction history</p>
        </Link>
        <Link to="/transact" className="card">
          <span className="card-icon">↗</span>
          <h2>Transact</h2>
          <p>Send to another wallet</p>
        </Link>
        <Link to="/fund" className="card">
          <span className="card-icon">＋</span>
          <h2>Fund</h2>
          <p>Add stablecoin to your wallet</p>
        </Link>
      </div>
    </div>
  );
}
