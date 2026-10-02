import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  assignTag,
  deleteDocument,
  getDocument,
  listTags,
  removeTag,
  updateDocument,
} from '../lib/api';
import type { DocumentRequest, DocumentResponse, TagResponse } from '../types';
import DocumentForm from '../components/DocumentForm';

export default function DocumentDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const docId = Number(id);

  const [doc, setDoc] = useState<DocumentResponse | null>(null);
  const [allTags, setAllTags] = useState<TagResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const [d, tags] = await Promise.all([getDocument(docId), listTags()]);
        if (!cancelled) {
          setDoc(d);
          setAllTags(tags);
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Load failed.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    if (Number.isInteger(docId)) {
      load();
    } else {
      setError('Invalid document id.');
      setLoading(false);
    }
    return () => {
      cancelled = true;
    };
  }, [docId]);

  async function handleUpdate(data: DocumentRequest) {
    setSaving(true);
    try {
      const updated = await updateDocument(docId, data);
      setDoc(updated);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm('Delete this document?')) return;
    await deleteDocument(docId);
    navigate('/');
  }

  async function toggleTag(tagId: number, assigned: boolean) {
    if (assigned) {
      await removeTag(docId, tagId);
    } else {
      await assignTag(docId, tagId);
    }
    setDoc(await getDocument(docId));
  }

  if (loading) return <p>Loading…</p>;
  if (error || !doc)
    return (
      <p role="alert">
        {error ?? 'Not found.'} <button type="button" onClick={() => navigate('/')}>Back</button>
      </p>
    );

  const assignedIds = new Set(doc.tags.map((t) => t.id));

  return (
    <section>
      <button type="button" className="btn-ghost" onClick={() => navigate('/')}>← Back</button>
      <div className="page-head" style={{ marginTop: 12 }}>
        <div>
          <h1>{doc.title}</h1>
          <p><span className={`badge status ${doc.status}`}>{doc.status}</span></p>
        </div>
        <div className="btn-row">
          <button type="button" className="btn-ghost" onClick={() => setEditing((v) => !v)}>
            {editing ? 'Cancel edit' : 'Edit'}
          </button>
          <button type="button" className="btn-danger" onClick={handleDelete}>Delete</button>
        </div>
      </div>

      <div className="detail-grid">
        <div className="card">
          <h2>Details</h2>
          <dl className="kv">
            {doc.description && (<><dt>Description</dt><dd>{doc.description}</dd></>)}
            {doc.fileName && (<><dt>File</dt><dd className="mono">{doc.fileName}</dd></>)}
            {doc.filePath && (<><dt>Path</dt><dd className="mono">{doc.filePath}</dd></>)}
            {doc.contentType && (<><dt>Type</dt><dd>{doc.contentType}</dd></>)}
            {doc.fileSize !== undefined && doc.fileSize !== null && (<><dt>Size</dt><dd>{doc.fileSize} bytes</dd></>)}
          </dl>
          {doc.ocrText && (<><h2>OCR text</h2><p className="mono">{doc.ocrText}</p></>)}
          {doc.summary && (<><h2 style={{ marginTop: 12 }}>Summary</h2><p>{doc.summary}</p></>)}

          {editing && (
            <div style={{ marginTop: 16 }}>
              <DocumentForm
                initial={{
                  title: doc.title,
                  description: doc.description,
                  filePath: doc.filePath,
                  fileName: doc.fileName,
                  fileSize: doc.fileSize,
                  contentType: doc.contentType,
                }}
                pending={saving}
                onSubmit={handleUpdate}
              />
            </div>
          )}
        </div>

        <div className="card">
          <h2>Tags</h2>
          {allTags.length === 0 && <p className="muted">No tags yet — create one under Tags.</p>}
          <ul className="check-list">
            {allTags.map((t) => {
              const assigned = assignedIds.has(t.id);
              return (
                <li key={t.id}>
                  <label>
                    <input
                      type="checkbox"
                      checked={assigned}
                      onChange={() => toggleTag(t.id, assigned)}
                    />
                    {t.name}
                  </label>
                </li>
              );
            })}
          </ul>
        </div>
      </div>
    </section>
  );
}
