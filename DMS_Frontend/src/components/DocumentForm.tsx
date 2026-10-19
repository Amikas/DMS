import { useState } from 'react';
import type { FormEvent } from 'react';
import type { DocumentRequest } from '../types';
import { ApiError } from '../lib/api';
import { isValid, validateDocument, type ValidationErrors } from '../lib/validation';

interface Props {
  initial?: DocumentRequest;
  pending?: boolean;
  upload?: boolean;
  onSubmit: (data: DocumentRequest, file?: File) => Promise<void>;
}

const EMPTY: DocumentRequest = { title: '' };

export default function DocumentForm({ initial = EMPTY, pending = false, upload = false, onSubmit }: Props) {
  const [form, setForm] = useState<DocumentRequest>(initial);
  const [file, setFile] = useState<File>();
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
    if (upload && !file) {
      setServerError('Select a PDF file.');
      return;
    }
    try {
      await onSubmit({
        title: form.title.trim(),
        description: form.description?.trim() || undefined,
        filePath: form.filePath?.trim() || undefined,
        fileName: form.fileName?.trim() || undefined,
        fileSize: form.fileSize ?? undefined,
        contentType: form.contentType?.trim() || undefined,
      }, file);
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

      {upload && <div className="full">
        <label htmlFor="doc-file">PDF file * (maximum 10 MB)</label>
        <input id="doc-file" type="file" accept="application/pdf,.pdf" required
          onChange={(e) => setFile(e.target.files?.[0])} />
      </div>}

      {serverError && <p role="alert">{serverError}</p>}

      <button type="submit" disabled={pending || !isValid(errors)}>
        {pending ? 'Saving…' : 'Save document'}
      </button>
    </form>
  );
}
