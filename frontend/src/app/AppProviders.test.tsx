import { Table } from '@arco-design/web-react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { createQueryClient } from '@/lib/queryClient';
import { AppProviders } from './AppProviders';

describe('AppProviders', () => {
  it('renders Arco components in English', () => {
    const data = Array.from({ length: 30 }, (_, i) => ({ key: i, name: `Row ${i}` }));
    render(
      <AppProviders queryClient={createQueryClient()}>
        <Table
          columns={[{ title: 'Name', dataIndex: 'name' }]}
          data={data}
          pagination={{ pageSize: 10, showTotal: true }}
        />
      </AppProviders>,
    );
    expect(screen.getByText('Total: 30')).toBeInTheDocument();
    expect(screen.queryByText(/共/)).not.toBeInTheDocument();
  });
});
