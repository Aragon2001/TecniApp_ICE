import React, { useState, useMemo } from 'react'
import { Search, Gauge } from 'lucide-react'
import { useMedidores } from '../hooks/useMedidores'

export default function Medidores() {
  const { medidores, loading } = useMedidores()
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    if (!search) return medidores
    const q = search.toLowerCase()
    return medidores.filter(m =>
      m.medidorNumber.toLowerCase().includes(q) ||
      m.calle.toLowerCase().includes(q) ||
      m.pueblo.toLowerCase().includes(q) ||
      m.cliente.toLowerCase().includes(q) ||
      m.localizacion.toLowerCase().includes(q) ||
      m.subregion.toLowerCase().includes(q)
    )
  }, [medidores, search])

  // Stats by subregion
  const bySubregion = useMemo(() => {
    const counts: Record<string, number> = {}
    medidores.forEach(m => {
      const key = m.subregion || 'Sin subregión'
      counts[key] = (counts[key] || 0) + 1
    })
    return Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 5)
  }, [medidores])

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Consulta de Medidores</h2>
          <p className="text-slate-500 text-sm mt-0.5">
            {loading ? 'Cargando...' : `${medidores.length.toLocaleString()} medidores registrados`}
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-50 text-[#003087] text-sm font-semibold rounded-full">
          <Gauge size={14} /> {medidores.length.toLocaleString()}
        </span>
      </div>

      {/* Stats bar */}
      {bySubregion.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {bySubregion.map(([sub, count]) => (
            <span key={sub} className="inline-flex items-center gap-1.5 px-3 py-1 bg-white border border-slate-200 rounded-full text-xs text-slate-600 font-medium">
              {sub}: <span className="font-bold text-[#003087]">{count}</span>
            </span>
          ))}
        </div>
      )}

      {/* Search */}
      <div className="relative max-w-2xl mx-auto">
        <Search size={20} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder="Buscar por número, localización, cliente, pueblo..."
          className="w-full pl-12 pr-4 py-3.5 border border-slate-200 rounded-xl text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] shadow-sm"
        />
        {search && (
          <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-slate-400">
            {filtered.length} resultado(s)
          </span>
        )}
      </div>

      {/* Results */}
      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4 animate-pulse">
          {[1,2,3,4,5,6].map(i => (
            <div key={i} className="bg-white rounded-xl border border-slate-100 p-5 space-y-3">
              <div className="h-6 bg-slate-100 rounded w-2/3" />
              <div className="h-4 bg-slate-100 rounded w-full" />
              <div className="h-4 bg-slate-100 rounded w-1/2" />
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-16 text-center">
          <Gauge size={40} className="text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">No se encontraron medidores</p>
          {search && <p className="text-slate-400 text-sm mt-1">Intente con otro término de búsqueda</p>}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map((m) => (
            <div
              key={m.medidorNumber}
              className="bg-white rounded-xl border border-slate-100 p-5 hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between gap-2 mb-3">
                <span className="font-mono text-lg font-bold text-[#003087] leading-tight">
                  {m.medidorNumber}
                </span>
                {m.subregion && (
                  <span className="text-[10px] bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full font-semibold flex-shrink-0">
                    {m.subregion}
                  </span>
                )}
              </div>
              <div className="space-y-1 text-sm text-slate-600">
                {m.calle && (
                  <div className="flex gap-2">
                    <span className="text-xs text-slate-400 w-14 flex-shrink-0">Calle</span>
                    <span className="font-medium truncate">{m.calle}</span>
                  </div>
                )}
                {m.pueblo && (
                  <div className="flex gap-2">
                    <span className="text-xs text-slate-400 w-14 flex-shrink-0">Pueblo</span>
                    <span className="truncate">{m.pueblo}</span>
                  </div>
                )}
                {m.localizacion && (
                  <div className="flex gap-2">
                    <span className="text-xs text-slate-400 w-14 flex-shrink-0">Localiz.</span>
                    <span className="truncate">{m.localizacion}</span>
                  </div>
                )}
                {m.cliente && (
                  <div className="flex gap-2">
                    <span className="text-xs text-slate-400 w-14 flex-shrink-0">Cliente</span>
                    <span className="text-[#003087] font-medium truncate">{m.cliente}</span>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
