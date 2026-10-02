import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { createDocument, listDocuments } from '../lib/api';
import { validateSearch } from '../lib/validation';
import type { DocumentRequest, DocumentResponse } from '../types';
import DocumentForm from '../components/DocumentForm';

export default function Dashboard() {
  const [docs, setDocs] = useState<DocumentResponse[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setDocs(await listDocuments());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const searchError = validateSearch(query).query;
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q || searchError) return docs;
    return docs.filter(
      (d) =>
        d.title.toLowerCase().includes(q) ||
        (d.description ?? '').toLowerCase().includes(q) ||
        d.tags.some((t) => t.name.toLowerCase().includes(q)),
    );
  }, [docs, query, searchError]);

  async function handleCreate(data: DocumentRequest) {
    setCreating(true);
    try {
      const created = await createDocument(data);
      setDocs((prev) => [created, ...prev]);
      setShowCreate(false);
    } finally {
      setCreating(false);
    }
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Documents</h1>
          <p>{docs.length} document{docs.length === 1 ? '' : 's'} in your archive</p>
        </div>
        <button type="button" className="btn-primary" onClick={() => setShowCreate((v) => !v)}>
          {showCreate ? 'Close' : '+ New document'}
        </button>
      </div>

      <div className="toolbar">
        <div className="search">
          <input
            id="dashboard-search"
            type="search"
            aria-label="Search documents"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search title, description, or tag…"
            maxLength={255}
            aria-invalid={Boolean(searchError)}
          />
        </div>
      </div>
      {searchError && <p role="alert">{searchError}</p>}

      {showCreate && (
        <div className="card">
          <h2>New document</h2>
          <DocumentForm pending={creating} onSubmit={handleCreate} />
        </div>
      )}

      {loading && <p className="muted">Loading…</p>}
      {error && (
        <p role="alert">
          {error} <button type="button" className="btn-ghost" onClick={load}>Retry</button>
        </p>
      )}

      {!loading && !error && filtered.length === 0 && <p className="empty">No documents found.</p>}

      <ul className="list">
        {filtered.map((d) => (
          <li key={d.id} className="row-card">
            <Link to={`/documents/${d.id}`} className="title">{d.title}</Link>
            <span className="meta">
              <span className={`badge status ${d.status}`}>{d.status}</span>
              {d.tags.map((t) => (
                <span key={t.id} className="tag">{t.name}</span>
              ))}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
