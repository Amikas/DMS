import { useState } from 'react';
import type { FormEvent } from 'react';
import type { DocumentRequest } from '../types';
import { ApiError } from '../lib/api';
import { isValid, validateDocument, type ValidationErrors } from '../lib/validation';

interface Props {
  initial?: DocumentRequest;
  pending?: boolean;
  onSubmit: (data: DocumentRequest) => Promise<void>;
}

const EMPTY: DocumentRequest = { title: '' };

export default function DocumentForm({ initial = EMPTY, pending = false, onSubmit }: Props) {
  const [form, setForm] = useState<DocumentRequest>(initial);
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [serverError, setServerError] = useState<string | null>(null);

  function set<K extends keyof DocumentRequest>(key: K, value: DocumentRequest[K]) {
    const next = { ...form, [key]: value };
    setForm(next);
    if (touched[key as string]) {
      setErrors(validateDocument(next));
    }
  }

  function blur(key: string) {
    setTouched((t) => ({ ...t, [key]: true }));
    setErrors(validateDocument(form));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const clientErrors = validateDocument(form);
    setErrors(clientErrors);
    setTouched({ title: true });
    if (!isValid(clientErrors)) return;
    setServerError(null);
    try {
      await onSubmit({
        title: form.title.trim(),
        description: form.description?.trim() || undefined,
        filePath: form.filePath?.trim() || undefined,
        fileName: form.fileName?.trim() || undefined,
        fileSize: form.fileSize ?? undefined,
        contentType: form.contentType?.trim() || undefined,
      });
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
        setErrors(err.validationErrors);
      } else {
        setServerError(err instanceof Error ? err.message : 'Save failed.');
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="form-grid">
      <div className="full">
        <label htmlFor="doc-title">Title *</label>
        <input
          id="doc-title"
          value={form.title}
          onChange={(e) => set('title', e.target.value)}
          onBlur={() => blur('title')}
          aria-invalid={Boolean(errors.title)}
          maxLength={255}
          required
        />
        {errors.title && <p role="alert">{errors.title}</p>}
      </div>

      <div className="full">
        <label htmlFor="doc-description">Description</label>
        <textarea
          id="doc-description"
          value={form.description ?? ''}
          onChange={(e) => set('description', e.target.value)}
          onBlur={() => blur('description')}
          aria-invalid={Boolean(errors.description)}
          maxLength={2000}
        />
        {errors.description && <p role="alert">{errors.description}</p>}
      </div>

      <div>
        <label htmlFor="doc-filename">File name</label>
        <input
          id="doc-filename"
          value={form.fileName ?? ''}
          onChange={(e) => set('fileName', e.target.value)}
          onBlur={() => blur('fileName')}
          aria-invalid={Boolean(errors.fileName)}
          maxLength={255}
        />
        {errors.fileName && <p role="alert">{errors.fileName}</p>}
      </div>

      <div>
        <label htmlFor="doc-filepath">File path</label>
        <input
          id="doc-filepath"
          value={form.filePath ?? ''}
          onChange={(e) => set('filePath', e.target.value)}
          onBlur={() => blur('filePath')}
          aria-invalid={Boolean(errors.filePath)}
          maxLength={255}
        />
        {errors.filePath && <p role="alert">{errors.filePath}</p>}
      </div>

      <div>
        <label htmlFor="doc-filesize">File size (bytes)</label>
        <input
          id="doc-filesize"
          type="number"
          min={0}
          step={1}
          value={form.fileSize ?? ''}
          onChange={(e) =>
            set('fileSize', e.target.value === '' ? undefined : Number(e.target.value))
          }
          onBlur={() => blur('fileSize')}
          aria-invalid={Boolean(errors.fileSize)}
        />
        {errors.fileSize && <p role="alert">{errors.fileSize}</p>}
      </div>

      <div>
        <label htmlFor="doc-contenttype">Content type</label>
        <input
          id="doc-contenttype"
          placeholder="application/pdf"
          value={form.contentType ?? ''}
          onChange={(e) => set('contentType', e.target.value)}
          onBlur={() => blur('contentType')}
          aria-invalid={Boolean(errors.contentType)}
          maxLength={255}
        />
        {errors.contentType && <p role="alert">{errors.contentType}</p>}
      </div>

      {serverError && <p role="alert">{serverError}</p>}

      <button type="submit" disabled={pending || !isValid(errors)}>
        {pending ? 'Saving…' : 'Save document'}
      </button>
    </form>
  );
}
