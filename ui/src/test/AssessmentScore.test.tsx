import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AssessmentScore, isAssessmentScoreAvailable } from '../components/AssessmentScore';

describe('assessment score confidence boundary', () => {
  it.each([
    ['PARTIAL', 100], ['FAILED', 100], ['COMPLETE', 99], ['COMPLETE', null],
    ['COMPLETE', 1], ['PASSED', 100], [null, 100],
  ] as const)('does not infer confidence from %s / %s', (status, completeness) => {
    expect(isAssessmentScoreAvailable(status, completeness, true)).toBe(false);
  });

  it('allows only complete evaluation with known complete coverage', () => {
    expect(isAssessmentScoreAvailable('COMPLETE', 100, true)).toBe(true);
  });

  it.each([undefined, null, false])('does not certify historical scores without a trustworthy marker (%s)', marker => {
    expect(isAssessmentScoreAvailable('COMPLETE', 100, marker)).toBe(false);
  });

  it('shows no numerical score for a missing result', () => {
    render(<AssessmentScore score={null} status={null} completeness={null} />);
    expect(screen.getByText('Not assessed')).toBeInTheDocument();
    expect(screen.queryByTestId('score-bar')).not.toBeInTheDocument();
  });
});
