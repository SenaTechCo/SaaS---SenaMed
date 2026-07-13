import { Navigate, Route, Routes } from 'react-router-dom';
import { CadastroPage } from './pages/CadastroPage';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { DoctorsPage } from './pages/DoctorsPage';
import { DoctorAvailabilityPage } from './pages/DoctorAvailabilityPage';
import { DoctorTimeOffPage } from './pages/DoctorTimeOffPage';
import { ClinicCustomizationPage } from './pages/ClinicCustomizationPage';
import { ProtectedRoute } from './components/ProtectedRoute';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/cadastro" element={<CadastroPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/medicos"
        element={
          <ProtectedRoute>
            <DoctorsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/medicos/:id/horarios"
        element={
          <ProtectedRoute>
            <DoctorAvailabilityPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/medicos/:id/folgas"
        element={
          <ProtectedRoute>
            <DoctorTimeOffPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/personalizacao"
        element={
          <ProtectedRoute>
            <ClinicCustomizationPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
