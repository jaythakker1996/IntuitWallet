import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

// StrictMode intentionally omitted for the POC: React 18 double-invokes effects
// in dev to surface cleanup bugs, which surfaces as duplicate API calls in the
// network panel and is noisy when demoing. Re-enable later when there's a real
// reason to validate effect cleanup.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
