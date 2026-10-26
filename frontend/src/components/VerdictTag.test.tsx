import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Verdict } from '@/api/generated/model';
import { VerdictTag } from './VerdictTag';

describe('VerdictTag', () => {
  it.each([
    ['PENDING', 'Pending'],
    ['COMPILING', 'Compiling'],
    ['RUNNING', 'Running'],
  ] as const)('shows %s with an activity indicator', (status, label) => {
    const { container } = render(<VerdictTag status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector('.arco-icon-loading')).not.toBeNull();
  });

  it('shows running progress', () => {
    render(<VerdictTag status="RUNNING" progress="test 3 of 10" />);
    expect(screen.getByText('Running test 3 of 10')).toBeInTheDocument();
  });

  it.each([
    ['AC', 'Accepted', 'green'],
    ['WA', 'Wrong Answer', 'red'],
    ['TLE', 'Time Limit Exceeded', 'orangered'],
    ['MLE', 'Memory Limit Exceeded', 'orangered'],
    ['RE', 'Runtime Error', 'magenta'],
    ['CE', 'Compilation Error', 'gold'],
    ['OLE', 'Output Limit Exceeded', 'orangered'],
    ['SE', 'System Error', 'gray'],
  ] as const)('shows %s as "%s" with colour and icon', (verdict, label, color) => {
    const { container } = render(<VerdictTag status="FINISHED" verdict={verdict as Verdict} />);
    const tag = screen.getByText(label).closest('.arco-tag')!;
    expect(tag).toHaveClass(`arco-tag-${color}`);
    expect(container.querySelector('.arco-tag-icon svg')).not.toBeNull();
  });

  it('shows the abbreviation in short mode', () => {
    render(<VerdictTag status="FINISHED" verdict="WA" short />);
    expect(screen.getByText('WA')).toBeInTheDocument();
  });
});
