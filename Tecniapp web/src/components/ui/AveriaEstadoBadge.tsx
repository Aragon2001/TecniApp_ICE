import React from 'react';

type AveriaEstado = 'PENDIENTE' | 'ASIGNADA' | 'EN_ATENCION' | 'RESUELTA' | 'ANULADA';

interface AveriaEstadoBadgeProps {
  estado: AveriaEstado | string;
  size?: 'sm' | 'md';
}

const estadoConfig: Record<string, { label: string; className: string; dot: string }> = {
  PENDIENTE: {
    label: 'Pendiente',
    className: 'bg-amber-50 text-amber-700 border border-amber-200',
    dot: 'bg-amber-500',
  },
  ASIGNADA: {
    label: 'Asignada',
    className: 'bg-blue-50 text-blue-700 border border-blue-200',
    dot: 'bg-blue-500',
  },
  EN_ATENCION: {
    label: 'En Atención',
    className: 'bg-orange-50 text-orange-700 border border-orange-200',
    dot: 'bg-orange-500',
  },
  RESUELTA: {
    label: 'Resuelta',
    className: 'bg-green-50 text-green-700 border border-green-200',
    dot: 'bg-green-500',
  },
  ANULADA: {
    label: 'Anulada',
    className: 'bg-gray-100 text-gray-500 border border-gray-200',
    dot: 'bg-gray-400',
  },
};

const sizeClasses = {
  sm: 'px-1.5 py-0.5 text-[10px]',
  md: 'px-2.5 py-1 text-xs',
};

const AveriaEstadoBadge: React.FC<AveriaEstadoBadgeProps> = ({ estado, size = 'md' }) => {
  const config = estadoConfig[estado] || {
    label: estado,
    className: 'bg-slate-100 text-slate-600 border border-slate-200',
    dot: 'bg-slate-400',
  };

  return (
    <span
      className={`
        inline-flex items-center gap-1.5 font-semibold rounded-full
        ${config.className}
        ${sizeClasses[size]}
      `}
    >
      <span className={`w-1.5 h-1.5 rounded-full flex-shrink-0 ${config.dot}`} />
      {config.label}
    </span>
  );
};

export { AveriaEstadoBadge };
export default AveriaEstadoBadge;
