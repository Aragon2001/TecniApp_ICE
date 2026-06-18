import React from 'react';
import { Zap } from 'lucide-react';

type SpinnerSize = 'sm' | 'md' | 'lg' | 'xl';

interface SpinnerProps {
  size?: SpinnerSize;
  className?: string;
}

const sizeMap: Record<SpinnerSize, { svg: number; border: string }> = {
  sm: { svg: 14, border: 'border-2' },
  md: { svg: 24, border: 'border-2' },
  lg: { svg: 36, border: 'border-[3px]' },
  xl: { svg: 48, border: 'border-4' },
};

const Spinner: React.FC<SpinnerProps> = ({ size = 'md', className = '' }) => {
  const { svg, border } = sizeMap[size];
  return (
    <span
      className={`inline-flex items-center justify-center ${className}`}
      style={{ width: svg, height: svg }}
    >
      <span
        className={`block rounded-full ${border} border-current border-t-transparent animate-spin`}
        style={{ width: svg, height: svg }}
      />
    </span>
  );
};

export { Spinner };
export default Spinner;

// Full-page loading screen
export const LoadingScreen: React.FC<{ message?: string }> = ({
  message = 'Cargando...',
}) => {
  return (
    <div className="fixed inset-0 bg-[#001B52] flex flex-col items-center justify-center z-[9999]">
      {/* Logo */}
      <div className="flex flex-col items-center gap-6">
        <div className="relative">
          <div className="w-16 h-16 bg-[#FFB800] rounded-2xl flex items-center justify-center shadow-lg">
            <Zap size={32} className="text-[#001B52]" />
          </div>
          {/* Orbit spinner */}
          <div className="absolute -inset-3 rounded-full border-2 border-white/20 border-t-[#FFB800] animate-spin" />
        </div>
        <div className="text-center">
          <p className="text-white font-bold text-xl">TecniApp ICE</p>
          <p className="text-slate-400 text-sm mt-1">Sistema de Gestión de Campo</p>
        </div>
        <div className="flex items-center gap-2 text-slate-300 text-sm">
          <Spinner size="sm" className="text-[#FFB800]" />
          <span>{message}</span>
        </div>
      </div>
    </div>
  );
};
