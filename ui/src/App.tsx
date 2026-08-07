import { RouterProvider } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { router } from './routes';
import './styles/global.css';

export function App() {
  return (
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  );
}
