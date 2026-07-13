import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './DashboardPage.css';

export function DashboardPage() {
  const navigate = useNavigate();
  const { clinic, user, logout } = useAuth();

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <h1>Bem-vindo, {clinic?.name ?? 'sua clínica'}</h1>
          {user && <p className="dashboard-subtitle">Logado como {user.name} ({user.email})</p>}
        </div>
        <button type="button" className="btn-secondary" onClick={handleLogout}>
          Sair
        </button>
      </header>

      <main className="dashboard-content">
        <p>Esta é a área logada do SenaMed. Em breve: agenda, médicos e disponibilidade.</p>
      </main>
    </div>
  );
}
