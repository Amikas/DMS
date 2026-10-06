import { useEffect, useState } from 'react';
import {
  addDocumentToCollection,
  createCollection,
  deleteCollection,
  getCollection,
  listCollections,
  listDocuments,
  removeDocumentFromCollection,
  renameCollection,
} from '../lib/api';
import type { CollectionRequest, CollectionResponse, DocumentResponse } from '../types';
import CollectionForm from '../components/CollectionForm';

export default function Collections() {
  const [collections, setCollections] = useState<CollectionResponse[]>([]);
  const [docs, setDocs] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [detail, setDetail] = useState<CollectionResponse | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [c, d] = await Promise.all([listCollections(), listDocuments()]);
      setCollections(c);
      setDocs(d);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [c, d] = await Promise.all([listCollections(), listDocuments()]);
        if (!cancelled) {
          setCollections(c);
          setDocs(d);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Load failed.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleCreate(data: CollectionRequest) {
    setCreating(true);
    try {
      const created = await createCollection(data);
      setCollections((prev) => [...prev, created]);
      setShowCreate(false);
    } finally {
      setCreating(false);
    }
  }

  async function handleRename(id: number, data: CollectionRequest) {
    const updated = await renameCollection(id, data);
    setCollections((prev) => prev.map((c) => (c.id === id ? updated : c)));
    if (detail?.id === id) setDetail(updated);
    setEditingId(null);
  }

  async function handleDelete(id: number) {
    if (!window.confirm('Delete this collection? Documents are kept.')) return;
    await deleteCollection(id);
    setCollections((prev) => prev.filter((c) => c.id !== id));
    if (detail?.id === id) setDetail(null);
  }

  async function openDetail(id: number) {
    setDetail(await getCollection(id));
  }

  async function toggleDoc(collectionId: number, documentId: number, member: boolean) {
    if (member) {
      await removeDocumentFromCollection(collectionId, documentId);
    } else {
      await addDocumentToCollection(collectionId, documentId);
    }
    const updated = await getCollection(collectionId);
    setDetail(updated);
    setCollections((prev) => prev.map((c) => (c.id === collectionId ? updated : c)));
  }

  if (loading) return <p>Loading…</p>;
  if (error)
    return (
      <p role="alert">
        {error} <button type="button" onClick={load}>Retry</button>
      </p>
    );

  const memberIds = new Set(detail?.documentIds ?? []);

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Collections</h1>
          <p>Group documents — deleting a collection keeps its documents</p>
        </div>
        <button type="button" className="btn-primary" onClick={() => setShowCreate((v) => !v)}>
          {showCreate ? 'Close' : '+ New collection'}
        </button>
      </div>
      {showCreate && (
        <div className="card">
          <h2>New collection</h2>
          <CollectionForm pending={creating} onSubmit={handleCreate} />
        </div>
      )}

      {collections.length === 0 && <p className="empty">No collections yet.</p>}
      <ul className="list">
        {collections.map((c) => (
          <li key={c.id} className="row-card">
            <button type="button" className="btn-ghost" onClick={() => openDetail(c.id)}>
              {c.name} ({c.documentIds.length})
            </button>
            <span className="meta">
              <button type="button" className="btn-ghost" onClick={() => setEditingId(c.id)}>Rename</button>
              <button type="button" className="btn-danger" onClick={() => handleDelete(c.id)}>Delete</button>
            </span>
          </li>
        ))}
      </ul>
      {collections.some((c) => c.id === editingId) && (
        <div className="card">
          <h2>Rename collection</h2>
          {collections.filter((c) => c.id === editingId).map((c) => (
            <CollectionForm
              key={c.id}
              initial={{ name: c.name }}
              onSubmit={(data) => handleRename(c.id, data)}
            />
          ))}
        </div>
      )}

      {detail && (
        <div className="card">
          <h2>{detail.name} — documents</h2>
          <ul className="check-list">
            {docs.map((d) => {
              const member = memberIds.has(d.id);
              return (
                <li key={d.id}>
                  <label>
                    <input
                      type="checkbox"
                      checked={member}
                      onChange={() => toggleDoc(detail.id, d.id, member)}
                    />
                    {d.title}
                  </label>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </section>
  );
}
