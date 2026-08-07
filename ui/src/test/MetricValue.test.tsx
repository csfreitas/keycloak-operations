import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MetricValue } from '../components/MetricValue';

describe('MetricValue', () => {
  it('renders numeric value when available', () => {
    render(<MetricValue value={245.3} availability="AVAILABLE" unit="req/s" />);
    const el = screen.getByTestId('metric-value');
    expect(el).toHaveTextContent('245.30');
    expect(el).toHaveTextContent('req/s');
    expect(el).toHaveClass('metric-value--available');
  });

  it('renders integer value without decimals', () => {
    render(<MetricValue value={100} availability="AVAILABLE" />);
    expect(screen.getByTestId('metric-value')).toHaveTextContent('100');
  });

  it('NEVER shows 0 for NOT_AVAILABLE metric', () => {
    render(<MetricValue value={null} availability="NOT_AVAILABLE" />);
    const el = screen.getByTestId('metric-value');
    expect(el).not.toHaveTextContent('0');
    expect(el).toHaveTextContent('N/A');
    expect(el).toHaveClass('metric-value--unavailable');
  });

  it('shows Stale label for STALE availability', () => {
    render(<MetricValue value={null} availability="STALE" />);
    const el = screen.getByTestId('metric-value');
    expect(el).toHaveTextContent('Stale');
    expect(el).not.toHaveTextContent('0');
    expect(el).toHaveClass('metric-value--stale');
  });

  it('shows Unknown for UNKNOWN availability', () => {
    render(<MetricValue value={null} availability="UNKNOWN" />);
    expect(screen.getByTestId('metric-value')).toHaveTextContent('Unknown');
  });

  it('shows N/A when value is null even if availability says AVAILABLE', () => {
    render(<MetricValue value={null} availability="AVAILABLE" />);
    const el = screen.getByTestId('metric-value');
    expect(el).not.toHaveTextContent('0');
  });

  it('shows Partial for PARTIAL availability', () => {
    render(<MetricValue value={null} availability="PARTIAL" />);
    expect(screen.getByTestId('metric-value')).toHaveTextContent('Partial');
  });

  it('respects precision parameter', () => {
    render(<MetricValue value={3.14159} availability="AVAILABLE" precision={4} />);
    expect(screen.getByTestId('metric-value')).toHaveTextContent('3.1416');
  });
});
