import { useState, useRef, useEffect } from 'react'
import { Gauge, Copy, AlertCircle, CheckCircle2, X } from 'lucide-react'
import { useMedidores } from '../hooks/useMedidores'

function Campo({ label, valor }: { label: string; valor?: string | number }) {
  if (!valor && valor !== 0) return null
  return (
    <div className="flex gap-3 py-2.5 border-b border-slate-100 last:border-0">
      <span className="text-slate-400 text-sm w-28 flex-shrink-0">{label}</span>
      <span className="text-slate-800 text-sm font-medium break-all">{valor}</span>
    </div>
  )
}

export default function Medidores() {
  const { estado, buscarMedidor, limpiar } = useMedidores()
  const [input, setInput] = useState('')
  const [copiado, setCopiado] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const prevTipo = useRef<string>('idle')

  // Auto-clear and refocus after each result so the scanner is always ready
  useEffect(() => {
    const tipo = estado.tipo
    if (prevTipo.current === 'cargando' && tipo !== 'cargando') {
      setInput('')
      inputRef.current?.focus()
    }
    prevTipo.current = tipo
  }, [estado.tipo])

  const handleConsultar = () => {
    const trimmed = input.trim()
    if (!trimmed) return
    buscarMedidor(trimmed)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleConsultar()
  }

  const handleLimpiar = () => {
    setInput('')
    limpiar()
    inputRef.current?.focus()
  }

  const copiarInfo = () => {
    if (estado.tipo !== 'encontrado') return
    const m = estado.medidor
    const texto = [
      'DATOS DEL MEDIDOR',
      '',
      `Medidor      : ${m.medidorNumber || '-'}`,
      `Cliente      : ${m.cliente || '-'}`,
      `Localización : ${m.localizacion ?? '-'}`,
      `Calle        : ${m.calle || '-'}`,
      `Poste        : ${m.poste || '-'}`,
      `Metros       : ${m.metros || '-'}`,
      `Pueblo       : ${m.pueblo || '-'}`,
      `Subregión    : ${m.subregion || '-'}`,
      '',
      'Generado desde TecniApp ICE Web',
    ].join('\n')

    navigator.clipboard.writeText(texto).then(() => {
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    })
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Consulta de Medidores</h2>
        <p className="text-slate-500 text-sm mt-0.5">
          Escanee o ingrese el número de medidor
        </p>
      </div>

      {/* Search box — always visible and ready */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <label className="text-sm font-semibold text-slate-700 block mb-2">
          Número de Medidor
        </label>
        <div className="flex gap-3">
          <div className="relative flex-1">
            <Gauge size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              ref={inputRef}
              type="text"
              inputMode="numeric"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Escanee o escriba el número..."
              autoFocus
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              disabled={estado.tipo === 'cargando'}
              className="w-full pl-10 pr-10 py-3.5 border border-slate-200 rounded-xl text-base text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#003087]/25 focus:border-[#003087] font-mono disabled:opacity-50"
            />
            {input && (
              <button
                onClick={handleLimpiar}
                tabIndex={-1}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
              >
                <X size={16} />
              </button>
            )}
          </div>
          <button
            onClick={handleConsultar}
            disabled={!input.trim() || estado.tipo === 'cargando'}
            className="px-5 py-3 bg-[#003087] text-white text-sm font-semibold rounded-xl hover:bg-[#002070] disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            Consultar
          </button>
        </div>

        {/* Scanner status hint */}
        <p className="text-xs text-slate-400 mt-2 flex items-center gap-1">
          <span className={`w-1.5 h-1.5 rounded-full ${estado.tipo === 'cargando' ? 'bg-amber-400' : 'bg-green-400'}`} />
          {estado.tipo === 'cargando' ? 'Consultando...' : 'Listo para escanear'}
        </p>
      </div>

      {/* Loading */}
      {estado.tipo === 'cargando' && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8 flex items-center justify-center gap-3">
          <div className="w-6 h-6 border-4 border-[#003087]/20 border-t-[#003087] rounded-full animate-spin flex-shrink-0" />
          <p className="text-slate-500 text-sm">Consultando medidor...</p>
        </div>
      )}

      {/* Not found */}
      {estado.tipo === 'no_encontrado' && (
        <div className="bg-white rounded-2xl border border-orange-100 shadow-sm p-6 flex items-start gap-4">
          <AlertCircle size={24} className="text-orange-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-slate-800">Medidor no encontrado</p>
            <p className="text-slate-500 text-sm mt-0.5">
              No existe el medidor{' '}
              <span className="font-mono font-bold text-slate-700">"{estado.numero}"</span>{' '}
              en la base de datos.
            </p>
          </div>
          <button
            onClick={handleLimpiar}
            tabIndex={-1}
            className="ml-auto p-1.5 text-slate-400 hover:text-slate-600 flex-shrink-0"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Error */}
      {estado.tipo === 'error' && (
        <div className="bg-red-50 rounded-2xl border border-red-100 p-5 flex items-start gap-3">
          <AlertCircle size={18} className="text-red-500 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-red-700 text-sm">Error al consultar</p>
            <p className="text-red-600 text-sm mt-0.5">{estado.mensaje}</p>
          </div>
        </div>
      )}

      {/* Result card */}
      {estado.tipo === 'encontrado' && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          {/* Card header */}
          <div className="bg-[#003087] px-6 py-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center">
                <Gauge size={20} className="text-white" />
              </div>
              <div>
                <p className="text-white/70 text-xs font-medium uppercase tracking-wide">Medidor</p>
                <p className="text-white font-bold text-xl font-mono leading-tight">
                  {estado.medidor.medidorNumber}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={copiarInfo}
                tabIndex={-1}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-white/20 hover:bg-white/30 text-white text-xs font-semibold rounded-lg transition-colors"
              >
                {copiado ? <CheckCircle2 size={14} /> : <Copy size={14} />}
                {copiado ? 'Copiado' : 'Copiar'}
              </button>
              <button
                onClick={handleLimpiar}
                tabIndex={-1}
                className="p-1.5 bg-white/20 hover:bg-white/30 text-white rounded-lg transition-colors"
                title="Limpiar"
              >
                <X size={14} />
              </button>
            </div>
          </div>

          {/* Subregion badge */}
          {estado.medidor.subregion && (
            <div className="px-6 py-2 bg-blue-50 border-b border-slate-100 flex items-center gap-2">
              <span className="text-xs text-slate-500">Subregión:</span>
              <span className="text-xs font-bold text-[#003087] bg-blue-100 px-2 py-0.5 rounded-full">
                {estado.medidor.subregion}
              </span>
            </div>
          )}

          {/* Fields */}
          <div className="px-6 py-4 divide-y divide-slate-100">
            <Campo label="Cliente" valor={estado.medidor.cliente} />
            <Campo label="Localización" valor={estado.medidor.localizacion} />
            <Campo label="Calle" valor={estado.medidor.calle} />
            <Campo label="Poste" valor={estado.medidor.poste} />
            <Campo label="Metros al Poste" valor={estado.medidor.metros} />
            <Campo label="Pueblo (código)" valor={estado.medidor.pueblo} />
          </div>
        </div>
      )}
    </div>
  )
}
