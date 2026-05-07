import { Link } from 'react-router-dom';

export default function HomePage() {
  return (
    <div className="page">
      <h1>Welcome</h1>
      <div className="card-grid">
        <Link to="/wallet" className="card">
          <h2>Wallet</h2>
          <p>View balances and history</p>
        </Link>
        <Link to="/transact" className="card">
          <h2>Transact</h2>
          <p>Send to another wallet</p>
        </Link>
        <Link to="/fund" className="card">
          <h2>Fund</h2>
          <p>Add stablecoin to your wallet</p>
        </Link>
      </div>
    </div>
  );
}
