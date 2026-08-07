import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge } from '../components/StatusBadge';

describe('StatusBadge', () => {
  it('renders healthy status with correct label and class', () => {
    render(<StatusBadge status="HEALTHY" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent('Healthy');
    expect(badge).toHaveClass('badge--healthy');
  });

  it('renders critical status', () => {
    render(<StatusBadge status="CRITICAL" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Critical');
    expect(badge).toHaveClass('badge--critical');
  });

  it('renders warning status', () => {
    render(<StatusBadge status="WARNING" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Warning');
    expect(badge).toHaveClass('badge--warning');
  });

  it('renders unknown status', () => {
    render(<StatusBadge status="UNKNOWN" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Unknown');
    expect(badge).toHaveClass('badge--unknown');
  });

  it('renders assessment PASSED status', () => {
    render(<StatusBadge status="PASSED" />);
    expect(screen.getByTestId('status-badge')).toHaveTextContent('Passed');
  });

  it('renders assessment FAILED status', () => {
    render(<StatusBadge status="FAILED" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Failed');
    expect(badge).toHaveClass('badge--critical');
  });

  it('renders PARTIAL status with warning style', () => {
    render(<StatusBadge status="PARTIAL" />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent('Partial');
    expect(badge).toHaveClass('badge--warning');
  });

  it('applies sm size class', () => {
    render(<StatusBadge status="HEALTHY" size="sm" />);
    expect(screen.getByTestId('status-badge')).toHaveClass('badge--sm');
  });

  it('falls back gracefully for unknown status string', () => {
    render(<StatusBadge status="WEIRD_STATUS" />);
    expect(screen.getByTestId('status-badge')).toBeInTheDocument();
  });
});
