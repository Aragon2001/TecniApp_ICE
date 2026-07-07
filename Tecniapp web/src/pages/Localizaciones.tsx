import { useState } from 'react'
import { MapPin, Search, ExternalLink } from 'lucide-react'
import { useLocalizaciones } from '../hooks/useLocalizaciones'

export default function Localizaciones() {
  const { localizaciones, loading, searchLocalizacion, localizacionesBySubregion } = useLocalizaciones()
  const [search, setSearch] = useState('')
  const [subregionFilter, setSubregionFilter] = useState('')

  const subregiones = Object.keys(localizacionesBySubregion).sort()

  const filtered = search || subregionFilter
    ? searchLocalizacion(search).filter(l => !subregionFilter || l.subregion === subregionFilter)
    : localizaciones

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-800">Localizaciones</h2>
          <p className="text-slate-500 text-sm mt-0.5">
            {loading ? 'Cargando...' : `${localizaciones.length.toLocaleString()} localizaciones registradas`}
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-blue-50 text-[#003087] text-sm font-semibold rounded-full">
          <MapPin size={14} /> {localizaciones.length.toLocaleString()}
        </span>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={e => setSearch(e.target.value)}
            placeholder="Buscar por nombre, código, agencia, circuito..."
            className="w-full pl-9 pr-4 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087]"
          />
        </div>
        <select
          value={subregionFilter}
          onChange={e => setSubregionFilter(e.target.value)}
          className="px-3 py-2.5 border border-slate-200 rounded-lg text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] bg-white min-w-[180px]"
        >
          <option value="">Todas las subregiones</option>
          {subregiones.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {(search || subregionFilter) && (
        <p className="text-xs text-slate-500">{filtered.length.toLocaleString()} resultado(s)</p>
      )}

      {loading ? (
        <div className="bg-white rounded-xl border border-slate-100 p-5 space-y-3 animate-pulse">
          {[1,2,3,4,5].map(i => <div key={i} className="h-10 bg-slate-100 rounded-lg" />)}
        </div>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-16 text-center">
          <MapPin size={40} className="text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">No se encontraron localizaciones</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  {['ID / Código', 'Nombre', 'Agencia', 'Subregión', 'Tipo', 'Circuito', 'GPS'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filtered.slice(0, 500).map((loc, idx) => {
                  const mapsUrl = loc.latitud && loc.longitud
                    ? `https://www.google.com/maps?q=${loc.latitud},${loc.longitud}`
                    : null
                  return (
                    <tr key={loc.id || idx} className="hover:bg-slate-50 transition-colors">
                      <td className="px-4 py-2.5 font-mono text-xs text-[#003087] font-semibold">{loc.codigo || loc.id || '-'}</td>
                      <td className="px-4 py-2.5 text-slate-700 font-medium text-xs max-w-[180px] truncate">{loc.nombre || '-'}</td>
                      <td className="px-4 py-2.5 text-slate-600 text-xs">{loc.agencia || '-'}</td>
                      <td className="px-4 py-2.5 text-xs">
                        {loc.subregion && (
                          <span className="px-2 py-0.5 bg-blue-50 text-blue-700 rounded-full text-[10px] font-semibold">
                            {loc.subregion}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-2.5 text-slate-500 text-xs">{loc.tipo || '-'}</td>
                      <td className="px-4 py-2.5 text-slate-500 text-xs">{loc.circuito || '-'}</td>
                      <td className="px-4 py-2.5">
                        {mapsUrl ? (
                          <a
                            href={mapsUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-[10px] bg-green-50 text-green-700 px-2 py-0.5 rounded-full font-semibold hover:bg-green-100 transition-colors"
                          >
                            <ExternalLink size={10} />
                            {loc.latitud!.toFixed(4)}, {loc.longitud!.toFixed(4)}
                          </a>
                        ) : (
                          <span className="text-xs text-slate-300">—</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            {filtered.length > 500 && (
              <div className="px-4 py-3 text-center text-xs text-slate-400 border-t border-slate-50">
                Mostrando 500 de {filtered.length.toLocaleString()} — refine la búsqueda para ver más
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
