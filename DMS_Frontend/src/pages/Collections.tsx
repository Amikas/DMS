import { useEffect, useState } from 'react'
import {
  FolderSimpleIcon,
  PencilSimpleIcon,
  PlusIcon,
  TrashIcon,
  WarningCircleIcon,
  XIcon,
} from '@phosphor-icons/react'
import { toast } from 'sonner'

import {
  addDocumentToCollection,
  createCollection,
  deleteCollection,
  getCollection,
  listCollections,
  listDocuments,
  removeDocumentFromCollection,
  renameCollection,
} from '../lib/api'
import { pluralize } from '../lib/format'
import type { CollectionRequest, CollectionResponse, DocumentResponse } from '../types'
import CollectionForm from '../components/CollectionForm'
import ConfirmDialog from '../components/ConfirmDialog'
import StatusBadge from '../components/StatusBadge'
import TableSkeleton from '../components/TableSkeleton'
import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Item, ItemActions, ItemContent, ItemDescription, ItemGroup, ItemMedia, ItemTitle } from '@/components/ui/item'
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import { cn } from '@/lib/utils'

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof Error && err.message ? err.message : fallback
}

export default function Collections() {
  const [collections, setCollections] = useState<CollectionResponse[]>([])
  const [docs, setDocs] = useState<DocumentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [detail, setDetail] = useState<CollectionResponse | null>(null)
  const [pendingIds, setPendingIds] = useState<number[]>([])
  const [confirmDelete, setConfirmDelete] = useState<CollectionResponse | null>(null)
  const [deleting, setDeleting] = useState(false)

  async function runAction(id: number, action: () => Promise<void>) {
    setPendingIds((ids) => [...ids, id])
    try {
      await action()
    } catch (err) {
      toast.error(errorMessage(err, 'Action failed. Please try again.'))
    } finally {
      setPendingIds((ids) => ids.filter((pendingId) => pendingId !== id))
    }
  }

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const [c, d] = await Promise.all([listCollections(), listDocuments()])
      setCollections(c)
      setDocs(d)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [c, d] = await Promise.all([listCollections(), listDocuments()])
        if (!cancelled) {
          setCollections(c)
          setDocs(d)
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Load failed.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  async function handleCreate(data: CollectionRequest) {
    setCreating(true)
    try {
      const created = await createCollection(data)
      setCollections((prev) => [...prev, created])
      setShowCreate(false)
      toast.success('Collection created.')
    } finally {
      setCreating(false)
    }
  }

  async function handleRename(id: number, data: CollectionRequest) {
    const updated = await renameCollection(id, data)
    setCollections((prev) => prev.map((c) => (c.id === id ? updated : c)))
    if (detail?.id === id) setDetail(updated)
    setEditingId(null)
    toast.success('Collection renamed.')
  }

  async function handleDelete() {
    if (!confirmDelete) return
    const target = confirmDelete
    setDeleting(true)
    await runAction(target.id, async () => {
      await deleteCollection(target.id)
      setCollections((prev) => prev.filter((c) => c.id !== target.id))
      if (detail?.id === target.id) setDetail(null)
      toast.success(`Deleted “${target.name}”. Documents were kept.`)
    })
    setDeleting(false)
    setConfirmDelete(null)
  }

  async function openDetail(id: number) {
    await runAction(id, async () => {
      setDetail(await getCollection(id))
    })
  }

  async function toggleDoc(collectionId: number, documentId: number, member: boolean) {
    await runAction(collectionId, async () => {
      if (member) {
        await removeDocumentFromCollection(collectionId, documentId)
      } else {
        await addDocumentToCollection(collectionId, documentId)
      }
      const updated = await getCollection(collectionId)
      setDetail(updated)
      setCollections((prev) => prev.map((c) => (c.id === collectionId ? updated : c)))
      toast.success(
        member ? `Removed from “${updated.name}”.` : `Added to “${updated.name}”.`,
      )
    })
  }

  const editingCollection = collections.find((c) => c.id === editingId) ?? null
  const memberIds = new Set(detail?.documentIds ?? [])

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-xl font-semibold tracking-tight">Collections</h1>
          <p className="text-sm text-muted-foreground">
            Group documents. Deleting a collection keeps its documents.
          </p>
        </div>
        <Button onClick={() => setShowCreate((value) => !value)}>
          {showCreate ? <XIcon data-icon="inline-start" /> : <PlusIcon data-icon="inline-start" />}
          {showCreate ? 'Close' : 'New collection'}
        </Button>
      </div>

      {showCreate && (
        <Card>
          <CardHeader>
            <CardTitle>New collection</CardTitle>
          </CardHeader>
          <CardContent>
            <CollectionForm pending={creating} onSubmit={handleCreate} />
          </CardContent>
        </Card>
      )}

      {editingCollection && (
        <Card>
          <CardHeader>
            <CardTitle>Rename collection</CardTitle>
          </CardHeader>
          <CardContent>
            <CollectionForm
              key={editingCollection.id}
              initial={{ name: editingCollection.name }}
              pending={pendingIds.includes(editingCollection.id)}
              onCancel={() => setEditingId(null)}
              onSubmit={(data) => handleRename(editingCollection.id, data)}
            />
          </CardContent>
        </Card>
      )}

      {loading && <TableSkeleton rows={3} />}

      {error && (
        <Alert variant="destructive">
          <WarningCircleIcon />
          <AlertTitle>Could not load collections.</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
          <AlertAction>
            <Button variant="outline" size="sm" onClick={load}>
              Retry
            </Button>
          </AlertAction>
        </Alert>
      )}

      {!loading && !error && collections.length === 0 && (
        <Empty className="border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FolderSimpleIcon />
            </EmptyMedia>
            <EmptyTitle>No collections yet</EmptyTitle>
            <EmptyDescription>
              Group related documents without moving them. Deleting a collection never deletes
              documents.
            </EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button onClick={() => setShowCreate(true)}>
              <PlusIcon data-icon="inline-start" />
              New collection
            </Button>
          </EmptyContent>
        </Empty>
      )}

      {!loading && !error && collections.length > 0 && (
        <ItemGroup className="gap-2">
          {collections.map((collection) => {
            const pending = pendingIds.includes(collection.id)
            return (
              <Item
                key={collection.id}
                variant="outline"
                className={cn(
                  'transition-colors hover:bg-muted/40',
                  detail?.id === collection.id && 'border-primary/40',
                )}
              >
                <ItemMedia variant="icon" className="text-primary">
                  <FolderSimpleIcon />
                </ItemMedia>
                <ItemContent>
                  <ItemTitle>
                    <Button
                      variant="link"
                      className="h-auto justify-start p-0"
                      disabled={pending}
                      onClick={() => openDetail(collection.id)}
                    >
                      {collection.name}
                    </Button>
                  </ItemTitle>
                  <ItemDescription>
                    {pluralize(collection.documentIds.length, 'document')}
                  </ItemDescription>
                </ItemContent>
                <ItemActions>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Rename ${collection.name}`}
                    disabled={pending}
                    onClick={() => setEditingId(collection.id)}
                  >
                    <PencilSimpleIcon />
                  </Button>
                  <Button
                    variant="destructive"
                    size="icon-sm"
                    aria-label={`Delete ${collection.name}`}
                    disabled={pending}
                    onClick={() => setConfirmDelete(collection)}
                  >
                    <TrashIcon />
                  </Button>
                </ItemActions>
              </Item>
            )
          })}
        </ItemGroup>
      )}

      {detail && (
        <Card>
          <CardHeader>
            <CardTitle>{detail.name}</CardTitle>
            <CardDescription>
              {pluralize(detail.documentIds.length, 'document')} in this collection
            </CardDescription>
            <CardAction>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={pendingIds.includes(detail.id)}
                  onClick={() => setEditingId(detail.id)}
                >
                  <PencilSimpleIcon data-icon="inline-start" />
                  Rename
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  aria-label="Close collection"
                  onClick={() => setDetail(null)}
                >
                  <XIcon />
                </Button>
              </div>
            </CardAction>
          </CardHeader>
          <CardContent>
            {docs.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                No documents exist yet. Create documents first, then add them here.
              </p>
            ) : (
              <FieldGroup className="gap-1">
                {docs.map((doc) => {
                  const member = memberIds.has(doc.id)
                  const pending = pendingIds.includes(detail.id)
                  return (
                    <Field
                      key={doc.id}
                      orientation="horizontal"
                      data-disabled={pending ? true : undefined}
                      className="rounded-lg px-2 py-1.5 transition-colors hover:bg-muted/40"
                    >
                      <Checkbox
                        id={`collection-doc-${doc.id}`}
                        aria-label={doc.title}
                        checked={member}
                        disabled={pending}
                        onCheckedChange={() => toggleDoc(detail.id, doc.id, member)}
                      />
                      <FieldLabel htmlFor={`collection-doc-${doc.id}`} className="font-normal">
                        {doc.title}
                      </FieldLabel>
                      <StatusBadge status={doc.status} />
                    </Field>
                  )
                })}
              </FieldGroup>
            )}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={confirmDelete !== null}
        title={confirmDelete ? `Delete “${confirmDelete.name}”?` : ''}
        description="The collection is removed. Its documents are kept in the archive."
        confirmLabel="Delete collection"
        pending={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmDelete(null)}
      />
    </section>
  )
}
