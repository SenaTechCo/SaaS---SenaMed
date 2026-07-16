import { useEffect, useState } from 'react';
import { ArrowLeft, Plus, Trash2 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../components/DashboardLayout';
import { apiFetch, ApiError } from '../lib/http';
import type { AvailabilitySlot, AvailabilitySlotPayload, DayOfWeek, Doctor } from '../types/doctor';

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
      <Link
        className="inline-flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium mb-4"
        to="/dashboard/medicos"
      >
        <ArrowLeft className="w-4 h-4" /> Voltar para médicos
      </Link>

      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 mb-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
            Horários de atendimento{doctor ? ` — ${doctor.name}` : ''}
          </h1>
          <p className="text-sm text-slate-500 mt-1">Defina as janelas de disponibilidade semanal por dia da semana.</p>
        </div>
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-24">
          <div className="w-8 h-8 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
        </div>
      )}
      {loadError && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{loadError}</div>
      )}

      {!isLoading && !loadError && (
        <>
          {saveError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-6">{saveError}</div>
          )}
          {saveSuccess && (
            <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-6">
              Horários salvos com sucesso.
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
            {DAYS.map((day) => {
              const daySlots = slots.filter((slot) => slot.dayOfWeek === day.value);
              return (
                <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4" key={day.value}>
                  <h3 className="text-sm font-semibold text-slate-900 mb-3">{day.label}</h3>
                  {daySlots.length === 0 && <p className="text-slate-500 text-sm mb-3">Sem horários</p>}
                  <div className="space-y-2 mb-3">
                    {daySlots.map((slot) => (
                      <div className="flex items-center gap-2" key={slot.key}>
                        <input
                          type="time"
                          value={slot.startTime}
                          onChange={(e) => updateSlot(slot.key, 'startTime', e.target.value)}
                          aria-label={`Início ${day.label}`}
                          className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                        />
                        <span className="text-slate-400 text-sm">até</span>
                        <input
                          type="time"
                          value={slot.endTime}
                          onChange={(e) => updateSlot(slot.key, 'endTime', e.target.value)}
                          aria-label={`Fim ${day.label}`}
                          className="w-full px-3 py-2 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-100 focus:border-primary-400 bg-white transition-all"
                        />
                        <button
                          type="button"
                          onClick={() => removeSlot(slot.key)}
                          aria-label={`Remover horário de ${day.label}`}
                          className="p-1.5 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-600 transition-colors flex-shrink-0"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    onClick={() => addSlot(day.value)}
                    className="flex items-center gap-1 text-primary-600 hover:text-primary-700 text-sm font-medium"
                  >
                    <Plus className="w-3.5 h-3.5" /> Adicionar horário
                  </button>
                </div>
              );
            })}
          </div>

          <button
            type="button"
            onClick={handleSave}
            disabled={isSaving}
            className="bg-primary-600 hover:bg-primary-700 text-white font-semibold px-4 py-2.5 rounded-xl shadow-sm hover:shadow-md transition-all duration-150 text-sm disabled:opacity-50"
          >
            {isSaving ? 'Salvando...' : 'Salvar horários'}
          </button>
        </>
      )}
    </DashboardLayout>
  );
}
