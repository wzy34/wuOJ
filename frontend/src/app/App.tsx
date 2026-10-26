import '@arco-design/web-react/dist/css/arco.css';
import '@/styles/global.css';
import { RouterProvider } from 'react-router/dom';
import { useState } from 'react';
import { createQueryClient } from '@/lib/queryClient';
import { AppProviders } from './AppProviders';
import { createAppRouter } from './router';

export function App() {
  const [queryClient] = useState(() => createQueryClient());
  const [router] = useState(createAppRouter);
  return (
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>
  );
}
