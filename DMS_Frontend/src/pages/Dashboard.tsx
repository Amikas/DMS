import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowRightIcon,
  CaretDownIcon,
  CaretUpIcon,
  CaretUpDownIcon,
  FilesIcon,
  MagnifyingGlassIcon,
  PlusIcon,
  WarningCircleIcon,
  XIcon,
} from '@phosphor-icons/react'
import { toast } from 'sonner'

import { createDocument, listDocuments } from '../lib/api'
import { formatBytes, fileExtension, pluralize } from '../lib/format'
import { validateSearch } from '../lib/validation'
import type { DocumentRequest, DocumentResponse, DocumentStatus } from '../types'
import DocumentForm from '../components/DocumentForm'
import StatusBadge from '../components/StatusBadge'
import TableSkeleton from '../components/TableSkeleton'
import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from '@/components/ui/input-group'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'

type SortKey = 'title' | 'fileSize' | 'status'
type SortDir = 'asc' | 'desc'
type Sort = { key: SortKey; dir: SortDir } | null

const STATUS_ORDER: DocumentStatus[] = [
  'PROCESSING',
  'UPLOADED',
  'OCR_COMPLETE',
  'SUMMARY_GENERATED',
  'ARCHIVED',
]

interface SortHeaderProps {
  label: string
  column: SortKey
  sort: Sort
  onSort: (key: SortKey) => void
  className?: string
}

function SortHeader({ label, column, sort, onSort, className }: SortHeaderProps) {
  const active = sort?.key === column
  const ariaSort = active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined

  return (
    <TableHead aria-sort={ariaSort} className={className}>
      <Button variant="ghost" size="sm" className="-ml-2" onClick={() => onSort(column)}>
        {label}
        {active ? (
          sort.dir === 'asc' ? (
            <CaretUpIcon data-icon="inline-end" />
          ) : (
            <CaretDownIcon data-icon="inline-end" />
          )
        ) : (
          <CaretUpDownIcon data-icon="inline-end" />
        )}
      </Button>
    </TableHead>
  )
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [docs, setDocs] = useState<DocumentResponse[]>([])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [sort, setSort] = useState<Sort>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      setDocs(await listDocuments())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const searchError = validateSearch(query).query
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q || searchError) return docs
    return docs.filter(
      (d) =>
        d.title.toLowerCase().includes(q) ||
        (d.description ?? '').toLowerCase().includes(q),
    )
  }, [docs, query, searchError])

  const sorted = useMemo(() => {
    if (!sort) return filtered
    const direction = sort.dir === 'asc' ? 1 : -1
    return [...filtered].sort((a, b) => {
      switch (sort.key) {
        case 'title':
          return a.title.localeCompare(b.title) * direction
        case 'fileSize':
          return ((a.fileSize ?? -1) - (b.fileSize ?? -1)) * direction
        case 'status':
          return (STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status)) * direction
      }
    })
  }, [filtered, sort])

  function toggleSort(key: SortKey) {
    setSort((previous) =>
      previous && previous.key === key
        ? { key, dir: previous.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: 'asc' },
    )
  }

  async function handleCreate(data: DocumentRequest) {
    setCreating(true)
    try {
      const created = await createDocument(data)
      setDocs((prev) => [created, ...prev])
      setShowCreate(false)
      toast.success('Document created.')
    } finally {
      setCreating(false)
    }
  }

  const hasDocs = docs.length > 0

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-xl font-semibold tracking-tight">Documents</h1>
          <p className="text-sm text-muted-foreground">
            {loading
              ? 'Loading archive…'
              : hasDocs
                ? `${pluralize(docs.length, 'document')} in the archive`
                : 'The archive is empty'}
          </p>
        </div>
        <Button onClick={() => setShowCreate((value) => !value)}>
          {showCreate ? <XIcon data-icon="inline-start" /> : <PlusIcon data-icon="inline-start" />}
          {showCreate ? 'Close' : 'New document'}
        </Button>
      </div>

      {showCreate && (
        <Card>
          <CardHeader>
            <CardTitle>New document</CardTitle>
          </CardHeader>
          <CardContent>
            <DocumentForm pending={creating} onSubmit={handleCreate} />
          </CardContent>
        </Card>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <InputGroup className="max-w-sm">
          <InputGroupAddon>
            <MagnifyingGlassIcon />
          </InputGroupAddon>
          <InputGroupInput
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search title or description"
            aria-label="Search documents"
            maxLength={255}
            aria-invalid={Boolean(searchError)}
          />
          {query && (
            <InputGroupAddon align="inline-end">
              <InputGroupButton size="icon-xs" aria-label="Clear search" onClick={() => setQuery('')}>
                <XIcon />
              </InputGroupButton>
            </InputGroupAddon>
          )}
        </InputGroup>
        {query && !searchError && (
          <p className="text-sm text-muted-foreground">
            {filtered.length} of {docs.length} shown
          </p>
        )}
      </div>
      {searchError && (
        <p className="text-sm text-destructive" role="alert">
          {searchError}
        </p>
      )}

      {loading && <TableSkeleton rows={4} />}

      {error && (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Could not load the archive.</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
          <AlertAction>
            <Button variant="outline" size="sm" onClick={load}>
              Retry
            </Button>
          </AlertAction>
        </Alert>
      )}

      {!loading && !error && !hasDocs && (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FilesIcon />
            </EmptyMedia>
            <EmptyTitle>No documents yet</EmptyTitle>
            <EmptyDescription>
              Add the first document to start building the archive. Documents can later be grouped
              into collections.
            </EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button onClick={() => setShowCreate(true)}>
              <PlusIcon data-icon="inline-start" />
              New document
            </Button>
          </EmptyContent>
        </Empty>
      )}

      {!loading && !error && hasDocs && filtered.length === 0 && (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <MagnifyingGlassIcon />
            </EmptyMedia>
            <EmptyTitle>No matches</EmptyTitle>
            <EmptyDescription>
              Nothing in the archive matches “{query.trim()}”. Try a different term.
            </EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button variant="outline" onClick={() => setQuery('')}>
              Clear search
            </Button>
          </EmptyContent>
        </Empty>
      )}

      {!loading && !error && filtered.length > 0 && (
        <div className="overflow-hidden rounded-xl border bg-card">
          <Table className="table-fixed">
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <SortHeader label="Name" column="title" sort={sort} onSort={toggleSort} />
                <TableHead className="w-24">Type</TableHead>
                <SortHeader
                  label="Size"
                  column="fileSize"
                  sort={sort}
                  onSort={toggleSort}
                  className="w-28"
                />
                <SortHeader
                  label="Status"
                  column="status"
                  sort={sort}
                  onSort={toggleSort}
                  className="w-40"
                />
                <TableHead className="w-12">
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sorted.map((doc) => (
                <TableRow
                  key={doc.id}
                  className="cursor-pointer"
                  onClick={() => navigate(`/documents/${doc.id}`)}
                >
                  <TableCell>
                    <Link
                      to={`/documents/${doc.id}`}
                      className="font-medium transition-colors hover:text-primary"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <span className="block truncate">{doc.title}</span>
                    </Link>
                    {(doc.description || doc.fileName) && (
                      <p className="truncate text-xs text-muted-foreground">
                        {doc.description || doc.fileName}
                      </p>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{fileExtension(doc.fileName, doc.contentType)}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatBytes(doc.fileSize)}
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={doc.status} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      asChild
                      aria-label={`Open ${doc.title}`}
                      onClick={(event) => event.stopPropagation()}
                    >
                      <Link to={`/documents/${doc.id}`}>
                        <ArrowRightIcon />
                      </Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </section>
  )
}
