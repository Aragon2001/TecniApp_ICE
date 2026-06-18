import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Mail, Lock, Zap, CheckCircle2 } from 'lucide-react'
import { toast } from 'react-hot-toast'
import { useAuth } from '../context/AuthContext'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email, password)
      toast.success('Bienvenido a TecniApp ICE')
      navigate('/')
    } catch (err: any) {
      const msg = err?.code === 'auth/invalid-credential' || err?.code === 'auth/wrong-password'
        ? 'Credenciales incorrectas. Verifique su email y contraseña.'
        : err?.code === 'auth/user-not-found'
        ? 'Usuario no encontrado.'
        : err?.code === 'auth/too-many-requests'
        ? 'Demasiados intentos. Intente más tarde.'
        : 'Error al iniciar sesión. Intente de nuevo.'
      setError(msg)
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const features = [
    'Gestión de Averías en Tiempo Real',
    'Control de Flota y Vehículos',
    'Reportes y Analytics',
  ]

  return (
    <div className="min-h-screen flex">
      {/* Left side - branding */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-[#001B52] via-[#003087] to-[#0066CC] flex-col items-center justify-center p-12 relative overflow-hidden">
        {/* Background decoration */}
        <div className="absolute inset-0 overflow-hidden">
          <div className="absolute top-0 right-0 w-96 h-96 bg-white/5 rounded-full -translate-y-1/2 translate-x-1/2" />
          <div className="absolute bottom-0 left-0 w-80 h-80 bg-white/5 rounded-full translate-y-1/2 -translate-x-1/2" />
          <div className="absolute top-1/2 left-1/2 w-64 h-64 bg-[#FFB800]/10 rounded-full -translate-x-1/2 -translate-y-1/2" />
        </div>

        <div className="relative z-10 max-w-md text-center">
          {/* Logo */}
          <div className="flex items-center justify-center mb-8">
            <div className="w-24 h-24 bg-[#FFB800] rounded-3xl flex items-center justify-center shadow-2xl">
              <Zap size={48} className="text-[#001B52]" />
            </div>
          </div>

          <h1 className="text-5xl font-extrabold text-white mb-3 tracking-tight">
            TecniApp ICE
          </h1>
          <p className="text-blue-200 text-lg mb-2 font-medium">
            Sistema de Gestión de Operaciones de Campo
          </p>
          <p className="text-blue-300 text-sm mb-10">
            Instituto Costarricense de Electricidad
          </p>

          {/* Features */}
          <div className="space-y-4 text-left">
            {features.map((feature) => (
              <div key={feature} className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-[#FFB800]/20 flex items-center justify-center flex-shrink-0">
                  <CheckCircle2 size={16} className="text-[#FFB800]" />
                </div>
                <span className="text-blue-100 text-sm font-medium">{feature}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right side - login form */}
      <div className="flex-1 flex flex-col items-center justify-center p-6 bg-white">
        <div className="w-full max-w-md">
          {/* Mobile logo */}
          <div className="lg:hidden flex items-center justify-center gap-3 mb-8">
            <div className="w-12 h-12 bg-[#FFB800] rounded-xl flex items-center justify-center">
              <Zap size={24} className="text-[#001B52]" />
            </div>
            <div>
              <p className="text-xl font-bold text-[#003087]">TecniApp ICE</p>
              <p className="text-xs text-slate-500">Sistema de Gestión</p>
            </div>
          </div>

          <div className="mb-8">
            <h2 className="text-3xl font-bold text-slate-800 mb-2">Iniciar Sesión</h2>
            <p className="text-slate-500 text-sm">Instituto Costarricense de Electricidad</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Email */}
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                Correo Electrónico
              </label>
              <div className="relative">
                <Mail size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="usuario@ice.go.cr"
                  required
                  className="w-full pl-10 pr-4 py-3 border border-slate-200 rounded-xl text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] transition-colors"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                Contraseña
              </label>
              <div className="relative">
                <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  className="w-full pl-10 pr-12 py-3 border border-slate-200 rounded-xl text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-[#003087]/20 focus:border-[#003087] transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 transition-colors"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </div>

            {/* Error */}
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-xl p-3">
                <p className="text-red-700 text-sm">{error}</p>
              </div>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 px-6 bg-[#003087] hover:bg-[#002266] text-white font-semibold rounded-xl text-sm transition-colors focus:outline-none focus:ring-2 focus:ring-[#003087]/50 focus:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Iniciando sesión...
                </>
              ) : (
                'Iniciar Sesión'
              )}
            </button>
          </form>

          <p className="text-center text-xs text-slate-400 mt-8">
            TecniApp ICE v1.0 • © 2024 ICE
          </p>
        </div>
      </div>
    </div>
  )
}
