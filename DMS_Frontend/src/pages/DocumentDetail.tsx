import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PencilSimpleIcon, TrashIcon } from '@phosphor-icons/react'
import { toast } from 'sonner'

import { deleteDocument, getDocument, updateDocument } from '../lib/api'
import { formatBytes } from '../lib/format'
import type { DocumentRequest, DocumentResponse } from '../types'
import ConfirmDialog from '../components/ConfirmDialog'
import DocumentForm from '../components/DocumentForm'
import StatusBadge from '../components/StatusBadge'
import TableSkeleton from '../components/TableSkeleton'
import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert'
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export default function DocumentDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const docId = Number(id)

  const [doc, setDoc] = useState<DocumentResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const d = await getDocument(docId)
        if (!cancelled) {
          setDoc(d)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Load failed.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    if (Number.isInteger(docId)) {
      load()
    } else {
      setError('Invalid document id.')
      setLoading(false)
    }
    return () => {
      cancelled = true
    }
  }, [docId])

  async function handleUpdate(data: DocumentRequest) {
    setSaving(true)
    try {
      const updated = await updateDocument(docId, data)
      setDoc(updated)
      setEditing(false)
      toast.success('Document updated.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    setDeleting(true)
    try {
      await deleteDocument(docId)
      setConfirmingDelete(false)
      navigate('/')
      toast.success('Document deleted.')
    } catch (err) {
      toast.error(
        err instanceof Error && err.message ? err.message : 'Delete failed. Please try again.',
      )
    } finally {
      setDeleting(false)
      setConfirmingDelete(false)
    }
  }

  if (loading) {
    return (
      <section className="flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-xl font-semibold tracking-tight">Document</h1>
          <p className="text-sm text-muted-foreground">Loading…</p>
        </div>
        <TableSkeleton rows={2} />
      </section>
    )
  }

  if (error || !doc) {
    return (
      <section className="flex flex-col gap-6">
        <Breadcrumb>
          <BreadcrumbList>
            <BreadcrumbItem>
              <BreadcrumbLink asChild>
                <Link to="/">Documents</Link>
              </BreadcrumbLink>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
        <Alert variant="destructive">
          <AlertTitle>Could not load this document.</AlertTitle>
          <AlertDescription>{error ?? 'Not found.'}</AlertDescription>
          <AlertAction>
            <Button variant="outline" size="sm" onClick={() => navigate('/')}>
              Back to documents
            </Button>
          </AlertAction>
        </Alert>
      </section>
    )
  }

  return (
    <section className="flex flex-col gap-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link to="/">Documents</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>{doc.title}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-2">
          <h1 className="font-heading text-xl font-semibold tracking-tight">{doc.title}</h1>
          <StatusBadge status={doc.status} />
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            disabled={saving || deleting}
            onClick={() => setEditing((value) => !value)}
          >
            <PencilSimpleIcon data-icon="inline-start" />
            {editing ? 'Cancel edit' : 'Edit'}
          </Button>
          <Button
            variant="destructive"
            disabled={saving || deleting}
            onClick={() => setConfirmingDelete(true)}
          >
            <TrashIcon data-icon="inline-start" />
            {deleting ? 'Deleting…' : 'Delete'}
          </Button>
        </div>
      </div>

      {editing && (
        <Card>
          <CardHeader>
            <CardTitle>Edit document</CardTitle>
          </CardHeader>
          <CardContent>
            <DocumentForm
              initial={{
                title: doc.title,
                description: doc.description,
                filePath: doc.filePath,
                fileName: doc.fileName,
                fileSize: doc.fileSize,
                contentType: doc.contentType,
              }}
              pending={saving || deleting}
              onSubmit={handleUpdate}
            />
          </CardContent>
        </Card>
      )}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-6">
          {doc.description && (
            <Card>
              <CardHeader>
                <CardTitle>Description</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm leading-relaxed text-muted-foreground">{doc.description}</p>
              </CardContent>
            </Card>
          )}

          {doc.summary && (
            <Card>
              <CardHeader>
                <CardTitle>Summary</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm leading-relaxed text-muted-foreground">{doc.summary}</p>
              </CardContent>
            </Card>
          )}

          {doc.ocrText && (
            <Card>
              <CardHeader>
                <CardTitle>OCR text</CardTitle>
              </CardHeader>
              <CardContent>
                <pre className="max-h-80 overflow-auto rounded-lg bg-muted/50 p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap break-words">
                  {doc.ocrText}
                </pre>
              </CardContent>
            </Card>
          )}

          {!doc.description && !doc.summary && !doc.ocrText && (
            <Card>
              <CardHeader>
                <CardTitle>Content</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">
                  No extracted text yet. OCR results and summaries appear here once processing
                  completes.
                </p>
              </CardContent>
            </Card>
          )}
        </div>

        <Card className="h-fit">
          <CardHeader>
            <CardTitle>File details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="flex flex-col gap-4">
              <div className="flex flex-col gap-1">
                <dt className="text-xs font-medium text-muted-foreground">File name</dt>
                <dd className="font-mono text-xs break-all">{doc.fileName ?? '-'}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs font-medium text-muted-foreground">Path</dt>
                <dd className="font-mono text-xs break-all">{doc.filePath ?? '-'}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs font-medium text-muted-foreground">Content type</dt>
                <dd className="text-sm">{doc.contentType ?? '-'}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-xs font-medium text-muted-foreground">Size</dt>
                <dd className="text-sm">{formatBytes(doc.fileSize)}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>
      </div>

      <ConfirmDialog
        open={confirmingDelete}
        title={`Delete “${doc.title}”?`}
        description="This removes the document from the archive. This cannot be undone."
        confirmLabel="Delete document"
        pending={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmingDelete(false)}
      />
    </section>
  )
}
