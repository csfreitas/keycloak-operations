interface LoadingStateProps {
  message?: string;
  inline?: boolean;
}

export function LoadingState({ message = 'Loading…', inline = false }: LoadingStateProps) {
  if (inline) {
    return (
      <span className="loading-inline" data-testid="loading-state">
        <span className="spinner" aria-hidden="true" />
        <span className="loading-inline__label">{message}</span>
      </span>
    );
  }
  return (
    <div className="loading-page" data-testid="loading-state" role="status">
      <span className="spinner" aria-hidden="true" />
      <p className="loading-page__label">{message}</p>
    </div>
  );
}
