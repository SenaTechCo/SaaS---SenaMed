import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Menu, Stethoscope } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { Sidebar } from './Sidebar';

export function DashboardLayout({ children }: { children: ReactNode }) {
  const { clinic, refreshClinic } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    refreshClinic().catch(() => {
      // Best-effort refresh - the stale-but-present clinic from login is still usable if this fails.
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function renderStatusBanner() {
    if (!clinic || clinic.status === 'ACTIVE') return null;

    if (clinic.status === 'TRIAL') {
      const daysLeft = clinic.trialEndsAt
        ? Math.max(0, Math.ceil((new Date(clinic.trialEndsAt).getTime() - Date.now()) / (24 * 60 * 60 * 1000)))
        : null;
      return (
        <div className="px-4 md:px-6 py-2.5 text-sm font-medium bg-primary-50 text-primary-700 border-b border-primary-100">
          Você está no período de teste{daysLeft !== null ? ` — restam ${daysLeft} dia(s)` : ''}.{' '}
          <Link to="/dashboard/plano" className="font-semibold underline">
            Ver planos
          </Link>
        </div>
      );
    }

    const label = clinic.status === 'PAST_DUE' ? 'com pagamento atrasado' : 'bloqueada';
    return (
      <div className="px-4 md:px-6 py-2.5 text-sm font-medium bg-red-50 text-red-700 border-b border-red-100">
        Sua assinatura está {label}.{' '}
        <Link to="/dashboard/plano" className="font-semibold underline">
          Regularizar agora
        </Link>
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-slate-50">
      {mobileOpen && (
        <div className="fixed inset-0 bg-black/50 z-20 lg:hidden" onClick={() => setMobileOpen(false)} />
      )}
      <Sidebar
        expanded={expanded}
        mobileOpen={mobileOpen}
        onMouseEnter={() => setExpanded(true)}
        onMouseLeave={() => setExpanded(false)}
        onMobileClose={() => setMobileOpen(false)}
      />
      <main
        className={`flex-1 flex flex-col overflow-hidden transition-all duration-300 ease-in-out ${expanded ? 'lg:ml-64' : 'lg:ml-16'}`}
      >
        <div className="flex items-center gap-3 px-4 py-3 bg-white border-b border-slate-200/80 shadow-sm flex-shrink-0">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="p-2 rounded-lg text-gray-600 hover:bg-gray-100 transition-colors lg:hidden"
          >
            <Menu className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-2 lg:hidden">
            <div className="w-7 h-7 bg-primary-600 rounded-lg flex items-center justify-center">
              <Stethoscope className="w-4 h-4 text-white" />
            </div>
            <span className="font-semibold text-slate-900 text-[15px]">SenaMed</span>
          </div>
          <div className="hidden lg:block">
            <h1 className="text-lg font-semibold text-slate-900">{clinic?.name ?? 'SenaMed'}</h1>
          </div>
        </div>

        {renderStatusBanner()}

        <div className="flex-1 overflow-auto p-4 md:p-6 lg:p-8">{children}</div>
      </main>
    </div>
  );
}
