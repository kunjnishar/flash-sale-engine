import { useEffect, useState } from 'react';
import { Smartphone, Headphones, Package, Zap } from 'lucide-react';

const WINDOW_MS = 45 * 60 * 1000;

function getRemainingMs() {
  return WINDOW_MS - (Date.now() % WINDOW_MS);
}

function formatCountdown(ms) {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function pickIcon(title) {
  const lower = title.toLowerCase();
  if (lower.includes('phone')) return Smartphone;
  if (lower.includes('ear') || lower.includes('audio') || lower.includes('headphone')) return Headphones;
  return Package;
}

function stockTone(stock) {
  if (stock <= 0) return { text: 'text-danger', bg: 'bg-danger/10', border: 'border-danger/30' };
  if (stock < 5) return { text: 'text-danger', bg: 'bg-danger/10', border: 'border-danger/30' };
  if (stock < 20) return { text: 'text-opt', bg: 'bg-opt/10', border: 'border-opt/30' };
  return { text: 'text-mint', bg: 'bg-mint/10', border: 'border-mint/30' };
}

export default function ProductCard({ product, onPurchaseSuccess, onPurchaseError }) {
  const [remainingMs, setRemainingMs] = useState(getRemainingMs());
  const [isBuying, setIsBuying] = useState(false);

  useEffect(() => {
    const intervalId = setInterval(() => setRemainingMs(getRemainingMs()), 1000);
    return () => clearInterval(intervalId);
  }, []);

  const Icon = pickIcon(product.title);
  const tone = stockTone(product.stockQuantity);
  const isSoldOut = product.stockQuantity <= 0;

  async function handleBuyNow() {
    setIsBuying(true);
    try {
      const order = await onPurchaseSuccess(product.id);
      return order;
    } finally {
      setIsBuying(false);
    }
  }

  return (
    <div className="bg-panel border border-line rounded-lg p-5 flex flex-col gap-4">
      <div className="flex items-start justify-between">
        <div className="w-11 h-11 rounded-md bg-void border border-line flex items-center justify-center">
          <Icon size={20} className="text-flash" />
        </div>
        <span className={`px-2 py-0.5 rounded text-xs font-mono border ${tone.bg} ${tone.text} ${tone.border}`}>
          {isSoldOut ? 'sold out' : `${product.stockQuantity} left`}
        </span>
      </div>

      <div>
        <h3 className="font-display font-semibold text-ink text-base">{product.title}</h3>
        <p className="text-mute text-sm mt-1 leading-snug">{product.description}</p>
      </div>

      <div className="flex items-end justify-between mt-auto pt-2 border-t border-line">
        <div>
          <p className="font-mono text-xl text-ink">${Number(product.price).toFixed(2)}</p>
          <p className="font-mono text-xs text-mute mt-1 flex items-center gap-1">
            <Zap size={11} className="text-flash" />
            resets in {formatCountdown(remainingMs)}
          </p>
        </div>

        <button
          onClick={handleBuyNow}
          disabled={isSoldOut || isBuying}
          className="px-4 py-2 rounded-md bg-flash text-void font-medium text-sm hover:bg-flash/90 disabled:bg-line disabled:text-mute disabled:cursor-not-allowed transition-colors"
        >
          {isBuying ? 'Placing…' : isSoldOut ? 'Sold out' : 'Buy now'}
        </button>
      </div>
    </div>
  );
}