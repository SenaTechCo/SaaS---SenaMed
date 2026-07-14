import { Navigate, Route, Routes } from 'react-router-dom';
import { CadastroPage } from './pages/CadastroPage';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { DoctorsPage } from './pages/DoctorsPage';
import { DoctorAvailabilityPage } from './pages/DoctorAvailabilityPage';
import { DoctorTimeOffPage } from './pages/DoctorTimeOffPage';
import { ClinicCustomizationPage } from './pages/ClinicCustomizationPage';
import { PlanoPage } from './pages/PlanoPage';
import { PublicClinicPage } from './pages/PublicClinicPage';
import { CancelAppointmentPage } from './pages/CancelAppointmentPage';
import { ConfirmAppointmentPage } from './pages/ConfirmAppointmentPage';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { ProtectedRoute } from './components/ProtectedRoute';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/cadastro" element={<CadastroPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/clinica/:slug" element={<PublicClinicPage />} />
      <Route path="/cancelar/:token" element={<CancelAppointmentPage />} />
      <Route path="/confirmar/:token" element={<ConfirmAppointmentPage />} />
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
        path="/dashboard/consultas"
        element={
          <ProtectedRoute>
            <AppointmentsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/plano"
        element={
          <ProtectedRoute>
            <PlanoPage />
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
