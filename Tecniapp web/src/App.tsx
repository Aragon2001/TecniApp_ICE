import React, { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'react-hot-toast'
import toast from 'react-hot-toast'
import { AuthProvider, useAuth } from './context/AuthContext'
import Layout from './components/layout/Layout'
import { LoadingScreen } from './components/ui/Spinner'
import { queryClient } from './lib/queryClient'
import { processQueue } from './lib/syncQueue'

// Import all pages
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Averias from './pages/Averias'
import AveriaDetail from './pages/AveriaDetail'
import Medidores from './pages/Medidores'
import Localizaciones from './pages/Localizaciones'
import Luminarias from './pages/Luminarias'
import Inventario from './pages/Inventario'
import Vehiculos from './pages/Vehiculos'
import VehiculoDetail from './pages/VehiculoDetail'
import Programacion from './pages/Programacion'
import Planillas from './pages/Planillas'
import Reportes from './pages/Reportes'
import Usuarios from './pages/Usuarios'
import Admin from './pages/Admin'
import Perfil from './pages/Perfil'

function SyncOnReconnect() {
  useEffect(() => {
    const handleOnline = async () => {
      toast.loading('Conexión restaurada. Sincronizando...', { id: 'sync-restore' })
      await processQueue()
      toast.dismiss('sync-restore')
    }
    const handleOffline = () => {
      toast('Sin conexión. Los cambios se guardarán localmente.', {
        icon: '📶',
        duration: 5000,
        id: 'offline-notice',
      })
    }

    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    if (navigator.onLine) processQueue()

    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  return null
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { firebaseUser, loading } = useAuth()
  if (loading) return <LoadingScreen />
  if (!firebaseUser) return <Navigate to="/login" replace />
  return <Layout>{children}</Layout>
}

function AppRoutes() {
  const { firebaseUser, loading } = useAuth()
  if (loading) return <LoadingScreen />
  return (
    <>
      {firebaseUser && <SyncOnReconnect />}
      <Routes>
        <Route path="/login" element={firebaseUser ? <Navigate to="/" replace /> : <Login />} />
        <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/averias" element={<ProtectedRoute><Averias /></ProtectedRoute>} />
        <Route path="/averias/:caseId" element={<ProtectedRoute><AveriaDetail /></ProtectedRoute>} />
        <Route path="/medidores" element={<ProtectedRoute><Medidores /></ProtectedRoute>} />
        <Route path="/localizaciones" element={<ProtectedRoute><Localizaciones /></ProtectedRoute>} />
        <Route path="/luminarias" element={<ProtectedRoute><Luminarias /></ProtectedRoute>} />
        <Route path="/inventario" element={<ProtectedRoute><Inventario /></ProtectedRoute>} />
        <Route path="/vehiculos" element={<ProtectedRoute><Vehiculos /></ProtectedRoute>} />
        <Route path="/vehiculos/:vehiculoId" element={<ProtectedRoute><VehiculoDetail /></ProtectedRoute>} />
        <Route path="/programacion" element={<ProtectedRoute><Programacion /></ProtectedRoute>} />
        <Route path="/planillas" element={<ProtectedRoute><Planillas /></ProtectedRoute>} />
        <Route path="/reportes" element={<ProtectedRoute><Reportes /></ProtectedRoute>} />
        <Route path="/usuarios" element={<ProtectedRoute><Usuarios /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute><Admin /></ProtectedRoute>} />
        <Route path="/perfil" element={<ProtectedRoute><Perfil /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <AppRoutes />
          <Toaster
            position="top-right"
            toastOptions={{
              duration: 4000,
              style: {
                background: '#1e293b',
                color: '#f8fafc',
                borderRadius: '10px',
                fontSize: '0.875rem',
                fontFamily: 'Inter, sans-serif',
              },
              success: { iconTheme: { primary: '#22c55e', secondary: '#fff' } },
              error: { iconTheme: { primary: '#ef4444', secondary: '#fff' } },
            }}
          />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
