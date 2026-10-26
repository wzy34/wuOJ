import { NotFoundState } from '@/components/PageState';
import { usePageTitle } from '@/lib/usePageTitle';

export function NotFoundPage() {
  usePageTitle('Page not found');
  return <NotFoundState />;
}
