import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
}

interface CardHeaderProps {
  title?: string;
  subtitle?: string;
  action?: React.ReactNode;
  className?: string;
  children?: React.ReactNode;
}

interface CardContentProps {
  children: React.ReactNode;
  className?: string;
  noPadding?: boolean;
}

interface CardFooterProps {
  children: React.ReactNode;
  className?: string;
}

export const Card: React.FC<CardProps> = ({ children, className = '' }) => {
  return (
    <div className={`bg-white rounded-xl shadow-sm border border-slate-100 overflow-hidden ${className}`}>
      {children}
    </div>
  );
};

export const CardHeader: React.FC<CardHeaderProps> = ({
  title,
  subtitle,
  action,
  className = '',
  children,
}) => {
  if (children) {
    return (
      <div className={`px-6 py-4 border-b border-slate-100 ${className}`}>
        {children}
      </div>
    );
  }

  return (
    <div className={`px-6 py-4 border-b border-slate-100 flex items-center justify-between gap-4 ${className}`}>
      <div className="min-w-0">
        {title && (
          <h3 className="text-base font-semibold text-slate-800 leading-tight truncate">{title}</h3>
        )}
        {subtitle && (
          <p className="text-xs text-slate-500 mt-0.5 leading-tight">{subtitle}</p>
        )}
      </div>
      {action && <div className="flex-shrink-0">{action}</div>}
    </div>
  );
};

export const CardContent: React.FC<CardContentProps> = ({
  children,
  className = '',
  noPadding = false,
}) => {
  return (
    <div className={`${noPadding ? '' : 'px-6 py-4'} ${className}`}>
      {children}
    </div>
  );
};

export const CardFooter: React.FC<CardFooterProps> = ({ children, className = '' }) => {
  return (
    <div className={`px-6 py-3 border-t border-slate-100 bg-slate-50/50 ${className}`}>
      {children}
    </div>
  );
};

export default Card;
