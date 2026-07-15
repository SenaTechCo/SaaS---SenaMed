import { useEffect, useState } from 'react';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { AvailabilitySlot, DayOfWeek } from '../types/doctor';
import './dashboard-shared.css';
import './DoctorAvailabilityPage.css';

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
      <div className="page-header">
        <div>
          <h2>Minha Disponibilidade</h2>
          <p className="subtitle">Suas janelas de atendimento semanal, definidas pela clínica.</p>
        </div>
      </div>

      {isLoading && <p className="loading-state">Carregando disponibilidade...</p>}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && (
        <div className="availability-grid">
          {DAYS.map((day) => {
            const daySlots = slots.filter((slot) => slot.dayOfWeek === day.value);
            return (
              <div className="availability-day card" key={day.value}>
                <h3>{day.label}</h3>
                {daySlots.length === 0 && <p className="empty-state">Sem horários</p>}
                {daySlots.map((slot) => (
                  <div className="availability-slot" key={slot.id}>
                    <span>{slot.startTime} até {slot.endTime}</span>
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}
    </DashboardLayout>
  );
}
