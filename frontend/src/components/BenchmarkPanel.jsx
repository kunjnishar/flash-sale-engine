import { useState } from 'react';
import { Play, Loader2 } from 'lucide-react';
import { purchase } from '../services/api';

const STRATEGIES = [
  { id: 'pessimistic', label: 'Pessimistic', color: 'pess', description: 'SELECT ... FOR UPDATE row lock' },
  { id: 'optimistic', label: 'Optimistic', color: 'opt', description: '@Version check with retry' },
  { id: 'distributed', label: 'Redisson Distributed Lock', color: 'dist', description: 'Redis-backed cross-instance lock' },
  { id: 'redis-lua', label: 'Redis + Lua Atomic', color: 'lua', description: 'In-memory atomic check, zero DB reads for rejections' },
];

export default function BenchmarkPanel({ products, onRunComplete, onError }) {
  const [strategy, setStrategy] = useState('optimistic');
  const [productId, setProductId] = useState(products[0]?.id ?? '');
  const [concurrency, setConcurrency] = useState(50);
  const [isRunning, setIsRunning] = useState(false);

  const activeStrategy = STRATEGIES.find((s) => s.id === strategy);

  async function runTest() {
    if (!productId) {
      onError('Select a product to run the concurrency test against.');
      return;
    }

    setIsRunning(true);
    const startedAt = performance.now();

    // A fresh, unpredictable base per run keeps simulated userIds from colliding
    // across separate runs, which matters now that rate limiting is keyed by userId.
    const runBase = Date.now() + Math.floor(Math.random() * 100000);

    const requests = Array.from({ length: concurrency }, (_, index) => {
      const requestStart = performance.now();
      return purchase(strategy, {
        userId: runBase + index,
        productId: Number(productId),
        quantity: 1,
      })
        .then(() => ({ outcome: 'success', latency: performance.now() - requestStart }))
        .catch((err) => {
          const status = err.response?.status;
          const outcome = status === 409 ? 'rejected' : status === 429 ? 'rateLimited' : 'error';
          return { outcome, latency: performance.now() - requestStart };
        });
    });

    const results = await Promise.all(requests);
    const totalDurationMs = performance.now() - startedAt;

    const successfulOrders = results.filter((r) => r.outcome === 'success').length;
    const outOfStockRejections = results.filter((r) => r.outcome === 'rejected').length;
    const rateLimited = results.filter((r) => r.outcome === 'rateLimited').length;
    const otherFailures = results.length - successfulOrders - outOfStockRejections - rateLimited;
    const averageLatencyMs = results.reduce((sum, r) => sum + r.latency, 0) / results.length;

    onRunComplete({
      strategyLabel: activeStrategy.label,
      strategyColor: activeStrategy.color,
      totalRequests: results.length,
      successfulOrders,
      outOfStockRejections,
      rateLimited,
      otherFailures,
      averageLatencyMs,
      totalDurationMs,
    });

    setIsRunning(false);
  }

  return (
    <div className="bg-panel border border-line rounded-lg p-6 flex flex-col gap-6">
      <div>
        <h2 className="font-display font-semibold text-ink text-lg">Concurrency benchmark</h2>
        <p className="text-mute text-sm mt-1">
          Fire many simultaneous purchase requests at one product and compare how each locking
          strategy holds up under contention.
        </p>
      </div>

      <div>
        <label className="text-xs font-mono text-mute block mb-2">Locking strategy</label>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2">
          {STRATEGIES.map((s) => {
            const isActive = strategy === s.id;
            return (
              <button
                key={s.id}
                onClick={() => setStrategy(s.id)}
                className={`text-left rounded-md border px-3 py-2.5 transition-colors ${
                  isActive ? `border-${s.color} bg-${s.color}/10` : 'border-line hover:border-mute'
                }`}
              >
                <span
                  className={`block w-2 h-2 rounded-full mb-2 ${isActive ? `bg-${s.color}` : 'bg-mute'}`}
                />
                <span className="block text-sm font-medium text-ink">{s.label}</span>
                <span className="block text-xs text-mute mt-0.5">{s.description}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="text-xs font-mono text-mute block mb-2">Target product</label>
          <select
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
            className="w-full bg-void border border-line rounded-md px-3 py-2 text-sm text-ink focus:outline-none focus:border-flash"
          >
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.title} ({p.stockQuantity} in stock)
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="text-xs font-mono text-mute block mb-2">
            Concurrent requests: <span className="text-ink">{concurrency}</span>
          </label>
          <input
            type="range"
            min={10}
            max={200}
            step={5}
            value={concurrency}
            onChange={(e) => setConcurrency(Number(e.target.value))}
            className="w-full accent-flash"
          />
          <div className="flex justify-between text-xs font-mono text-mute mt-1">
            <span>10</span>
            <span>200</span>
          </div>
        </div>
      </div>

      <button
        onClick={runTest}
        disabled={isRunning}
        className="w-full flex items-center justify-center gap-2 rounded-md bg-flash text-void font-medium text-sm py-3 hover:bg-flash/90 disabled:bg-line disabled:text-mute transition-colors"
      >
        {isRunning ? (
          <>
            <Loader2 size={16} className="animate-spin" />
            Running {concurrency} requests…
          </>
        ) : (
          <>
            <Play size={16} />
            Run concurrency test
          </>
        )}
      </button>
    </div>
  );
}