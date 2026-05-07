import { HttpError } from '../api/client';

export default function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  if (error instanceof HttpError) {
    return (
      <div className="error">
        <strong>{error.body.error ?? `HTTP ${error.status}`}</strong>
        {error.body.message && <span>: {error.body.message}</span>}
      </div>
    );
  }
  if (error instanceof Error) {
    return <div className="error">{error.message}</div>;
  }
  return <div className="error">{String(error)}</div>;
}
