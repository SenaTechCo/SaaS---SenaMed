import { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Calendar,
  CalendarOff,
  ChevronUp,
  Clock,
  CreditCard,
  LayoutDashboard,
  Palette,
  RefreshCw,
  Settings,
  Stethoscope,
  UserRound,
  Users,
  Wallet,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import type { Permission, UserRole } from '../types/auth';

interface SidebarProps {
  expanded: boolean;
  mobileOpen: boolean;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onMobileClose: () => void;
}

interface NavItem {
  to: string;
  end?: boolean;
  icon: typeof LayoutDashboard;
  label: string;
  permission?: Permission;
  roles?: UserRole[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', end: true, icon: LayoutDashboard, label: 'Início' },
  { to: '/dashboard/usuarios', icon: Users, label: 'Usuários', permission: 'MANAGE_USERS' },
  { to: '/dashboard/pacientes', icon: UserRound, label: 'Pacientes', permission: 'MANAGE_PATIENTS' },
  { to: '/dashboard/agendamentos', icon: Calendar, label: 'Agendamentos', permission: 'MANAGE_APPOINTMENTS' },
  { to: '/dashboard/financeiro', icon: Wallet, label: 'Financeiro', permission: 'MANAGE_FINANCE' },
  { to: '/dashboard/plano', icon: CreditCard, label: 'Plano', roles: ['ADMIN'] },
  { to: '/dashboard/personalizacao', icon: Palette, label: 'Personalização', roles: ['ADMIN'] },
  { to: '/dashboard/minha-agenda', icon: Calendar, label: 'Minha Agenda', roles: ['DOCTOR'] },
  { to: '/dashboard/minha-disponibilidade', icon: Clock, label: 'Minha Disponibilidade', roles: ['DOCTOR'] },
  { to: '/dashboard/minhas-folgas', icon: CalendarOff, label: 'Minhas Folgas', roles: ['DOCTOR'] },
  { to: '/dashboard/google-calendar', icon: RefreshCw, label: 'Google Calendar', roles: ['DOCTOR'] },
  { to: '/dashboard/configuracoes', icon: Settings, label: 'Configurações' },
];

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Administrador',
  DOCTOR: 'Médico',
  STAFF: 'Equipe',
};

function isNavItemVisible(item: NavItem, role: UserRole, permissions: Permission[]): boolean {
  if (item.roles) {
    return item.roles.includes(role);
  }
  if (item.permission) {
    return role === 'ADMIN' || permissions.includes(item.permission);
  }
  return true;
}

export function Sidebar({ expanded, mobileOpen, onMouseEnter, onMouseLeave, onMobileClose }: SidebarProps) {
  const navigate = useNavigate();
  const { clinic, user, logout } = useAuth();
  const [showUserMenu, setShowUserMenu] = useState(false);

  const navItems = user ? NAV_ITEMS.filter((item) => isNavItemVisible(item, user.role, user.permissions)) : [];

  function handleLogout() {
    setShowUserMenu(false);
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <aside
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      className={`
        fixed left-0 top-0 h-screen z-30
        bg-gradient-to-b from-[#0f172a] to-[#1e293b] text-white flex flex-col
        transition-all duration-300 ease-in-out overflow-hidden
        w-64
        ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}
        lg:translate-x-0
        ${expanded ? 'lg:w-64' : 'lg:w-16'}
      `}
    >
      <div className="h-[73px] flex items-center gap-3 px-4 border-b border-white/10 flex-shrink-0">
        <div className="w-9 h-9 bg-primary-500 rounded-lg flex items-center justify-center flex-shrink-0 shadow-lg shadow-primary-500/30">
          <Stethoscope className="w-5 h-5 text-white" />
        </div>
        <span
          className={`font-semibold text-[15px] whitespace-nowrap overflow-hidden transition-all duration-300 ${expanded ? 'lg:opacity-100' : 'lg:opacity-0'}`}
        >
          SenaMed
        </span>
      </div>

      <nav className="flex-1 overflow-y-auto py-3">
        {navItems.map(({ to, end, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onMobileClose}
            className={({ isActive }) =>
              `relative flex items-center h-11 rounded-lg mx-2 mb-1 transition-all duration-150
              px-3 gap-3 lg:justify-start
              ${expanded ? '' : 'lg:justify-center lg:px-0 lg:gap-0'}
              ${isActive ? 'bg-white/10 text-white' : 'text-slate-400 hover:text-white hover:bg-white/[0.08]'}`
            }
          >
            {({ isActive }) => (
              <>
                {isActive && <span className="absolute left-0 top-2 bottom-2 w-[3px] bg-primary-400 rounded-full" />}
                <Icon className="w-5 h-5 flex-shrink-0" />
                <span
                  className={`text-[13px] font-medium whitespace-nowrap overflow-hidden transition-all duration-300 ${expanded ? '' : 'lg:hidden'}`}
                >
                  {label}
                </span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="relative border-t border-white/10 p-2 flex-shrink-0">
        {showUserMenu && (
          <div className="absolute bottom-full left-2 right-2 mb-2 bg-white rounded-2xl shadow-2xl overflow-hidden text-slate-900 animate-fade-in">
            <div className="px-4 py-3 border-b border-slate-100">
              <p className="text-sm font-semibold text-slate-900 truncate">{user?.name}</p>
              <p className="text-xs text-slate-500 truncate">{user?.email}</p>
              <span className="inline-block mt-1.5 px-1.5 py-0.5 bg-primary-50 text-primary-700 text-[10px] font-semibold rounded-full capitalize">
                {user ? ROLE_LABELS[user.role] : ''}
              </span>
            </div>
            <div className="p-2">
              <button
                type="button"
                onClick={handleLogout}
                className="w-full py-2 bg-red-50 text-red-600 text-sm font-semibold rounded-xl hover:bg-red-100 transition-colors"
              >
                Sair
              </button>
            </div>
          </div>
        )}

        <button
          type="button"
          onClick={() => setShowUserMenu((v) => !v)}
          className={`flex items-center w-full rounded-xl hover:bg-white/[0.08] transition-all duration-150 p-2 gap-3 ${showUserMenu ? 'bg-white/[0.08]' : ''}`}
        >
          <div className="w-8 h-8 bg-gradient-to-br from-primary-400 to-primary-600 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0">
            {user?.name?.charAt(0).toUpperCase() ?? 'U'}
          </div>
          <div className={`overflow-hidden whitespace-nowrap text-left flex-1 ${expanded ? '' : 'lg:hidden'}`}>
            <p className="text-sm font-medium text-white truncate">{user?.name ?? clinic?.name}</p>
            <p className="text-xs text-slate-400 capitalize truncate">
              {user ? ROLE_LABELS[user.role] : ''}
            </p>
          </div>
          <ChevronUp
            className={`w-4 h-4 text-slate-400 flex-shrink-0 transition-transform duration-200 ${showUserMenu ? '' : 'rotate-180'} ${expanded ? '' : 'lg:hidden'}`}
          />
        </button>
      </div>
    </aside>
  );
}
