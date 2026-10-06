import { Skeleton } from '@/components/ui/skeleton'

export default function TableSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="overflow-hidden rounded-xl border bg-card" aria-hidden="true">
      {Array.from({ length: rows }).map((_, index) => (
        <div
          key={index}
          className="flex items-center gap-4 border-b px-4 py-3.5 last:border-b-0"
        >
          <Skeleton className="h-4 w-[38%]" />
          <Skeleton className="ml-auto h-4 w-16" />
          <Skeleton className="h-5 w-20" />
        </div>
      ))}
    </div>
  )
}
