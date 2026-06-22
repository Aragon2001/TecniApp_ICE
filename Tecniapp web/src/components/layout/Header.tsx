import React, { useState, useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { Menu, Bell, LogOut, ChevronDown, User, Clock, Shield, WifiOff, RefreshCw } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useAverias } from '../../hooks/useAverias';
import { useNetworkStatus } from '../../hooks/useNetworkStatus';
import { usePendingCount } from '../../lib/syncQueue';

interface HeaderProps {
  onMenuToggle: () => void;
}

const routeTitles: Record<string, string> = {
  '/': 'Dashboard',
  '/averias': 'Averías',
  '/programacion': 'Programación',
  '/medidores': 'Medidores',
  '/localizaciones': 'Localizaciones',
  '/luminarias': 'Luminarias',
  '/vehiculos': 'Vehículos',
  '/inventario': 'Inventario',
  '/planillas': 'Planillas',
  '/reportes': 'Reportes',
  '/usuarios': 'Usuarios',
  '/admin': 'Administración',
};

const Header: React.FC<HeaderProps> = ({ onMenuToggle }) => {
  const location = useLocation();
  const { user, logout } = useAuth();
  const { averias } = useAverias({ estado: 'PENDIENTE' });
  const isOnline = useNetworkStatus();
  const pendingSync = usePendingCount();
  const [currentTime, setCurrentTime] = useState(new Date());
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const notifRef = useRef<HTMLDivElement>(null);

  const pageTitle = routeTitles[location.pathname] || 'TecniApp ICE';
  const pendingCount = averias?.length ?? 0;

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
        setNotificationsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const formattedTime = currentTime.toLocaleTimeString('es-CR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });

  const formattedDate = currentTime.toLocaleDateString('es-CR', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });

  const roleLabel: Record<string, string> = {
    admin: 'Administrador',
    supervisor: 'Supervisor',
    tecnico: 'Técnico',
  };

  return (
    <header className="bg-white shadow-sm border-t-2 border-t-[#FFB800] sticky top-0 z-30">
      <div className="flex items-center justify-between px-4 py-3">
        {/* Left: hamburger + title */}
        <div className="flex items-center gap-3">
          <button
            onClick={onMenuToggle}
            className="lg:hidden p-2 rounded-lg text-slate-500 hover:bg-gray-100 hover:text-slate-700 transition-colors"
          >
            <Menu size={20} />
          </button>
          <div>
            <h1 className="text-lg font-bold text-[#003087] leading-tight">{pageTitle}</h1>
            <p className="text-xs text-slate-400 leading-tight hidden sm:block">
              Instituto Costarricense de Electricidad
            </p>
          </div>
        </div>

        {/* Right: status chips, clock, notifications, user */}
        <div className="flex items-center gap-2">

          {/* Offline indicator */}
          {!isOnline && (
            <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 bg-amber-50 border border-amber-200 rounded-lg">
              <WifiOff size={12} className="text-amber-600" />
              <span className="text-xs font-medium text-amber-700">Sin conexión</span>
            </div>
          )}

          {/* Pending sync indicator */}
          {isOnline && pendingSync > 0 && (
            <div className="hidden sm:flex items-center gap-1.5 px-2.5 py-1 bg-blue-50 border border-blue-200 rounded-lg">
              <RefreshCw size={12} className="text-blue-600 animate-spin" />
              <span className="text-xs font-medium text-blue-700">
                {pendingSync} pendiente{pendingSync !== 1 ? 's' : ''}
              </span>
            </div>
          )}

          {/* Clock */}
          <div className="hidden md:flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg border border-slate-100">
            <Clock size={14} className="text-slate-400" />
            <div className="text-right">
              <p className="text-xs font-mono font-semibold text-slate-700 leading-tight">{formattedTime}</p>
              <p className="text-[10px] text-slate-400 leading-tight capitalize">{formattedDate}</p>
            </div>
          </div>

          {/* Notifications */}
          <div className="relative" ref={notifRef}>
            <button
              onClick={() => setNotificationsOpen(!notificationsOpen)}
              className="relative p-2 rounded-lg text-slate-500 hover:bg-gray-100 hover:text-slate-700 transition-colors"
            >
              <Bell size={20} />
              {pendingCount > 0 && (
                <span className="absolute top-1 right-1 min-w-[16px] h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center px-0.5">
                  {pendingCount > 99 ? '99+' : pendingCount}
                </span>
              )}
            </button>

            {notificationsOpen && (
              <div className="absolute right-0 mt-2 w-72 bg-white rounded-xl shadow-lg border border-slate-100 overflow-hidden">
                <div className="px-4 py-3 border-b border-slate-100 bg-slate-50">
                  <p className="text-sm font-semibold text-slate-700">Notificaciones</p>
                  <p className="text-xs text-slate-400">{pendingCount} avería(s) pendiente(s)</p>
                </div>
                <div className="max-h-64 overflow-y-auto">
                  {averias?.slice(0, 5).map((averia: any) => (
                    <div key={averia.id} className="px-4 py-3 hover:bg-slate-50 border-b border-slate-50 cursor-pointer">
                      <p className="text-xs font-medium text-slate-700 truncate">
                        {averia.descripcion || averia.tipoAveria || 'Avería pendiente'}
                      </p>
                      <p className="text-[10px] text-slate-400 mt-0.5">{averia.agencia || 'Sin agencia'}</p>
                    </div>
                  ))}
                  {(!averias || averias.length === 0) && (
                    <div className="px-4 py-6 text-center">
                      <p className="text-sm text-slate-400">Sin notificaciones pendientes</p>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* User dropdown */}
          <div className="relative" ref={dropdownRef}>
            <button
              onClick={() => setDropdownOpen(!dropdownOpen)}
              className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <div className="w-8 h-8 bg-[#003087] rounded-full flex items-center justify-center">
                <User size={14} className="text-white" />
              </div>
              <div className="hidden sm:block text-left">
                <p className="text-xs font-semibold text-slate-700 leading-tight max-w-[120px] truncate">
                  {user ? (`${user.nombre} ${user.apellidos}`.trim() || user.email.split('@')[0]) : 'Usuario'}
                </p>
                <p className="text-[10px] text-slate-400 leading-tight">
                  {roleLabel[user?.rol ?? 'tecnico'] || 'Técnico'}
                </p>
              </div>
              <ChevronDown size={14} className={`text-slate-400 transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
            </button>

            {dropdownOpen && (
              <div className="absolute right-0 mt-2 w-56 bg-white rounded-xl shadow-lg border border-slate-100 overflow-hidden">
                <div className="px-4 py-3 border-b border-slate-100 bg-slate-50">
                  <p className="text-sm font-semibold text-slate-700 truncate">
                    {user ? (`${user.nombre} ${user.apellidos}`.trim() || 'Usuario') : 'Usuario'}
                  </p>
                  <p className="text-xs text-slate-400 truncate">{user?.email}</p>
                  <div className="mt-1.5 flex items-center gap-1.5">
                    <Shield size={10} className="text-[#003087]" />
                    <span className="text-[10px] font-medium text-[#003087] bg-blue-50 px-1.5 py-0.5 rounded">
                      {roleLabel[user?.rol ?? 'tecnico'] || 'Técnico'}
                    </span>
                  </div>
                </div>
                <div className="py-1">
                  <button
                    onClick={() => { logout(); setDropdownOpen(false); }}
                    className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
                  >
                    <LogOut size={14} />
                    Cerrar Sesión
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
