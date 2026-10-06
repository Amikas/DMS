import { Badge } from '@/components/ui/badge'
import type { DocumentStatus } from '../types'

const LABELS: Record<DocumentStatus, string> = {
  UPLOADED: 'Uploaded',
  PROCESSING: 'Processing',
  OCR_COMPLETE: 'OCR complete',
  SUMMARY_GENERATED: 'Summary ready',
  ARCHIVED: 'Archived',
}

const VARIANTS: Record<DocumentStatus, 'secondary' | 'warning' | 'success' | 'outline'> = {
  UPLOADED: 'secondary',
  PROCESSING: 'warning',
  OCR_COMPLETE: 'success',
  SUMMARY_GENERATED: 'success',
  ARCHIVED: 'outline',
}

export default function StatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <Badge variant={VARIANTS[status] ?? 'secondary'}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
      {LABELS[status] ?? status}
    </Badge>
  )
}
