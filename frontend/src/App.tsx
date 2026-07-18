import { Navigate, Route, Routes } from 'react-router-dom';
import { CadastroPage } from './pages/CadastroPage';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { DoctorsPage } from './pages/DoctorsPage';
import { PatientsPage } from './pages/PatientsPage';
import { DoctorAvailabilityPage } from './pages/DoctorAvailabilityPage';
import { DoctorTimeOffPage } from './pages/DoctorTimeOffPage';
import { ClinicCustomizationPage } from './pages/ClinicCustomizationPage';
import { PlanoPage } from './pages/PlanoPage';
import { PublicClinicPage } from './pages/PublicClinicPage';
import { CancelAppointmentPage } from './pages/CancelAppointmentPage';
import { ConfirmAppointmentPage } from './pages/ConfirmAppointmentPage';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { MyAgendaPage } from './pages/MyAgendaPage';
import { MyAvailabilityPage } from './pages/MyAvailabilityPage';
import { MyTimeOffPage } from './pages/MyTimeOffPage';
import { GoogleCalendarPage } from './pages/GoogleCalendarPage';
import { ConfiguracoesPage } from './pages/ConfiguracoesPage';
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
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <DoctorsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/medicos/:id/horarios"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <DoctorAvailabilityPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/medicos/:id/folgas"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <DoctorTimeOffPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/pacientes"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <PatientsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/agendamentos"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <AppointmentsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/plano"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <PlanoPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/personalizacao"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <ClinicCustomizationPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/minha-agenda"
        element={
          <ProtectedRoute allowedRoles={['DOCTOR']}>
            <MyAgendaPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/minha-disponibilidade"
        element={
          <ProtectedRoute allowedRoles={['DOCTOR']}>
            <MyAvailabilityPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/minhas-folgas"
        element={
          <ProtectedRoute allowedRoles={['DOCTOR']}>
            <MyTimeOffPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/google-calendar"
        element={
          <ProtectedRoute allowedRoles={['DOCTOR']}>
            <GoogleCalendarPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/dashboard/configuracoes"
        element={
          <ProtectedRoute>
            <ConfiguracoesPage />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
