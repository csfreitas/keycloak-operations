import { createBrowserRouter, Navigate, useParams } from 'react-router-dom';
import { AppLayout } from '../layouts/AppLayout';
import { TargetLayout } from '../layouts/TargetLayout';
import { FleetPage } from '../pages/FleetPage';
import { TargetOverviewPage } from '../pages/TargetOverviewPage';
import { HealthPage } from '../pages/HealthPage';
import { AssessmentPage } from '../pages/AssessmentPage';
import { PerformancePage } from '../pages/PerformancePage';
import { InfrastructurePage } from '../pages/InfrastructurePage';
import { HistoryPage } from '../pages/HistoryPage';

/** Forces a full remount of TargetLayout when targetId changes. */
function TargetLayoutWrapper() {
  const { targetId } = useParams<{ targetId: string }>();
  return <TargetLayout key={targetId} />;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to="/targets" replace /> },
      { path: 'targets', element: <FleetPage /> },
      {
        path: 'targets/:targetId',
        // Remount layout when targetId changes (prevents Target A state on Target B)
        element: <TargetLayoutWrapper />,
        children: [
          { index: true, element: <TargetOverviewPage /> },
          { path: 'health', element: <HealthPage /> },
          { path: 'assessment', element: <AssessmentPage /> },
          { path: 'performance', element: <PerformancePage /> },
          { path: 'infrastructure', element: <InfrastructurePage /> },
          { path: 'history', element: <HistoryPage /> },
        ],
      },
    ],
  },
]);
