import { ConfigProvider } from '@arco-design/web-react';
import enUS from '@arco-design/web-react/es/locale/en-US';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useApplyTheme } from '@/lib/theme';

function ThemeSync() {
  useApplyTheme();
  return null;
}

/**
 * Providers shared by the app and component tests. Arco defaults to Chinese, so the English
 * locale must be set here for pagination, empty states and pickers.
 */
export function AppProviders({
  queryClient,
  children,
}: {
  queryClient: QueryClient;
  children: ReactNode;
}) {
  return (
    <ConfigProvider locale={enUS}>
      <QueryClientProvider client={queryClient}>
        <ThemeSync />
        {children}
      </QueryClientProvider>
    </ConfigProvider>
  );
}
