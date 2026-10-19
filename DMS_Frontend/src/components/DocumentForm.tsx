import { useState } from 'react'
import type { FormEvent } from 'react'
import { WarningCircleIcon } from '@phosphor-icons/react'

import type { DocumentRequest } from '../types'
import { ApiError } from '../lib/api'
import { isValid, validateDocument, type ValidationErrors } from '../lib/validation'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Textarea } from '@/components/ui/textarea'

interface Props {
  initial?: DocumentRequest
  pending?: boolean
  upload?: boolean
  onSubmit: (data: DocumentRequest, file?: File) => Promise<void>
}

const EMPTY: DocumentRequest = { title: '' }

export default function DocumentForm({ initial = EMPTY, pending = false, upload = false, onSubmit }: Props) {
  const [form, setForm] = useState<DocumentRequest>(initial)
  const [file, setFile] = useState<File>()
  const [errors, setErrors] = useState<ValidationErrors>({})
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [serverError, setServerError] = useState<string | null>(null)

  function set<K extends keyof DocumentRequest>(key: K, value: DocumentRequest[K]) {
    const next = { ...form, [key]: value }
    setForm(next)
    if (touched[key as string]) {
      setErrors(validateDocument(next))
    }
  }

  function blur(key: string) {
    setTouched((t) => ({ ...t, [key]: true }))
    setErrors(validateDocument(form))
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const clientErrors = validateDocument(form)
    setErrors(clientErrors)
    setTouched({ title: true })
    if (!isValid(clientErrors)) return
    setServerError(null)
    if (upload && !file) {
      setServerError('Select a PDF file.')
      return
    }
    try {
      await onSubmit({
        title: form.title.trim(),
        description: form.description?.trim() || undefined,
        filePath: form.filePath?.trim() || undefined,
        fileName: form.fileName?.trim() || undefined,
        fileSize: form.fileSize ?? undefined,
        contentType: form.contentType?.trim() || undefined,
      }, file)
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
        setErrors(err.validationErrors)
      } else {
        setServerError(err instanceof Error ? err.message : 'Save failed.')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <FieldGroup>
        <Field data-invalid={errors.title ? true : undefined}>
          <FieldLabel htmlFor="doc-title">
            Title <span className="text-destructive">*</span>
          </FieldLabel>
          <Input
            id="doc-title"
            value={form.title}
            onChange={(e) => set('title', e.target.value)}
            onBlur={() => blur('title')}
            aria-invalid={Boolean(errors.title)}
            maxLength={255}
            required
          />
          <FieldError>{errors.title}</FieldError>
        </Field>

        <Field data-invalid={errors.description ? true : undefined}>
          <FieldLabel htmlFor="doc-description">Description</FieldLabel>
          <Textarea
            id="doc-description"
            value={form.description ?? ''}
            onChange={(e) => set('description', e.target.value)}
            onBlur={() => blur('description')}
            aria-invalid={Boolean(errors.description)}
            maxLength={2000}
          />
          <FieldError>{errors.description}</FieldError>
        </Field>

        {upload && (
          <Field>
            <FieldLabel htmlFor="doc-file">PDF file * (maximum 10 MB)</FieldLabel>
            <Input id="doc-file" type="file" accept="application/pdf,.pdf" required
              onChange={(e) => setFile(e.target.files?.[0])} />
          </Field>
        )}

        {serverError && (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertDescription>{serverError}</AlertDescription>
          </Alert>
        )}

        <div className="flex justify-end">
          <Button type="submit" disabled={pending || !isValid(errors)}>
            {pending && <Spinner data-icon="inline-start" />}
            {pending ? 'Saving…' : 'Save document'}
          </Button>
        </div>
      </FieldGroup>
    </form>
  )
}
