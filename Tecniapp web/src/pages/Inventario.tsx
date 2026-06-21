import React, { useState, useMemo } from 'react'
import { Package } from 'lucide-react'
import { useInventario } from '../hooks/useInventario'
import { useVehiculos } from '../hooks/useVehiculos'

export default function Inventario() {
  const { inventario, loading } = useInventario()
  const { vehiculos } = useVehiculos()
  const [selectedVehiculo, setSelectedVehiculo] = useState('')

  // Get unique vehiculo IDs from inventory
  const vehiculoIds = useMemo(() => {
    const set = new Set(inventario.map(i => i.vehiculoId).filter((id): id is string => Boolean(id)))
    return Array.from(set).sort()
  }, [inventario])

  // Set first vehiculo as default when data loads
  React.useEffect(() => {
    if (!selectedVehiculo && vehiculoIds.length > 0) {
      setSelectedVehiculo(vehiculoIds[0])
    }
  }, [vehiculoIds, selectedVehiculo])

  const vehiculoItems = useMemo(() =>
    inventario.filter(i => i.vehiculoId === selectedVehiculo)
      .sort((a, b) => (a.codigoMaterial || '').localeCompare(b.codigoMaterial || '', 'es')),
    [inventario, selectedVehiculo]
  )

  const totalItems = vehiculoItems.length
  const totalCantidad = vehiculoItems.reduce((sum, i) => sum + (i.cantidadDisponible ?? 0), 0)
  const lowStock = vehiculoItems.filter(i => (i.cantidadDisponible ?? 0) > 0 && (i.cantidadDisponible ?? 0) < 5).length
  const zeroStock = vehiculoItems.filter(i => (i.cantidadDisponible ?? 0) === 0).length

  // Get placa for a vehiculoId
  const getPlaca = (vid: string) => {
    const v = vehiculos.find(v => v.vehiculoId === vid || v.placa === vid || v.placaRaw === vid)
    return v ? v.placa : vid
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Inventario de Materiales</h2>
        <p className="text-slate-500 text-sm mt-0.5">
          {loading ? 'Cargando...' : `${inventario.length} ítems en ${vehiculoIds.length} vehículos`}
        </p>
      </div>

      {/* Vehicle tabs */}
      {vehiculoIds.length > 0 && (
        <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-thin">
          {vehiculoIds.map(vid => (
            <button
              key={vid}
              onClick={() => setSelectedVehiculo(vid)}
              className={`flex-shrink-0 px-4 py-2 rounded-lg text-sm font-semibold transition-all ${
                selectedVehiculo === vid
                  ? 'bg-[#003087] text-white shadow-sm'
                  : 'bg-white text-slate-600 border border-slate-200 hover:border-[#003087] hover:text-[#003087]'
              }`}
            >
              {getPlaca(vid)}
            </button>
          ))}
        </div>
      )}

      {/* Stats */}
      {selectedVehiculo && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div className="bg-white rounded-xl border border-slate-100 p-4 text-center">
            <p className="text-2xl font-bold text-slate-800">{totalItems}</p>
            <p className="text-xs text-slate-500 mt-1">Total Ítems</p>
          </div>
          <div className="bg-white rounded-xl border border-slate-100 p-4 text-center">
            <p className="text-2xl font-bold text-slate-800">{totalCantidad}</p>
            <p className="text-xs text-slate-500 mt-1">Total Materiales</p>
          </div>
          <div className="bg-amber-50 rounded-xl border border-amber-100 p-4 text-center">
            <p className="text-2xl font-bold text-amber-700">{lowStock}</p>
            <p className="text-xs text-amber-600 mt-1">Stock Bajo (&lt;5)</p>
          </div>
          <div className="bg-red-50 rounded-xl border border-red-100 p-4 text-center">
            <p className="text-2xl font-bold text-red-700">{zeroStock}</p>
            <p className="text-xs text-red-600 mt-1">Sin Stock</p>
          </div>
        </div>
      )}

      {/* Table */}
      {loading ? (
        <div className="bg-white rounded-xl border border-slate-100 p-5 space-y-3 animate-pulse">
          {[1,2,3,4,5,6].map(i => <div key={i} className="h-10 bg-slate-100 rounded-lg" />)}
        </div>
      ) : !selectedVehiculo ? (
        <div className="bg-white rounded-xl border border-slate-100 p-16 text-center">
          <Package size={40} className="text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">Seleccione un vehículo</p>
        </div>
      ) : vehiculoItems.length === 0 ? (
        <div className="bg-white rounded-xl border border-slate-100 p-16 text-center">
          <Package size={40} className="text-slate-300 mx-auto mb-3" />
          <p className="text-slate-500 font-medium">Sin inventario para este vehículo</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-100">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Código</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Descripción</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-slate-500 uppercase tracking-wider">Cantidad</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-slate-500 uppercase tracking-wider">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {vehiculoItems.map((item) => {
                  const qty = item.cantidadDisponible ?? 0
                  const isLow = qty > 0 && qty < 5
                  const isZero = qty === 0
                  return (
                    <tr
                      key={item.id}
                      className={`transition-colors ${isZero ? 'bg-red-50/50 hover:bg-red-50' : isLow ? 'bg-amber-50/50 hover:bg-amber-50' : 'hover:bg-slate-50'}`}
                    >
                      <td className="px-4 py-2.5 font-mono text-xs text-slate-600">{item.codigoMaterial ?? item.id}</td>
                      <td className="px-4 py-2.5 text-slate-700 text-xs">{item.descripcionMaterial ?? '—'}</td>
                      <td className={`px-4 py-2.5 text-right font-bold ${isZero ? 'text-red-600' : isLow ? 'text-amber-600' : 'text-slate-800'}`}>
                        {qty}
                      </td>
                      <td className="px-4 py-2.5">
                        {isZero ? (
                          <span className="text-[10px] bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-semibold">Sin stock</span>
                        ) : isLow ? (
                          <span className="text-[10px] bg-amber-100 text-amber-700 px-2 py-0.5 rounded-full font-semibold">Stock bajo</span>
                        ) : (
                          <span className="text-[10px] bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-semibold">OK</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
