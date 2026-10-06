import { useState } from 'react';
import type { FormEvent } from 'react';
import type { CollectionRequest } from '../types';
import { ApiError } from '../lib/api';
import { isValid, validateCollection, type ValidationErrors } from '../lib/validation';

interface Props {
  initial?: CollectionRequest;
  pending?: boolean;
  onSubmit: (data: CollectionRequest) => Promise<void>;
}

export default function CollectionForm({
  initial = { name: '' },
  pending = false,
  onSubmit,
}: Props) {
  const [form, setForm] = useState<CollectionRequest>(initial);
  const [errors, setErrors] = useState<ValidationErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const clientErrors = validateCollection(form);
    setErrors(clientErrors);
    if (!isValid(clientErrors)) return;
    setServerError(null);
    try {
      await onSubmit({ name: form.name.trim() });
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
        setErrors(err.validationErrors);
      } else {
        setServerError(err instanceof Error ? err.message : 'Save failed.');
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div>
        <label htmlFor="collection-name">Name *</label>
        <input
          id="collection-name"
          value={form.name}
          onChange={(e) => {
            const next = { name: e.target.value };
            setForm(next);
            setErrors(validateCollection(next));
          }}
          aria-invalid={Boolean(errors.name)}
          maxLength={255}
          required
        />
        {errors.name && <p role="alert">{errors.name}</p>}
      </div>

      {serverError && <p role="alert">{serverError}</p>}

      <button type="submit" disabled={pending || !isValid(errors)}>
        {pending ? 'Saving…' : 'Save collection'}
      </button>
    </form>
  );
}
