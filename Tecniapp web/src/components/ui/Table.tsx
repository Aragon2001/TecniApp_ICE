import React, { useState } from 'react';
import { ChevronUp, ChevronDown, Inbox } from 'lucide-react';

export interface Column<T = any> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
  sortable?: boolean;
  width?: string;
}

interface TableProps<T = any> {
  columns: Column<T>[];
  data: T[];
  loading?: boolean;
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
  keyExtractor?: (row: T) => string;
}

function Table<T extends Record<string, any>>({
  columns,
  data,
  loading = false,
  emptyMessage = 'No hay datos disponibles',
  onRowClick,
  keyExtractor,
}: TableProps<T>) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const sortedData = React.useMemo(() => {
    if (!sortKey) return data;
    return [...data].sort((a, b) => {
      const av = a[sortKey] ?? '';
      const bv = b[sortKey] ?? '';
      const cmp = String(av).localeCompare(String(bv), 'es', { numeric: true });
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [data, sortKey, sortDir]);

  return (
    <div className="w-full overflow-x-auto rounded-xl border border-slate-100">
      <table className="w-full text-sm text-left">
        <thead>
          <tr className="bg-slate-50 border-b border-slate-100">
            {columns.map((col) => (
              <th
                key={col.key}
                className={`px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide whitespace-nowrap ${
                  col.sortable ? 'cursor-pointer select-none hover:text-slate-700' : ''
                } ${col.width ? col.width : ''}`}
                onClick={col.sortable ? () => handleSort(col.key) : undefined}
              >
                <div className="flex items-center gap-1">
                  {col.header}
                  {col.sortable && (
                    <span className="flex flex-col gap-0.5 opacity-40">
                      <ChevronUp
                        size={10}
                        className={sortKey === col.key && sortDir === 'asc' ? 'opacity-100 text-[#003087]' : ''}
                      />
                      <ChevronDown
                        size={10}
                        className={sortKey === col.key && sortDir === 'desc' ? 'opacity-100 text-[#003087]' : ''}
                      />
                    </span>
                  )}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-50">
          {loading ? (
            // Skeleton rows
            Array.from({ length: 5 }).map((_, i) => (
              <tr key={i}>
                {columns.map((col) => (
                  <td key={col.key} className="px-4 py-3">
                    <div className="h-4 bg-slate-100 rounded animate-pulse" />
                  </td>
                ))}
              </tr>
            ))
          ) : sortedData.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-12 text-center">
                <div className="flex flex-col items-center gap-2 text-slate-400">
                  <Inbox size={32} className="opacity-40" />
                  <p className="text-sm">{emptyMessage}</p>
                </div>
              </td>
            </tr>
          ) : (
            sortedData.map((row, idx) => (
              <tr
                key={keyExtractor ? keyExtractor(row) : idx}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={`bg-white hover:bg-slate-50 transition-colors ${
                  onRowClick ? 'cursor-pointer' : ''
                }`}
              >
                {columns.map((col) => (
                  <td key={col.key} className="px-4 py-3 text-slate-700">
                    {col.render ? col.render(row) : row[col.key] ?? '—'}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default Table;
