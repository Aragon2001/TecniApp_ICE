import React, { useState, useMemo } from 'react'
import { Package, Edit2, Check, X, Plus, Minus } from 'lucide-react'
import { useInventario, updateCantidadInventario } from '../hooks/useInventario'
import { useVehiculos } from '../hooks/useVehiculos'
import { useAuth } from '../context/AuthContext'
import toast from 'react-hot-toast'

export default function Inventario() {
  const { user } = useAuth()
  const esGestor = user?.rol === 'supervisor' || user?.rol === 'admin'
  const { inventario, loading } = useInventario()
  const { vehiculos } = useVehiculos()
  const [selectedVehiculo, setSelectedVehiculo] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editQty, setEditQty] = useState(0)
  const [saving, setSaving] = useState(false)

  const vehiculoIds = useMemo(() => {
    const set = new Set(inventario.map(i => i.vehiculoId).filter((id): id is string => Boolean(id)))
    return Array.from(set).sort()
  }, [inventario])

  React.useEffect(() => {
    if (!selectedVehiculo && vehiculoIds.length > 0) {
      setSelectedVehiculo(vehiculoIds[0])
    }
  }, [vehiculoIds, selectedVehiculo])

  const vehiculoItems = useMemo(() =>
    inventario
      .filter(i => i.vehiculoId === selectedVehiculo)
      .sort((a, b) => (a.codigoMaterial || '').localeCompare(b.codigoMaterial || '', 'es')),
    [inventario, selectedVehiculo]
  )

  const totalItems = vehiculoItems.length
  const totalCantidad = vehiculoItems.reduce((sum, i) => sum + (i.cantidadDisponible ?? 0), 0)
  const lowStock = vehiculoItems.filter(i => (i.cantidadDisponible ?? 0) > 0 && (i.cantidadDisponible ?? 0) < 5).length
  const zeroStock = vehiculoItems.filter(i => (i.cantidadDisponible ?? 0) === 0).length

  const getPlaca = (vid: string) => {
    const v = vehiculos.find(v => v.vehiculoId === vid || v.placa === vid || v.placaRaw === vid)
    return v ? v.placa : vid
  }

  function startEdit(item: any) {
    setEditingId(item.id)
    setEditQty(item.cantidadDisponible ?? 0)
  }

  async function confirmEdit(item: any) {
    if (editQty < 0) { toast.error('La cantidad no puede ser negativa'); return }
    setSaving(true)
    try {
      await updateCantidadInventario(item.vehiculoId, item.codigoMaterial, editQty)
      toast.success('Cantidad actualizada')
      setEditingId(null)
    } catch {
      toast.error('Error al actualizar cantidad')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-bold text-slate-800">Inventario de Materiales</h2>
        <p className="text-slate-500 text-sm mt-0.5">
          {loading ? 'Cargando...' : `${inventario.length} ítems en ${vehiculoIds.length} vehículos`}
        </p>
      </div>

      {/* Vehicle tabs */}
      {vehiculoIds.length > 0 && (
        <div className="flex gap-2 overflow-x-auto pb-2">
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

      {selectedVehiculo && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: 'Total Ítems', value: totalItems, className: 'bg-white border-slate-100' },
            { label: 'Total Unidades', value: totalCantidad, className: 'bg-white border-slate-100' },
            { label: 'Stock Bajo (<5)', value: lowStock, className: 'bg-amber-50 border-amber-100 text-amber-700' },
            { label: 'Sin Stock', value: zeroStock, className: 'bg-red-50 border-red-100 text-red-700' },
          ].map(({ label, value, className }) => (
            <div key={label} className={`rounded-xl border p-4 text-center ${className}`}>
              <p className="text-2xl font-bold">{value}</p>
              <p className="text-xs mt-1 opacity-70">{label}</p>
            </div>
          ))}
        </div>
      )}

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
                  {esGestor && <th className="px-4 py-3 text-center text-xs font-semibold text-slate-500 uppercase tracking-wider">Editar</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {vehiculoItems.map((item) => {
                  const qty = item.cantidadDisponible ?? 0
                  const isLow = qty > 0 && qty < 5
                  const isZero = qty === 0
                  const isEditing = editingId === item.id

                  return (
                    <tr
                      key={item.id}
                      className={`transition-colors ${isZero ? 'bg-red-50/50 hover:bg-red-50' : isLow ? 'bg-amber-50/50 hover:bg-amber-50' : 'hover:bg-slate-50'}`}
                    >
                      <td className="px-4 py-2.5 font-mono text-xs text-slate-600">{item.codigoMaterial ?? item.id}</td>
                      <td className="px-4 py-2.5 text-slate-700 text-xs">{item.descripcionMaterial ?? '—'}</td>
                      <td className="px-4 py-2.5 text-right">
                        {isEditing ? (
                          <div className="flex items-center justify-end gap-1">
                            <button
                              onClick={() => setEditQty(q => Math.max(0, q - 1))}
                              className="p-1 text-slate-500 hover:bg-slate-100 rounded"
                            >
                              <Minus size={12} />
                            </button>
                            <input
                              type="number"
                              min={0}
                              value={editQty}
                              onChange={e => setEditQty(Number(e.target.value))}
                              className="w-16 text-center border border-slate-200 rounded px-2 py-1 text-sm font-bold focus:outline-none focus:ring-2 focus:ring-[#003087]/20"
                            />
                            <button
                              onClick={() => setEditQty(q => q + 1)}
                              className="p-1 text-slate-500 hover:bg-slate-100 rounded"
                            >
                              <Plus size={12} />
                            </button>
                          </div>
                        ) : (
                          <span className={`font-bold ${isZero ? 'text-red-600' : isLow ? 'text-amber-600' : 'text-slate-800'}`}>
                            {qty}
                          </span>
                        )}
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
                      {esGestor && (
                        <td className="px-4 py-2.5 text-center">
                          {isEditing ? (
                            <div className="flex items-center justify-center gap-1">
                              <button
                                onClick={() => confirmEdit(item)}
                                disabled={saving}
                                className="p-1.5 bg-green-100 text-green-700 hover:bg-green-200 rounded transition-colors"
                              >
                                <Check size={14} />
                              </button>
                              <button
                                onClick={() => setEditingId(null)}
                                className="p-1.5 bg-slate-100 text-slate-500 hover:bg-slate-200 rounded transition-colors"
                              >
                                <X size={14} />
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => startEdit(item)}
                              className="p-1.5 text-[#0066CC] hover:bg-blue-50 rounded transition-colors"
                            >
                              <Edit2 size={14} />
                            </button>
                          )}
                        </td>
                      )}
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
