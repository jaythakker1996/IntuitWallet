import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';

export default function RequireUser({ children }: { children: ReactNode }) {
  const id = localStorage.getItem('intuitAccountId');
  if (!id) return <Navigate to="/" replace />;
  return <>{children}</>;
}
