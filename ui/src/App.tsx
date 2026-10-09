import { RouterProvider } from 'react-router-dom';
import { useState } from 'react';
import { AuthProvider } from './auth/AuthProvider';
import { createAppRouter } from './routes';
import './styles/global.css';

export function App() {
  return (
    <AuthProvider>
      <AuthenticatedRoutes />
    </AuthProvider>
  );
}

function AuthenticatedRoutes() {
  const [router] = useState(createAppRouter);
  return <RouterProvider router={router} />;
}
