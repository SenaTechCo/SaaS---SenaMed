import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { AvailabilitySlot, DayOfWeek } from '../types/doctor';

const DAYS: { value: DayOfWeek; label: string }[] = [
  { value: 1, label: 'Segunda' },
  { value: 2, label: 'Terça' },
  { value: 3, label: 'Quarta' },
  { value: 4, label: 'Quinta' },
  { value: 5, label: 'Sexta' },
  { value: 6, label: 'Sábado' },
  { value: 7, label: 'Domingo' },
];

export function MyAvailabilityPage() {
  const [slots, setSlots] = useState<AvailabilitySlot[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const availability = await apiFetch<AvailabilitySlot[]>('/api/doctors/me/availability');
        if (cancelled) return;
        setSlots(availability);
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar sua disponibilidade.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <DashboardLayout>
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Minha Disponibilidade</h1>
          <p className="text-sm text-slate-500 mt-1">Suas janelas de atendimento semanal, definidas pela clínica.</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">
          {loadError}
        </div>
      )}

      {!isLoading && !loadError && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {DAYS.map((day) => {
            const daySlots = slots.filter((slot) => slot.dayOfWeek === day.value);
            return (
              <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4" key={day.value}>
                <h3 className="text-sm font-semibold text-slate-900 mb-3">{day.label}</h3>
                {daySlots.length === 0 ? (
                  <div className="flex items-center gap-2 text-slate-400 text-sm">
                    <Clock className="w-4 h-4" />
                    <span>Sem horários</span>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {daySlots.map((slot) => (
                      <div key={slot.id} className="flex items-center gap-2 text-sm text-slate-700">
                        <Clock className="w-4 h-4 text-slate-400 flex-shrink-0" />
                        <span>{slot.startTime}</span>
                        <span className="text-slate-400 text-sm">até</span>
                        <span>{slot.endTime}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </DashboardLayout>
  );
}
