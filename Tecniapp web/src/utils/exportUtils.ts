import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import { format } from 'date-fns'
import { es } from 'date-fns/locale'

// ─── Excel export ─────────────────────────────────────────────────────────────

export interface SheetConfig {
  name: string
  headers: string[]
  rows: (string | number | null | undefined)[][]
}

/**
 * Generate and download a multi-sheet Excel file.
 * @param sheets Array of sheet configurations
 * @param filename Filename without extension
 */
export function exportToExcel(sheets: SheetConfig[], filename: string): void {
  const wb = XLSX.utils.book_new()

  sheets.forEach(({ name, headers, rows }) => {
    const data = [headers, ...rows]
    const ws = XLSX.utils.aoa_to_sheet(data)

    // Style header row bold
    const range = XLSX.utils.decode_range(ws['!ref'] ?? 'A1')
    for (let c = range.s.c; c <= range.e.c; c++) {
      const cellAddr = XLSX.utils.encode_cell({ r: 0, c })
      if (!ws[cellAddr]) continue
      ws[cellAddr].s = {
        font: { bold: true, color: { rgb: 'FFFFFF' } },
        fill: { fgColor: { rgb: '003087' } },
        alignment: { horizontal: 'center' },
      }
    }

    // Auto column widths
    const colWidths = headers.map((h, ci) => {
      const maxLen = Math.max(
        h.length,
        ...rows.map((r) => String(r[ci] ?? '').length)
      )
      return { wch: Math.min(maxLen + 2, 40) }
    })
    ws['!cols'] = colWidths

    XLSX.utils.book_append_sheet(wb, ws, name.slice(0, 31))
  })

  const dateStr = format(new Date(), 'yyyyMMdd_HHmm')
  XLSX.writeFile(wb, `${filename}_${dateStr}.xlsx`)
}

// ─── PDF export ───────────────────────────────────────────────────────────────

export interface PdfConfig {
  title: string
  subtitle?: string
  headers: string[]
  rows: (string | number | null | undefined)[][]
  orientation?: 'portrait' | 'landscape'
}

/**
 * Generate and download a PDF table report.
 */
export function exportToPdf(config: PdfConfig, filename: string): void {
  const { title, subtitle, headers, rows, orientation = 'landscape' } = config

  const doc = new jsPDF({ orientation, unit: 'mm', format: 'a4' })
  const pageWidth = doc.internal.pageSize.getWidth()
  const dateStr = format(new Date(), "dd/MM/yyyy HH:mm", { locale: es })

  // Header
  doc.setFillColor(0, 48, 135)
  doc.rect(0, 0, pageWidth, 18, 'F')
  doc.setTextColor(255, 255, 255)
  doc.setFontSize(14)
  doc.setFont('helvetica', 'bold')
  doc.text('TecniApp ICE', 10, 7)
  doc.setFontSize(10)
  doc.setFont('helvetica', 'normal')
  doc.text('Instituto Costarricense de Electricidad', 10, 13)

  // Title
  doc.setTextColor(0, 48, 135)
  doc.setFontSize(13)
  doc.setFont('helvetica', 'bold')
  doc.text(title, 10, 26)

  if (subtitle) {
    doc.setFontSize(9)
    doc.setFont('helvetica', 'normal')
    doc.setTextColor(100, 100, 100)
    doc.text(subtitle, 10, 32)
  }

  doc.setFontSize(8)
  doc.setTextColor(150, 150, 150)
  doc.text(`Generado: ${dateStr}`, pageWidth - 10, 26, { align: 'right' })

  // Table
  autoTable(doc, {
    head: [headers],
    body: rows.map((r) => r.map((cell) => cell ?? '')),
    startY: subtitle ? 36 : 30,
    styles: {
      fontSize: 8,
      cellPadding: 2,
    },
    headStyles: {
      fillColor: [0, 48, 135],
      textColor: 255,
      fontStyle: 'bold',
    },
    alternateRowStyles: {
      fillColor: [232, 240, 255],
    },
    margin: { left: 10, right: 10 },
  })

  // Footer
  const pageCount = (doc as any).internal.getNumberOfPages()
  for (let i = 1; i <= pageCount; i++) {
    doc.setPage(i)
    doc.setFontSize(7)
    doc.setTextColor(150)
    doc.text(
      `Página ${i} de ${pageCount} — TecniApp ICE`,
      pageWidth / 2,
      doc.internal.pageSize.getHeight() - 5,
      { align: 'center' }
    )
  }

  const dateFileStr = format(new Date(), 'yyyyMMdd_HHmm')
  doc.save(`${filename}_${dateFileStr}.pdf`)
}

// ─── Helpers for specific reports ─────────────────────────────────────────────

export function exportAveriasExcel(averias: any[]): void {
  exportToExcel(
    [
      {
        name: 'Averías',
        headers: [
          'Caso ID', 'Estado', 'Agencia', 'Clientes Afectados',
          'Localización', 'Causa', 'Técnico Asignado',
          'Fecha Inicio', 'Hora Llegada', 'Hora Final',
          'Tipo Afectación', 'Observaciones',
        ],
        rows: averias.map((a) => [
          a.id ?? a.caseId,
          a.estado,
          a.agencia ?? a.nombreAgencia,
          a.clientesAfectados,
          a.localizacion ?? a.direccion,
          a.causa ?? a.causaClor,
          a.tecnicoAsignadoNombre ?? a.tecnicoNombre,
          a.fechaInicioMillis ? new Date(a.fechaInicioMillis).toLocaleString('es-CR') : '',
          a.horaLlegadaMillis ? new Date(a.horaLlegadaMillis).toLocaleString('es-CR') : '',
          a.horaFinalMillis ? new Date(a.horaFinalMillis).toLocaleString('es-CR') : '',
          a.tipoAfectacion,
          a.observaciones,
        ]),
      },
    ],
    'Reporte_Averias'
  )
}

export function exportVehiculosExcel(vehiculos: any[]): void {
  exportToExcel(
    [
      {
        name: 'Vehículos',
        headers: [
          'ID', 'Placa', 'Tipo', 'Agencia', 'Subregión',
          'KM Actual', 'Orimetro', 'Próximo Mantenimiento', 'Estado',
        ],
        rows: vehiculos.map((v) => [
          v.vehiculoId ?? v.id,
          v.placa,
          v.tipo,
          v.agencia,
          v.subregion,
          v.kmActual,
          v.orimetroActual,
          v.mantenimientoProximo,
          v.estado,
        ]),
      },
    ],
    'Reporte_Vehiculos'
  )
}

export function exportUsuariosExcel(usuarios: any[]): void {
  exportToExcel(
    [
      {
        name: 'Usuarios',
        headers: [
          'UID', 'Nombre', 'Apellidos', 'Email', 'Cédula',
          'Teléfono', 'Rol', 'Región', 'Agencia', 'Vehículo',
        ],
        rows: usuarios.map((u) => [
          u.uid,
          u.nombre,
          u.apellidos,
          u.email,
          u.cedula,
          u.telefono,
          u.rol,
          u.regionNombre ?? u.region,
          u.agencia,
          u.placaVehiculo,
        ]),
      },
    ],
    'Reporte_Usuarios'
  )
}
