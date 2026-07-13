import type { ReactNode } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './DashboardLayout.css';

function navLinkClassName({ isActive }: { isActive: boolean }): string {
  return isActive ? 'active' : '';
}

export function DashboardLayout({ children }: { children: ReactNode }) {
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
          <h1>{clinic?.name ?? 'SenaMed'}</h1>
          {user && (
            <p className="dashboard-subtitle">
              Logado como {user.name} ({user.email})
            </p>
          )}
        </div>
        <button type="button" className="btn-secondary" onClick={handleLogout}>
          Sair
        </button>
      </header>

      <nav className="dashboard-nav">
        <NavLink to="/dashboard" end className={navLinkClassName}>
          Início
        </NavLink>
        <NavLink to="/dashboard/medicos" className={navLinkClassName}>
          Médicos
        </NavLink>
        <NavLink to="/dashboard/personalizacao" className={navLinkClassName}>
          Personalização
        </NavLink>
      </nav>

      <main className="dashboard-content">{children}</main>
    </div>
  );
}
