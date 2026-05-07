import { Link, useLocation, useNavigate } from 'react-router-dom';

export default function Nav() {
  const location = useLocation();
  const navigate = useNavigate();
  if (location.pathname === '/' || location.pathname === '/signup') return null;

  function logout() {
    localStorage.removeItem('intuitAccountId');
    localStorage.removeItem('walletId');
    navigate('/');
  }

  return (
    <nav className="nav">
      <Link to="/home" className="nav-brand">Intuit Wallet</Link>
      <button className="nav-logout" onClick={logout}>Log out</button>
    </nav>
  );
}
