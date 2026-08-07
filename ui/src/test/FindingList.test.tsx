import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FindingList } from '../components/FindingList';
import { findings, findingCritical, findingHigh } from './fixtures';

describe('FindingList', () => {
  it('renders all findings', () => {
    render(<FindingList findings={findings} />);
    expect(screen.getAllByTestId('finding-item').length).toBe(findings.length);
  });

  it('sorts critical findings before high', () => {
    render(<FindingList findings={[findingHigh, findingCritical]} />);
    const items = screen.getAllByTestId('finding-item');
    // Critical should be first
    expect(items[0]).toHaveTextContent('CRITICAL');
  });

  it('shows finding titles', () => {
    render(<FindingList findings={findings} />);
    expect(screen.getByText(findingCritical.title)).toBeInTheDocument();
    expect(screen.getByText(findingHigh.title)).toBeInTheDocument();
  });

  it('shows empty state when no findings', () => {
    render(<FindingList findings={[]} />);
    expect(screen.getByTestId('empty-state')).toBeInTheDocument();
  });

  it('shows recommendation when present', () => {
    render(<FindingList findings={[findingCritical]} />);
    expect(screen.getByText(findingCritical.recommendation!)).toBeInTheDocument();
  });

  it('respects maxItems limit', () => {
    render(<FindingList findings={findings} maxItems={1} />);
    expect(screen.getAllByTestId('finding-item').length).toBe(1);
    expect(screen.getByText(/\+ 1 more/)).toBeInTheDocument();
  });
});
