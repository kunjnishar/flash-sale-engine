import { Activity, CheckCircle2, XCircle, Timer, ShieldAlert } from 'lucide-react';

function MetricCard({ icon: Icon, label, value, accentClass }) {
  return (
    <div className="bg-panel border border-line rounded-lg p-5 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-xs font-mono text-mute">{label}</span>
        <Icon size={16} className={accentClass} />
      </div>
      <span className="font-mono text-2xl text-ink">{value}</span>
    </div>
  );
}

export default function MetricsDashboard({ metrics }) {
  if (!metrics) {
    return (
      <div className="bg-panel border border-line rounded-lg p-8 text-center">
        <p className="text-mute text-sm">
          Run a concurrency test above to see live results here.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2">
        <span className={`w-2 h-2 rounded-full bg-${metrics.strategyColor}`} />
        <h3 className="font-display font-semibold text-ink text-sm">
          Last run: {metrics.strategyLabel} — {metrics.totalDurationMs.toFixed(0)}ms total
        </h3>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        <MetricCard
          icon={Activity}
          label="Total requests"
          value={metrics.totalRequests}
          accentClass="text-ink"
        />
        <MetricCard
          icon={CheckCircle2}
          label="Successful orders"
          value={metrics.successfulOrders}
          accentClass="text-mint"
        />
        <MetricCard
          icon={XCircle}
          label="Out of stock"
          value={metrics.outOfStockRejections}
          accentClass="text-opt"
        />
        <MetricCard
          icon={ShieldAlert}
          label="Rate limited (429)"
          value={metrics.rateLimited}
          accentClass="text-limit"
        />
        <MetricCard
          icon={Timer}
          label="Avg latency"
          value={`${metrics.averageLatencyMs.toFixed(1)}ms`}
          accentClass="text-flash"
        />
      </div>

      {metrics.otherFailures > 0 && (
        <p className="text-xs font-mono text-danger">
          {metrics.otherFailures} request(s) failed with an unexpected error — check the backend logs.
        </p>
      )}
    </div>
  );
}