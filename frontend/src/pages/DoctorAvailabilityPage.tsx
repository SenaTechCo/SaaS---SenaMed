import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { AvailabilitySlot, AvailabilitySlotPayload, DayOfWeek, Doctor } from '../types/doctor';
import './auth-pages.css';
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

interface EditableSlot {
  key: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
}

let tempKeySeq = 0;
function nextTempKey(): string {
  tempKeySeq += 1;
  return `new-${tempKeySeq}`;
}

export function DoctorAvailabilityPage() {
  const { id } = useParams<{ id: string }>();

  const [doctor, setDoctor] = useState<Doctor | null>(null);
  const [slots, setSlots] = useState<EditableSlot[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setLoadError(null);
      try {
        const [doctorData, availability] = await Promise.all([
          apiFetch<Doctor>(`/api/doctors/${id}`),
          apiFetch<AvailabilitySlot[]>(`/api/doctors/${id}/availability`),
        ]);
        if (cancelled) return;
        setDoctor(doctorData);
        setSlots(
          availability.map((slot) => ({
            key: slot.id,
            dayOfWeek: slot.dayOfWeek,
            startTime: slot.startTime,
            endTime: slot.endTime,
          })),
        );
      } catch (err) {
        if (cancelled) return;
        setLoadError(err instanceof ApiError ? err.message : 'Não foi possível carregar os horários.');
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [id]);

  function addSlot(day: DayOfWeek) {
    setSlots((prev) => [...prev, { key: nextTempKey(), dayOfWeek: day, startTime: '08:00', endTime: '12:00' }]);
    setSaveSuccess(false);
  }

  function updateSlot(key: string, field: 'startTime' | 'endTime', value: string) {
    setSlots((prev) => prev.map((slot) => (slot.key === key ? { ...slot, [field]: value } : slot)));
    setSaveSuccess(false);
  }

  function removeSlot(key: string) {
    setSlots((prev) => prev.filter((slot) => slot.key !== key));
    setSaveSuccess(false);
  }

  async function handleSave() {
    if (!id) return;
    setSaveError(null);
    setSaveSuccess(false);
    setIsSaving(true);
    try {
      const payload: AvailabilitySlotPayload[] = slots.map((slot) => ({
        dayOfWeek: slot.dayOfWeek,
        startTime: slot.startTime,
        endTime: slot.endTime,
      }));
      const saved = await apiFetch<AvailabilitySlot[]>(`/api/doctors/${id}/availability`, {
        method: 'POST',
        body: payload,
      });
      setSlots(
        saved.map((slot) => ({
          key: slot.id,
          dayOfWeek: slot.dayOfWeek,
          startTime: slot.startTime,
          endTime: slot.endTime,
        })),
      );
      setSaveSuccess(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Não foi possível salvar os horários.');
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <DashboardLayout>
      <Link className="back-link" to="/dashboard/medicos">
        ← Voltar para médicos
      </Link>

      <div className="page-header">
        <div>
          <h2>Horários de atendimento{doctor ? ` — ${doctor.name}` : ''}</h2>
          <p className="subtitle">Defina as janelas de disponibilidade semanal por dia da semana.</p>
        </div>
      </div>

      {isLoading && <p className="loading-state">Carregando horários...</p>}
      {loadError && <div className="form-error">{loadError}</div>}

      {!isLoading && !loadError && (
        <>
          {saveError && <div className="form-error">{saveError}</div>}
          {saveSuccess && <div className="form-success">Horários salvos com sucesso.</div>}

          <div className="availability-grid">
            {DAYS.map((day) => {
              const daySlots = slots.filter((slot) => slot.dayOfWeek === day.value);
              return (
                <div className="availability-day card" key={day.value}>
                  <h3>{day.label}</h3>
                  {daySlots.length === 0 && <p className="empty-state">Sem horários</p>}
                  {daySlots.map((slot) => (
                    <div className="availability-slot" key={slot.key}>
                      <input
                        type="time"
                        value={slot.startTime}
                        onChange={(e) => updateSlot(slot.key, 'startTime', e.target.value)}
                        aria-label={`Início ${day.label}`}
                      />
                      <span>até</span>
                      <input
                        type="time"
                        value={slot.endTime}
                        onChange={(e) => updateSlot(slot.key, 'endTime', e.target.value)}
                        aria-label={`Fim ${day.label}`}
                      />
                      <button
                        type="button"
                        className="btn-danger btn-small"
                        onClick={() => removeSlot(slot.key)}
                        aria-label={`Remover horário de ${day.label}`}
                      >
                        Remover
                      </button>
                    </div>
                  ))}
                  <button type="button" className="btn-link" onClick={() => addSlot(day.value)}>
                    + Adicionar horário
                  </button>
                </div>
              );
            })}
          </div>

          <button type="button" className="btn-primary btn-small" style={{ width: 'auto' }} onClick={handleSave} disabled={isSaving}>
            {isSaving ? 'Salvando...' : 'Salvar horários'}
          </button>
        </>
      )}
    </DashboardLayout>
  );
}
