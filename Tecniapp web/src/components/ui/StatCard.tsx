import React from 'react';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  change?: string;
  changeType?: 'up' | 'down' | 'neutral';
  icon: React.ReactNode;
  iconBg?: string;
  trend?: number[];
  color?: string;
  subtitle?: string;
}

// Tiny sparkline SVG
const Sparkline: React.FC<{ data: number[]; color: string }> = ({ data, color }) => {
  if (!data || data.length < 2) return null;
  const width = 80;
  const height = 32;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const step = width / (data.length - 1);

  const points = data
    .map((v, i) => {
      const x = i * step;
      const y = height - ((v - min) / range) * (height - 4) - 2;
      return `${x},${y}`;
    })
    .join(' ');

  return (
    <svg width={width} height={height} className="overflow-visible">
      <polyline
        points={points}
        fill="none"
        stroke={color}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity="0.7"
      />
      {/* Gradient fill */}
      <defs>
        <linearGradient id={`grad-${color}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon
        points={`0,${height} ${points} ${(data.length - 1) * step},${height}`}
        fill={`url(#grad-${color})`}
      />
    </svg>
  );
};

const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  change,
  changeType = 'neutral',
  icon,
  iconBg = '#EBF2FF',
  trend,
  color = '#003087',
  subtitle,
}) => {
  const changeIcons = {
    up: <TrendingUp size={12} />,
    down: <TrendingDown size={12} />,
    neutral: <Minus size={12} />,
  };

  const changeColors = {
    up: 'text-green-600 bg-green-50',
    down: 'text-red-600 bg-red-50',
    neutral: 'text-slate-500 bg-slate-100',
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-slate-100 p-5 hover:shadow-md transition-shadow duration-200">
      <div className="flex items-start justify-between gap-3">
        {/* Icon */}
        <div
          className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ backgroundColor: iconBg }}
        >
          <span style={{ color }}>{icon}</span>
        </div>

        {/* Sparkline */}
        {trend && trend.length > 1 && (
          <div className="flex-shrink-0 opacity-70">
            <Sparkline data={trend} color={color} />
          </div>
        )}
      </div>

      <div className="mt-4">
        <p className="text-2xl font-bold text-slate-800 leading-none">{value}</p>
        <p className="text-sm text-slate-500 mt-1 leading-tight">{title}</p>
        {subtitle && <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>}
      </div>

      {change && (
        <div className="mt-3">
          <span
            className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${changeColors[changeType]}`}
          >
            {changeIcons[changeType]}
            {change}
          </span>
        </div>
      )}
    </div>
  );
};

export default StatCard;
