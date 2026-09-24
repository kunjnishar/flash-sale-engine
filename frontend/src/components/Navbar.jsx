import { useEffect, useState } from 'react';
import { Zap, RotateCcw, Loader2 } from 'lucide-react';
import { fetchProducts, resetInventory } from '../services/api';

export default function Navbar({ onResetSuccess, onResetError }) {
  const [isOnline, setIsOnline] = useState(null);
  const [isResetting, setIsResetting] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function checkBackend() {
      try {
        await fetchProducts();
        if (!cancelled) setIsOnline(true);
      } catch (err) {
        if (!cancelled) setIsOnline(false);
      }
    }

    checkBackend();
    const intervalId = setInterval(checkBackend, 5000);

    return () => {
      cancelled = true;
      clearInterval(intervalId);
    };
  }, []);

  async function handleReset() {
    setIsResetting(true);
    try {
      const products = await resetInventory();
      onResetSuccess(products);
    } catch (err) {
      onResetError('Could not reset inventory. Is the backend running?');
    } finally {
      setIsResetting(false);
    }
  }

  const statusLabel = isOnline === null ? 'Checking…' : isOnline ? 'Backend online' : 'Backend offline';
  const statusColor = isOnline === null ? 'bg-mute' : isOnline ? 'bg-mint' : 'bg-danger';

  return (
    <nav className="sticky top-0 z-40 border-b border-line bg-void/90 backdrop-blur">
      <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <Zap size={22} className="text-flash" fill="currentColor" />
          <span className="font-display font-semibold text-lg tracking-tight text-ink">
            Flash Sale Engine
          </span>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleReset}
            disabled={isResetting}
            className="flex items-center gap-1.5 rounded-full border border-line bg-panel px-3 py-1.5 text-xs text-mute hover:text-ink hover:border-mute disabled:opacity-50 transition-colors"
            title="Reset all product stock to default levels and clear any stuck locks"
          >
            {isResetting ? <Loader2 size={13} className="animate-spin" /> : <RotateCcw size={13} />}
            Reset inventory
          </button>

          <div className="flex items-center gap-2 rounded-full border border-line bg-panel px-3 py-1.5">
            <span className={`w-2 h-2 rounded-full ${statusColor} ${isOnline ? 'animate-pulse' : ''}`} />
            <span className="font-mono text-xs text-mute">{statusLabel}</span>
          </div>
        </div>
      </div>
    </nav>
  );
}