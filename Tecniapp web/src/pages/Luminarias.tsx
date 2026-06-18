import React, { useState, useMemo } from 'react'
import { Lightbulb, Search, CheckCircle2 } from 'lucide-react'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'
import { ref, update } from 'firebase/database'
import { toast } from 'react-hot-toast'
import { rtdbLuminarias } from '../firebase/config'
import { useLuminarias } from '../hooks/useLuminarias'
import { Spinner } from '../components/ui/Spinner'
import type { LuminariaEstado } from '../types'

export default function Luminarias() {
  const { luminarias, loading } = useLuminarias()
  const [search, setSearch] = useState('')
  const [estadoFilter, setEstadoFilter] = useState<LuminariaEstado | ''>('')
  const [vehiculoFilter, setVehiculoFilter] = useState('')
  const [savingId, setSavingId] = useState<string | null>(null)
  const [selectedLuminaria, setSelectedLuminaria] = useState<string | null>(null)

  const vehiculos = useMemo(() => {
    const set = new Set(luminarias.map(l => l.vehiculoId).filter(Boolean))
    return Array.from(set).sort()
  }, [luminarias])

  const filtered = useMemo(() => {
    let list = luminarias
    if (search) {
      const q = search.toLowerCase()
      list = list.filter(l =>
        l.cliente.toLowerCase().includes(q) ||
        l.localizacion.toLowerCase().includes(q) ||
        l.ejecutorNombre.toLowerCase().includes(q)
      )
    }
    if (estadoFilter) list = list.filter(l => l.estado === estadoFilter)
    if (vehiculoFilter) list = list.filter(l => l.vehiculoId === vehiculoFilter)
    return list
  }, [luminarias, search, estadoFilter, vehiculoFilter])

  const pendientes = luminarias.filter(l => l.estado === 'PENDIENTE').length
  const reparadas = luminarias.filter(l => l.estado === 'REPARADA').length

  const handleMarcarReparada = async (id: string) => {
    setSavingId(id)
    try {
      await update(ref(rtdbLuminarias, `luminarias/${id}`), {
        estado: 'REPARADA',
        fechaReparacion: Date.now(),
      })
      toast.success('Luminaria marcada como reparada')
    } catch {
      toast.error('Error al actualizar luminaria')
    } finally {
      setSavingId(null)
    }
  }

  const detailLum = selectedLuminaria ? luminarias.find(l => l.id === selectedLuminaria) : null
  const detailMateriales = useMemo(() => {
    if (!detailLum?.materialesJson) return []
    try { return JSON.parse(detailLum.materialesJson) } catch { return [] }
  }, [detailLum])

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Luminarias</h2>
          <p className="text-slate-500 text-sm mt-0.5">
            {loading ? 'Cargando...' : `${luminarias.length} registros`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-amber-50 text-amber-700 text-sm font-semibold rounded-full border border-amber-200">
            <Lightbulb size={14} /> {pendientes} pendientes
          </span>
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-50 text-green-700 text-sm font-semibold rounded-full border border-green-200">
            <CheckCircle2 size={14} /> {reparadas} reparadas
          </span>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl border border-slate-100 p-4">
        <div className="flex flex-col sm:flex-row gap-3">
          <div className="relative flex-1">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar por cliente, localización, ejecutor..."
              className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
            />
          </div>
          <select
            value={estadoFilter}
            onChange={e => setEstadoFilter(e.target.value as LuminariaEstado | '')}
            className="px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
          >
            <option value="">Todos los estados</option>
            <option value="PENDIENTE">Pendiente</option>
            <option value="REPARADA">Reparada</option>
          </select>
          <select
            value={vehiculoFilter}
            onChange={e => setVehiculoFilter(e.target.value)}
            className="px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white"
          >
            <option value="">Todos los vehículos</option>
            {vehiculos.map(v => <option key={v} value={v}>{v}</option>)}
          </select>
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <div className="bg-white rounded-xl border border-slate-100 p-5 space-y-3 animate-pulse">
          {[1,2,3,4,5].map(i => <div key={i} className="h-12 bg-slate-100 rounded-lg" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-16 text-center">
          <Lightbulb size={40} className="text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">No se encontraron luminarias</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  {['ID','Localización','Cliente','Ejecutor','Estado','Reg.','Reparación','Acciones'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filtered.map(lum => (
                  <tr
                    key={lum.id}
                    className={`hover:bg-slate-50 transition-colors border-l-4 ${lum.estado === 'PENDIENTE' ? 'border-l-amber-400' : 'border-l-green-400'}`}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-slate-600">{lum.id.slice(-8)}</td>
                    <td className="px-4 py-3 text-slate-700 text-xs max-w-[140px] truncate">{lum.localizacion || '-'}</td>
                    <td className="px-4 py-3 text-slate-700 text-xs max-w-[120px] truncate">{lum.cliente || '-'}</td>
                    <td className="px-4 py-3 text-slate-600 text-xs">{lum.ejecutorNombre || '-'}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold border ${lum.estado === 'PENDIENTE' ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-green-50 text-green-700 border-green-200'}`}>
                        {lum.estado === 'PENDIENTE' ? 'Pendiente' : 'Reparada'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-400 text-xs whitespace-nowrap">
                      {lum.fechaRegistro ? format(new Date(lum.fechaRegistro), 'dd/MM/yy', { locale: es }) : '-'}
                    </td>
                    <td className="px-4 py-3 text-slate-400 text-xs whitespace-nowrap">
                      {lum.fechaReparacion ? format(new Date(lum.fechaReparacion), 'dd/MM/yy', { locale: es }) : '-'}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => setSelectedLuminaria(selectedLuminaria === lum.id ? null : lum.id)}
                          className="px-2 py-1 text-xs text-[#0066CC] hover:bg-blue-50 rounded-lg transition-colors font-medium"
                        >
                          Detalle
                        </button>
                        {lum.estado === 'PENDIENTE' && (
                          <button
                            onClick={() => handleMarcarReparada(lum.id)}
                            disabled={savingId === lum.id}
                            className="px-2 py-1 text-xs bg-green-600 text-white hover:bg-green-700 rounded-lg transition-colors font-medium flex items-center gap-1 disabled:opacity-60"
                          >
                            {savingId === lum.id ? <Spinner size="sm" className="text-white" /> : <CheckCircle2 size={10} />}
                            Reparar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Detail panel */}
      {detailLum && (
        <div className="bg-white rounded-xl border border-slate-100 p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-slate-800">Detalle de Luminaria</h3>
            <button onClick={() => setSelectedLuminaria(null)} className="text-xs text-slate-400 hover:text-slate-600">✕ Cerrar</button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-2 text-sm">
              <div><span className="text-xs text-slate-400">Cliente:</span> <span className="font-medium">{detailLum.cliente}</span></div>
              <div><span className="text-xs text-slate-400">Contacto:</span> <span>{detailLum.contacto}</span></div>
              <div><span className="text-xs text-slate-400">Localización:</span> <span>{detailLum.localizacion}</span></div>
              <div><span className="text-xs text-slate-400">Ejecutor:</span> <span>{detailLum.ejecutorNombre} ({detailLum.ejecutorCedula})</span></div>
              {detailLum.observaciones && (
                <div><span className="text-xs text-slate-400">Observaciones:</span> <p className="mt-0.5 text-slate-600">{detailLum.observaciones}</p></div>
              )}
            </div>
            {detailMateriales.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-slate-500 mb-2">Materiales</p>
                <div className="space-y-1">
                  {detailMateriales.map((m: any, i: number) => (
                    <div key={i} className="flex justify-between text-xs text-slate-600 py-1 border-b border-slate-50">
                      <span>{m.descripcion || m.material || JSON.stringify(m)}</span>
                      <span className="font-medium">{m.cantidad || m.quantity || 1}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
