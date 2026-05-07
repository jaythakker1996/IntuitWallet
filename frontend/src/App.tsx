import { Routes, Route } from 'react-router-dom';
import Nav from './components/Nav';
import RequireUser from './components/RequireUser';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import HomePage from './pages/HomePage';
import WalletPage from './pages/WalletPage';
import StablecoinPage from './pages/StablecoinPage';
import TransactionsPage from './pages/TransactionsPage';
import TransactionDetailPage from './pages/TransactionDetailPage';
import TransactPage from './pages/TransactPage';
import FundPage from './pages/FundPage';

export default function App() {
  return (
    <>
      <Nav />
      <main>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/home" element={<RequireUser><HomePage /></RequireUser>} />
          <Route path="/wallet" element={<RequireUser><WalletPage /></RequireUser>} />
          <Route path="/wallet/balances/:stablecoin" element={<RequireUser><StablecoinPage /></RequireUser>} />
          <Route path="/wallet/transactions" element={<RequireUser><TransactionsPage /></RequireUser>} />
          <Route path="/wallet/transactions/:txId" element={<RequireUser><TransactionDetailPage /></RequireUser>} />
          <Route path="/transact" element={<RequireUser><TransactPage /></RequireUser>} />
          <Route path="/fund" element={<RequireUser><FundPage /></RequireUser>} />
        </Routes>
      </main>
    </>
  );
}
