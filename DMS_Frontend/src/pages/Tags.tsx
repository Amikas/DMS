import { useEffect, useState } from 'react';
import { createTag, listTags } from '../lib/api';
import type { TagRequest, TagResponse } from '../types';
import TagForm from '../components/TagForm';

export default function Tags() {
  const [tags, setTags] = useState<TagResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await listTags();
        if (!cancelled) setTags(data);
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

  async function handleCreate(data: TagRequest) {
    setCreating(true);
    try {
      const created = await createTag(data);
      setTags((prev) => [...prev, created]);
      setShowCreate(false);
    } finally {
      setCreating(false);
    }
  }

  if (loading) return <p>Loading…</p>;
  if (error) return <p role="alert">{error}</p>;

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Tags</h1>
          <p>Label documents for quick filtering</p>
        </div>
        <button type="button" className="btn-primary" onClick={() => setShowCreate((v) => !v)}>
          {showCreate ? 'Close' : '+ New tag'}
        </button>
      </div>
      {showCreate && (
        <div className="card">
          <h2>New tag</h2>
          <TagForm pending={creating} onSubmit={handleCreate} />
        </div>
      )}

      {tags.length === 0 && <p className="empty">No tags yet.</p>}
      <ul className="check-list">
        {tags.map((t) => (
          <li key={t.id}><span className="tag">{t.name}</span></li>
        ))}
      </ul>
    </section>
  );
}
