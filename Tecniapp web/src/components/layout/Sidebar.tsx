import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, AlertTriangle, Calendar, Gauge, MapPin,
  Lightbulb, Truck, Package, ClipboardList, BarChart2,
  Users, Settings, Zap, X, ChevronRight
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

interface NavItem {
  path: string;
  label: string;
  icon: React.ReactNode;
  adminOnly?: boolean;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

const navSections: NavSection[] = [
  {
    title: 'PRINCIPAL',
    items: [
      { path: '/', label: 'Dashboard', icon: <LayoutDashboard size={18} /> },
    ],
  },
  {
    title: 'OPERACIONES',
    items: [
      { path: '/averias', label: 'Averías', icon: <AlertTriangle size={18} /> },
      { path: '/programacion', label: 'Programación', icon: <Calendar size={18} /> },
    ],
  },
  {
    title: 'INFRAESTRUCTURA',
    items: [
      { path: '/medidores', label: 'Medidores', icon: <Gauge size={18} /> },
      { path: '/localizaciones', label: 'Localizaciones', icon: <MapPin size={18} /> },
      { path: '/luminarias', label: 'Luminarias', icon: <Lightbulb size={18} /> },
      { path: '/vehiculos', label: 'Vehículos', icon: <Truck size={18} /> },
    ],
  },
  {
    title: 'MATERIALES',
    items: [
      { path: '/inventario', label: 'Inventario', icon: <Package size={18} /> },
    ],
  },
  {
    title: 'RECURSOS HUMANOS',
    items: [
      { path: '/planillas', label: 'Planillas', icon: <ClipboardList size={18} /> },
    ],
  },
  {
    title: 'REPORTES',
    items: [
      { path: '/reportes', label: 'Reportes', icon: <BarChart2 size={18} /> },
    ],
  },
  {
    title: 'ADMINISTRACIÓN',
    items: [
      { path: '/usuarios', label: 'Usuarios', icon: <Users size={18} />, adminOnly: true },
      { path: '/admin', label: 'Admin', icon: <Settings size={18} />, adminOnly: true },
    ],
  },
];

const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  const { user } = useAuth();
  const isAdmin = user?.rol === 'admin';

  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={onClose}
        />
      )}

      {/* Sidebar */}
      <aside
        className={`
          fixed top-0 left-0 h-full w-64 z-50 flex flex-col
          bg-[#001B52] transition-transform duration-300 ease-in-out
          lg:translate-x-0 lg:static lg:z-auto
          ${isOpen ? 'translate-x-0' : '-translate-x-full'}
        `}
      >
        {/* Logo area */}
        <div className="flex items-center justify-between px-4 py-5 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-[#FFB800] rounded-lg flex items-center justify-center flex-shrink-0">
              <Zap size={20} className="text-[#001B52]" />
            </div>
            <div>
              <p className="text-white font-bold text-sm leading-tight">TecniApp ICE</p>
              <p className="text-slate-400 text-xs leading-tight">Sistema de Gestión</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="lg:hidden text-slate-400 hover:text-white transition-colors p-1 rounded"
          >
            <X size={18} />
          </button>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto py-4 scrollbar-thin scrollbar-thumb-white/10">
          {navSections.map((section) => {
            const visibleItems = section.items.filter(
              (item) => !item.adminOnly || isAdmin
            );
            if (visibleItems.length === 0) return null;

            return (
              <div key={section.title} className="mb-6">
                <p className="px-4 mb-1.5 text-[10px] font-semibold tracking-widest text-slate-500 uppercase">
                  {section.title}
                </p>
                <ul>
                  {visibleItems.map((item) => (
                    <li key={item.path}>
                      <NavLink
                        to={item.path}
                        end={item.path === '/'}
                        onClick={() => {
                          if (window.innerWidth < 1024) onClose();
                        }}
                        className={({ isActive }) =>
                          `flex items-center gap-3 px-4 py-2.5 mx-2 rounded-lg text-sm font-medium transition-all duration-150 group relative ${
                            isActive
                              ? 'bg-[#0066CC]/20 text-white border-l-2 border-[#0066CC] pl-3.5'
                              : 'text-slate-400 hover:bg-white/5 hover:text-white border-l-2 border-transparent pl-3.5'
                          }`
                        }
                      >
                        <span className="flex-shrink-0">{item.icon}</span>
                        <span className="flex-1">{item.label}</span>
                        <ChevronRight
                          size={14}
                          className="opacity-0 group-hover:opacity-100 transition-opacity text-slate-500"
                        />
                      </NavLink>
                    </li>
                  ))}
                </ul>
              </div>
            );
          })}
        </nav>

        {/* Version */}
        <div className="px-4 py-3 border-t border-white/10">
          <p className="text-xs text-slate-600 text-center">v1.0.0 — TecniApp ICE</p>
        </div>
      </aside>
    </>
  );
};

export default Sidebar;
