import { CheckCircle2, XCircle, Info } from 'lucide-react';

const STYLES = {
  success: { border: 'border-mint', icon: CheckCircle2, iconColor: 'text-mint' },
  error: { border: 'border-danger', icon: XCircle, iconColor: 'text-danger' },
  info: { border: 'border-line', icon: Info, iconColor: 'text-mute' },
};

export default function Toast({ toast, onDismiss }) {
  const style = STYLES[toast.type] || STYLES.info;
  const Icon = style.icon;

  return (
    <div
      className={`flex items-start gap-3 w-80 bg-panel border ${style.border} rounded-md px-4 py-3 shadow-lg shadow-black/40 animate-[fadeIn_0.15s_ease-out]`}
    >
      <Icon size={18} className={`${style.iconColor} shrink-0 mt-0.5`} />
      <p className="text-sm text-ink leading-snug flex-1">{toast.message}</p>
      <button
        onClick={() => onDismiss(toast.id)}
        className="text-mute hover:text-ink transition-colors text-xs"
        aria-label="Dismiss notification"
      >
        ✕
      </button>
    </div>
  );
}