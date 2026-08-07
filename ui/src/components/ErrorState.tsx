interface ErrorStateProps {
  error: string | Error | unknown;
  onRetry?: () => void;
  title?: string;
}

function extractMessage(error: unknown): string {
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message;
  return 'An unexpected error occurred.';
}

export function ErrorState({ error, onRetry, title = 'Failed to load' }: ErrorStateProps) {
  return (
    <div className="error-state" data-testid="error-state" role="alert">
      <div className="error-state__icon" aria-hidden="true">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
      </div>
      <h3 className="error-state__title">{title}</h3>
      <p className="error-state__message">{extractMessage(error)}</p>
      {onRetry && (
        <button className="btn btn--secondary" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
