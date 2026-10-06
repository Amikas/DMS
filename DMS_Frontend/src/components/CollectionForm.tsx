import { useState } from 'react'
import type { FormEvent } from 'react'
import { WarningCircleIcon } from '@phosphor-icons/react'

import type { CollectionRequest } from '../types'
import { ApiError } from '../lib/api'
import { isValid, validateCollection, type ValidationErrors } from '../lib/validation'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Field, FieldError, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'

interface Props {
  initial?: CollectionRequest
  pending?: boolean
  onCancel?: () => void
  onSubmit: (data: CollectionRequest) => Promise<void>
}

export default function CollectionForm({
  initial = { name: '' },
  pending = false,
  onCancel,
  onSubmit,
}: Props) {
  const [form, setForm] = useState<CollectionRequest>(initial)
  const [errors, setErrors] = useState<ValidationErrors>({})
  const [serverError, setServerError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const clientErrors = validateCollection(form)
    setErrors(clientErrors)
    if (!isValid(clientErrors)) return
    setServerError(null)
    try {
      await onSubmit({ name: form.name.trim() })
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
        <Field data-invalid={errors.name ? true : undefined}>
          <FieldLabel htmlFor="collection-name">
            Name <span className="text-destructive">*</span>
          </FieldLabel>
          <Input
            id="collection-name"
            value={form.name}
            onChange={(e) => {
              const next = { name: e.target.value }
              setForm(next)
              setErrors(validateCollection(next))
            }}
            aria-invalid={Boolean(errors.name)}
            maxLength={255}
            required
          />
          <FieldError>{errors.name}</FieldError>
        </Field>

        {serverError && (
          <Alert variant="destructive">
            <WarningCircleIcon />
            <AlertDescription>{serverError}</AlertDescription>
          </Alert>
        )}

        <div className="flex justify-end gap-2">
          {onCancel && (
            <Button type="button" variant="outline" onClick={onCancel} disabled={pending}>
              Cancel
            </Button>
          )}
          <Button type="submit" disabled={pending || !isValid(errors)}>
            {pending && <Spinner data-icon="inline-start" />}
            {pending ? 'Saving…' : 'Save collection'}
          </Button>
        </div>
      </FieldGroup>
    </form>
  )
}
