import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './DashboardLayout.css';

function navLinkClassName({ isActive }: { isActive: boolean }): string {
  return isActive ? 'active' : '';
}

export function DashboardLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const { clinic, user, logout, refreshClinic } = useAuth();

  useEffect(() => {
    refreshClinic().catch(() => {
      // Best-effort refresh - the stale-but-present clinic from login is still usable if this fails.
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  function renderStatusBanner() {
    if (!clinic || clinic.status === 'ACTIVE') return null;

    if (clinic.status === 'TRIAL') {
      const daysLeft = clinic.trialEndsAt
        ? Math.max(0, Math.ceil((new Date(clinic.trialEndsAt).getTime() - Date.now()) / (24 * 60 * 60 * 1000)))
        : null;
      return (
        <div className="dashboard-banner dashboard-banner-info">
          Você está no período de teste{daysLeft !== null ? ` — restam ${daysLeft} dia(s)` : ''}.{' '}
          <Link to="/dashboard/plano">Ver planos</Link>
        </div>
      );
    }

    const label = clinic.status === 'PAST_DUE' ? 'com pagamento atrasado' : 'bloqueada';
    return (
      <div className="dashboard-banner dashboard-banner-warning">
        Sua assinatura está {label}. <Link to="/dashboard/plano">Regularizar agora</Link>
      </div>
    );
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

      {renderStatusBanner()}

      <nav className="dashboard-nav">
        <NavLink to="/dashboard" end className={navLinkClassName}>
          Início
        </NavLink>
        {user?.role === 'ADMIN' && (
          <>
            <NavLink to="/dashboard/medicos" className={navLinkClassName}>
              Médicos
            </NavLink>
            <NavLink to="/dashboard/consultas" className={navLinkClassName}>
              Consultas
            </NavLink>
            <NavLink to="/dashboard/plano" className={navLinkClassName}>
              Plano
            </NavLink>
            <NavLink to="/dashboard/personalizacao" className={navLinkClassName}>
              Personalização
            </NavLink>
          </>
        )}
        {user?.role === 'DOCTOR' && (
          <>
            <NavLink to="/dashboard/minha-agenda" className={navLinkClassName}>
              Minha Agenda
            </NavLink>
            <NavLink to="/dashboard/minha-disponibilidade" className={navLinkClassName}>
              Minha Disponibilidade
            </NavLink>
            <NavLink to="/dashboard/minhas-folgas" className={navLinkClassName}>
              Minhas Folgas
            </NavLink>
            <NavLink to="/dashboard/google-calendar" className={navLinkClassName}>
              Google Calendar
            </NavLink>
          </>
        )}
      </nav>

      <main className="dashboard-content">{children}</main>
    </div>
  );
}
