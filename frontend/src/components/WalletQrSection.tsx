import { useEffect, useState } from 'react';
import QRCode from 'react-qr-code';
import { createQr, getQr } from '../api/qr';
import { HttpError } from '../api/client';
import type { QrResponse } from '../types/api';
import Button from './Button';
import ErrorBox from './ErrorBox';

export default function WalletQrSection({ walletId }: { walletId: string }) {
  const [qr, setQr] = useState<QrResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    setLoading(true);
    getQr(walletId)
      .then((q) => setQr(q))
      .catch((e) => {
        if (e instanceof HttpError && e.status === 404 && e.body.error === 'QR_NOT_FOUND') {
          setQr(null);
        } else {
          setError(e);
        }
      })
      .finally(() => setLoading(false));
  }, [walletId]);

  async function handleGenerate() {
    setGenerating(true);
    setError(null);
    try {
      const { qr: created } = await createQr(walletId);
      setQr(created);
    } catch (e) {
      setError(e);
    } finally {
      setGenerating(false);
    }
  }

  return (
    <section className="qr-section">
      <h2>Your QR code</h2>
      {loading && <p className="muted">Loading…</p>}
      <ErrorBox error={error} />
      {!loading && !qr && !error && (
        <div className="qr-empty">
          <p className="muted">
            Generate a QR code that anyone can scan to send you stablecoin.
          </p>
          <Button onClick={handleGenerate} disabled={generating}>
            {generating ? 'Generating…' : 'Generate QR'}
          </Button>
        </div>
      )}
      {qr && (
        <div className="qr-display">
          <div className="qr-image">
            <QRCode value={qr.payload} size={192} />
          </div>
          <p className="qr-payload" title={qr.payload}>
            {qr.payload}
          </p>
        </div>
      )}
    </section>
  );
}
