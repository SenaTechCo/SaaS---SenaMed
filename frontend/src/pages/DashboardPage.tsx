import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  CalendarCheck,
  CalendarDays,
  CalendarX,
  ClipboardList,
  CreditCard,
  Plus,
  TrendingUp,
  UserX,
  Users,
  Wallet,
  X,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import {
  Area,
  AreaChart,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { DashboardLayout } from '../components/DashboardLayout';
import { useAuth } from '../context/AuthContext';
import { apiFetch, ApiError } from '../lib/http';
import type { Appointment } from '../types/appointment';
import type { DashboardReports, DashboardSummary } from '../types/dashboard';
import type { Doctor } from '../types/doctor';
import type { Patient } from '../types/patient';

const CLINIC_STATUS_LABELS: Record<string, string> = {
  TRIAL: 'Em teste',
  ACTIVE: 'Ativo',
  PAST_DUE: 'Pagamento atrasado',
  BLOCKED: 'Bloqueada',
};

const currencyFormatter = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

const STATUS_DOT: Record<Appointment['status'], string> = {
  CONFIRMED: 'bg-green-500',
  ATTENDED: 'bg-blue-500',
  NO_SHOW: 'bg-red-500',
  CANCELLED: 'bg-slate-400',
};

const STATUS_BADGE: Record<Appointment['status'], string> = {
  CONFIRMED: 'bg-green-50 text-green-700',
  ATTENDED: 'bg-blue-50 text-blue-700',
  NO_SHOW: 'bg-red-50 text-red-700',
  CANCELLED: 'bg-slate-100 text-slate-500',
};

const STATUS_LABEL: Record<Appointment['status'], string> = {
  CONFIRMED: 'Confirmado',
  ATTENDED: 'Atendido',
  NO_SHOW: 'Faltou',
  CANCELLED: 'Cancelado',
};

const STATUS_LEGEND_ORDER: Appointment['status'][] = ['CONFIRMED', 'ATTENDED', 'CANCELLED', 'NO_SHOW'];

const PIE_COLORS = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#9333ea', '#64748b'];

const TASKS_STORAGE_KEY = 'senamed.dashboard.tasks';

const CARD_CLASSES = 'bg-white rounded-2xl border border-slate-100 shadow-[0_1px_3px_rgba(0,0,0,0.06),0_1px_2px_rgba(0,0,0,0.06)]';

function todayAsInputValue(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDayMonth(dateStr: string): string {
  const parts = dateStr.split('-');
  if (parts.length !== 3) return dateStr;
  return `${parts[2]}/${parts[1]}`;
}

function computeDurationLabel(appointment: Appointment): string {
  const [startHours, startMinutes] = appointment.startTime.split(':').map(Number);
  const [endHours, endMinutes] = appointment.endTime.split(':').map(Number);
  const minutes = endHours * 60 + endMinutes - (startHours * 60 + startMinutes);
  if (minutes <= 0) return '';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours}h` : `${hours}h${rest}min`;
}

interface Task {
  id: string;
  text: string;
  done: boolean;
}

interface Distribution {
  name: string;
  value: number;
}

function buildDistribution(patients: Patient[], getKey: (patient: Patient) => string | null): Distribution[] {
  const counts = new Map<string, number>();
  patients.forEach((patient) => {
    const raw = getKey(patient);
    const key = raw && raw.trim() ? raw : 'Não informado';
    counts.set(key, (counts.get(key) ?? 0) + 1);
  });
  return Array.from(counts.entries()).map(([name, value]) => ({ name, value }));
}

function computeAge(birthDate: string): number {
  const [year, month, day] = birthDate.split('-').map(Number);
  const today = new Date();
  let age = today.getFullYear() - year;
  const hadBirthdayThisYear =
    today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day);
  if (!hadBirthdayThisYear) age -= 1;
  return age;
}

function buildAgeDistribution(patients: Patient[]): Distribution[] {
  const buckets: Record<string, number> = { Infantil: 0, Adolescente: 0, Adulto: 0, Idoso: 0 };
  patients.forEach((patient) => {
    if (!patient.birthDate) return;
    const age = computeAge(patient.birthDate);
    if (age < 12) buckets.Infantil += 1;
    else if (age < 18) buckets.Adolescente += 1;
    else if (age < 60) buckets.Adulto += 1;
    else buckets.Idoso += 1;
  });
  return Object.entries(buckets)
    .filter(([, value]) => value > 0)
    .map(([name, value]) => ({ name, value }));
}

export function DashboardPage() {
  const { user, clinic } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSlow, setIsSlow] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const slowTimer = setTimeout(() => {
      if (!cancelled) setIsSlow(true);
    }, 8000);

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      setIsSlow(false);
      try {
        const data = await apiFetch<DashboardSummary>('/api/dashboard/summary');
        if (cancelled) return;
        setSummary(data);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o resumo da clínica.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
      clearTimeout(slowTimer);
    };
  }, []);

  const isDoctor = user?.role === 'DOCTOR';
  const showAdminReports = !!summary && summary.pendingReceivablesTotal !== null;

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Início</h1>
          <p className="text-sm text-slate-500 mt-1">
            {isDoctor ? 'Resumo da sua agenda.' : 'Resumo da clínica.'}
          </p>
        </div>
      </div>

      {isLoading && (
        <div className="flex flex-col items-center gap-3 py-16">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
          {isSlow && (
            <p className="text-sm text-slate-500">
              Isso está demorando mais que o esperado — o servidor pode estar iniciando, aguarde alguns segundos.
            </p>
          )}
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {loadError}
        </div>
      )}

      {!isLoading && !loadError && summary && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
            <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 bg-blue-50 rounded-xl flex items-center justify-center flex-shrink-0">
                  <CalendarCheck className="w-4.5 h-4.5 text-primary-600" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-slate-900">{summary.todayCount}</p>
                  <p className="text-xs text-slate-500 leading-tight">Agendamentos hoje</p>
                </div>
              </div>
            </div>

            {summary.activeDoctorCount !== null && (
              <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-accent-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <Users className="w-4.5 h-4.5 text-accent-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">{summary.activeDoctorCount}</p>
                    <p className="text-xs text-slate-500 leading-tight">Médicos ativos</p>
                  </div>
                </div>
              </div>
            )}

            {summary.pendingReceivablesTotal !== null && (
              <Link
                to="/dashboard/financeiro"
                className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4 hover:shadow-md hover:border-primary-200 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-amber-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <Wallet className="w-4.5 h-4.5 text-amber-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {currencyFormatter.format(summary.pendingReceivablesTotal)}
                    </p>
                    <p className="text-xs text-slate-500 leading-tight">A receber</p>
                  </div>
                </div>
              </Link>
            )}

            {summary.paidThisMonthTotal !== null && (
              <Link
                to="/dashboard/financeiro"
                className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4 hover:shadow-md hover:border-primary-200 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-green-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <TrendingUp className="w-4.5 h-4.5 text-green-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {currencyFormatter.format(summary.paidThisMonthTotal)}
                    </p>
                    <p className="text-xs text-slate-500 leading-tight">Recebido este mês</p>
                  </div>
                </div>
              </Link>
            )}

            {clinic && (
              <Link
                to="/dashboard/plano"
                className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4 hover:shadow-md hover:border-primary-200 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 bg-purple-50 rounded-xl flex items-center justify-center flex-shrink-0">
                    <CreditCard className="w-4.5 h-4.5 text-purple-600" />
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-slate-900">
                      {CLINIC_STATUS_LABELS[clinic.status] ?? clinic.status}
                    </p>
                    <p className="text-xs text-slate-500 leading-tight">Status do plano — ver detalhes</p>
                  </div>
                </div>
              </Link>
            )}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6 items-start">
            <TodayActivitiesCard isDoctor={isDoctor} />
            <TaskListCard />
          </div>

          {showAdminReports && (
            <>
              <ManagerialReportsSection />
              <PatientsReportSection />
            </>
          )}
        </>
      )}
    </DashboardLayout>
  );
}

function TodayActivitiesCard({ isDoctor }: { isDoctor: boolean }) {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [filterDoctorId, setFilterDoctorId] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    const todayStr = todayAsInputValue();

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        if (isDoctor) {
          const list = await apiFetch<Appointment[]>('/api/doctors/me/appointments');
          if (cancelled) return;
          setAppointments(list.filter((appointment) => appointment.date === todayStr));
        } else {
          const [list, doctorsList] = await Promise.all([
            apiFetch<Appointment[]>('/api/appointments'),
            apiFetch<Doctor[]>('/api/doctors'),
          ]);
          if (cancelled) return;
          setAppointments(list.filter((appointment) => appointment.date === todayStr));
          setDoctors(doctorsList.filter((doctor) => doctor.active));
        }
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar as atividades do dia.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [isDoctor]);

  const filteredAppointments = appointments
    .filter((appointment) => filterDoctorId == null || appointment.doctorId === filterDoctorId)
    .slice()
    .sort((a, b) => a.startTime.localeCompare(b.startTime));

  return (
    <div className={`${CARD_CLASSES} overflow-hidden`}>
      <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
        <div className="flex items-center gap-2">
          <CalendarDays className="w-4 h-4 text-slate-400" />
          <h2 className="text-base font-semibold text-slate-900">Atividades do Dia</h2>
        </div>
        {!isDoctor && doctors.length > 0 && (
          <select
            value={filterDoctorId ?? ''}
            onChange={(e) => setFilterDoctorId(e.target.value ? Number(e.target.value) : null)}
            className="px-3.5 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all w-full sm:w-auto"
          >
            <option value="">Todos os médicos</option>
            {doctors.map((doctor) => (
              <option key={doctor.id} value={doctor.id}>
                {doctor.name}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="px-5 py-3 border-b border-slate-100 flex flex-wrap items-center gap-4">
        {STATUS_LEGEND_ORDER.map((status) => (
          <div key={status} className="flex items-center gap-1.5">
            <span className={`w-2 h-2 rounded-full ${STATUS_DOT[status]}`} />
            <span className="text-xs text-slate-500">{STATUS_LABEL[status]}</span>
          </div>
        ))}
      </div>

      {isLoading && (
        <div className="flex justify-center py-10">
          <div className="w-6 h-6 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="mx-5 my-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{loadError}</div>
      )}

      {!isLoading && !loadError && (
        filteredAppointments.length === 0 ? (
          <div className="px-6 py-16 text-center">
            <div className="flex flex-col items-center gap-3">
              <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
                <CalendarDays className="w-6 h-6 text-slate-400" />
              </div>
              <p className="text-slate-500 text-sm">Nenhum agendamento hoje.</p>
            </div>
          </div>
        ) : (
          <ul className="divide-y divide-slate-100 max-h-[420px] overflow-y-auto">
            {filteredAppointments.map((appointment) => (
              <li key={appointment.id} className="px-5 py-3 flex items-center gap-3">
                <span className={`w-2 h-2 rounded-full shrink-0 ${STATUS_DOT[appointment.status]}`} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-800 truncate">{appointment.patientName}</p>
                  {!isDoctor && <p className="text-xs text-slate-500 truncate">{appointment.doctorName}</p>}
                </div>
                <div className="text-right shrink-0">
                  <p className="text-sm text-slate-700">
                    {appointment.startTime} — {appointment.endTime}
                  </p>
                  <p className="text-xs text-slate-400">{computeDurationLabel(appointment)}</p>
                </div>
                <span
                  className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium shrink-0 ${STATUS_BADGE[appointment.status]}`}
                >
                  {STATUS_LABEL[appointment.status]}
                </span>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  );
}

function TaskListCard() {
  const [tasks, setTasks] = useState<Task[]>(() => {
    try {
      return JSON.parse(localStorage.getItem(TASKS_STORAGE_KEY) ?? '[]');
    } catch {
      return [];
    }
  });
  const [newTaskText, setNewTaskText] = useState('');

  useEffect(() => {
    localStorage.setItem(TASKS_STORAGE_KEY, JSON.stringify(tasks));
  }, [tasks]);

  function handleAddTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = newTaskText.trim();
    if (!text) return;
    setTasks((prev) => [...prev, { id: crypto.randomUUID(), text, done: false }]);
    setNewTaskText('');
  }

  function toggleTask(id: string) {
    setTasks((prev) => prev.map((task) => (task.id === id ? { ...task, done: !task.done } : task)));
  }

  function removeTask(id: string) {
    setTasks((prev) => prev.filter((task) => task.id !== id));
  }

  return (
    <div className={`${CARD_CLASSES} overflow-hidden`}>
      <div className="px-5 py-4 border-b border-slate-100 flex items-center gap-2">
        <ClipboardList className="w-4 h-4 text-slate-400" />
        <h2 className="text-base font-semibold text-slate-900">Lista de tarefas</h2>
      </div>
      <form onSubmit={handleAddTask} className="px-5 py-4 flex gap-2 border-b border-slate-100">
        <input
          type="text"
          value={newTaskText}
          onChange={(e) => setNewTaskText(e.target.value)}
          placeholder="Nova tarefa..."
          className="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
        />
        <button
          type="submit"
          className="flex items-center justify-center gap-2 bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm shrink-0"
        >
          <Plus className="w-4 h-4" /> Adicionar
        </button>
      </form>
      {tasks.length === 0 ? (
        <div className="px-6 py-16 text-center">
          <div className="flex flex-col items-center gap-3">
            <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center">
              <ClipboardList className="w-6 h-6 text-slate-400" />
            </div>
            <p className="text-slate-500 text-sm">Nenhuma tarefa por enquanto.</p>
          </div>
        </div>
      ) : (
        <ul className="divide-y divide-slate-100 max-h-[340px] overflow-y-auto">
          {tasks.map((task) => (
            <li key={task.id} className="px-5 py-3 flex items-center gap-3">
              <input
                type="checkbox"
                checked={task.done}
                onChange={() => toggleTask(task.id)}
                className="w-4 h-4 rounded border-slate-300 text-primary-600 focus:ring-primary-200"
              />
              <span className={`flex-1 text-sm ${task.done ? 'line-through text-slate-400' : 'text-slate-700'}`}>
                {task.text}
              </span>
              <button
                type="button"
                onClick={() => removeTask(task.id)}
                className="p-1 text-slate-400 hover:text-red-600 transition-colors shrink-0"
                aria-label="Remover tarefa"
              >
                <X className="w-4 h-4" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function StatTile({
  icon: Icon,
  iconBg,
  iconColor,
  value,
  label,
}: {
  icon: LucideIcon;
  iconBg: string;
  iconColor: string;
  value: string | number;
  label: string;
}) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200/60 shadow-sm p-4">
      <div className="flex items-center gap-3">
        <div className={`w-9 h-9 ${iconBg} rounded-xl flex items-center justify-center flex-shrink-0`}>
          <Icon className={`w-4.5 h-4.5 ${iconColor}`} />
        </div>
        <div>
          <p className="text-2xl font-bold text-slate-900">{value}</p>
          <p className="text-xs text-slate-500 leading-tight">{label}</p>
        </div>
      </div>
    </div>
  );
}

interface ChartCardProps {
  title: string;
  total: number;
  data: { date: string; value: number }[];
  color: string;
  gradientId: string;
}

function ChartCard({ title, total, data, color, gradientId }: ChartCardProps) {
  return (
    <div className={`${CARD_CLASSES} p-5`}>
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        <span className="text-sm font-bold text-slate-900">{currencyFormatter.format(total)}</span>
      </div>
      <ResponsiveContainer width="100%" height={220}>
        <AreaChart data={data}>
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor={color} stopOpacity={0.3} />
              <stop offset="95%" stopColor={color} stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="date" tickFormatter={formatDayMonth} tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
          <YAxis tick={{ fontSize: 11 }} axisLine={false} tickLine={false} width={40} />
          <Tooltip
            formatter={(value) => currencyFormatter.format(Number(value))}
            labelFormatter={(label) => formatDayMonth(String(label))}
          />
          <Area type="monotone" dataKey="value" stroke={color} strokeWidth={2} fill={`url(#${gradientId})`} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

function ManagerialReportsSection() {
  const [reports, setReports] = useState<DashboardReports | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const data = await apiFetch<DashboardReports>('/api/dashboard/reports?days=14');
        if (cancelled) return;
        setReports(data);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os relatórios gerenciais.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const receivedSeries = useMemo(
    () => (reports ? reports.dailySeries.map((point) => ({ date: point.date, value: point.received })) : []),
    [reports],
  );
  const receivableSeries = useMemo(
    () => (reports ? reports.dailySeries.map((point) => ({ date: point.date, value: point.receivable })) : []),
    [reports],
  );
  const receivedTotal = receivedSeries.reduce((sum, point) => sum + point.value, 0);
  const receivableTotal = receivableSeries.reduce((sum, point) => sum + point.value, 0);

  return (
    <section className="mt-2 mb-6">
      <h2 className="text-lg font-bold text-slate-900 tracking-tight mb-4">Relatórios Gerenciais</h2>

      {isLoading && (
        <div className="flex justify-center py-16">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{loadError}</div>
      )}

      {!isLoading && !loadError && reports && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <ChartCard
              title="Valores Recebidos"
              total={receivedTotal}
              data={receivedSeries}
              color="#16a34a"
              gradientId="dashboard-received-gradient"
            />
            <ChartCard
              title="Valores A Receber"
              total={receivableTotal}
              data={receivableSeries}
              color="#dc2626"
              gradientId="dashboard-receivable-gradient"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <StatTile
              icon={CalendarCheck}
              iconBg="bg-blue-50"
              iconColor="text-primary-600"
              value={reports.attendedCount}
              label="Sessões Atendidas"
            />
            <StatTile
              icon={CalendarX}
              iconBg="bg-slate-100"
              iconColor="text-slate-500"
              value={reports.cancelledCount}
              label="Sessões Desmarcadas"
            />
            <StatTile icon={UserX} iconBg="bg-red-50" iconColor="text-red-600" value={reports.noShowCount} label="Faltas" />
          </div>

          <div className={`${CARD_CLASSES} p-5`}>
            <h3 className="text-sm font-semibold text-slate-900 mb-4">Atendimentos por dia</h3>
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={reports.dailySeries}>
                <XAxis dataKey="date" tickFormatter={formatDayMonth} tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11 }} axisLine={false} tickLine={false} allowDecimals={false} width={30} />
                <Tooltip labelFormatter={(label) => formatDayMonth(String(label))} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Area
                  type="monotone"
                  dataKey="attended"
                  name="Atendidos"
                  stackId="1"
                  stroke="#16a34a"
                  fill="#16a34a"
                  fillOpacity={0.25}
                />
                <Area
                  type="monotone"
                  dataKey="cancelled"
                  name="Desmarcados"
                  stackId="1"
                  stroke="#64748b"
                  fill="#64748b"
                  fillOpacity={0.25}
                />
                <Area
                  type="monotone"
                  dataKey="noShow"
                  name="Faltantes"
                  stackId="1"
                  stroke="#dc2626"
                  fill="#dc2626"
                  fillOpacity={0.25}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className={`${CARD_CLASSES} p-5 max-w-md`}>
            <h3 className="text-sm font-semibold text-slate-900 mb-4">DRE simplificado</h3>
            <div className="space-y-2.5">
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Receita Bruta</span>
                <span className="font-semibold text-slate-800">{currencyFormatter.format(reports.grossRevenue)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-slate-500">Custo Direto — Comissões</span>
                <span className="font-semibold text-slate-800">{currencyFormatter.format(reports.directCost)}</span>
              </div>
              <div className="flex justify-between text-sm pt-2.5 border-t border-slate-100">
                <span className="text-slate-700 font-medium">Lucro Bruto</span>
                <span className="font-bold text-slate-900">{currencyFormatter.format(reports.grossProfit)}</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function PieCard({ title, data }: { title: string; data: Distribution[] }) {
  return (
    <div className={`${CARD_CLASSES} p-4`}>
      <h3 className="text-sm font-semibold text-slate-900 mb-2 text-center">{title}</h3>
      {data.length === 0 ? (
        <p className="text-xs text-slate-400 text-center py-14">Sem dados.</p>
      ) : (
        <ResponsiveContainer width="100%" height={220}>
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="name" cx="50%" cy="45%" outerRadius={60}>
              {data.map((entry, index) => (
                <Cell key={entry.name} fill={PIE_COLORS[index % PIE_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip />
            <Legend wrapperStyle={{ fontSize: 11 }} />
          </PieChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}

function PatientsReportSection() {
  const [patients, setPatients] = useState<Patient[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const list = await apiFetch<Patient[]>('/api/patients');
        if (cancelled) return;
        setPatients(list);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar o relatório de pacientes.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const genderData = useMemo(() => buildDistribution(patients, (patient) => patient.sex), [patients]);
  const ageData = useMemo(() => buildAgeDistribution(patients), [patients]);
  const statusData = useMemo(
    () => buildDistribution(patients, (patient) => (patient.active ? 'Ativo' : 'Arquivado')),
    [patients],
  );
  const referralData = useMemo(() => buildDistribution(patients, (patient) => patient.referralSource), [patients]);

  return (
    <section className="mb-6">
      <h2 className="text-lg font-bold text-slate-900 tracking-tight mb-4">Relatório de Pacientes</h2>

      {isLoading && (
        <div className="flex justify-center py-16">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl">{loadError}</div>
      )}

      {!isLoading && !loadError && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <PieCard title="Gênero" data={genderData} />
          <PieCard title="Faixa etária" data={ageData} />
          <PieCard title="Status" data={statusData} />
          <PieCard title="Como conheceu" data={referralData} />
        </div>
      )}
    </section>
  );
}
