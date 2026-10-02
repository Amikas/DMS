import type { CollectionRequest, DocumentRequest, TagRequest } from '../types';

export type ValidationErrors = Record<string, string>;

export function isValid(errors: ValidationErrors): boolean {
  return Object.keys(errors).length === 0;
}

function validateRequiredName(value: string, field = 'name'): ValidationErrors {
  const errors: ValidationErrors = {};
  if (!value || value.trim().length === 0) {
    errors[field] = 'Name is required.';
  } else if (value.trim().length > 255) {
    errors[field] = 'Max 255 characters allowed.';
  }
  return errors;
}

export function validateTag(data: TagRequest): ValidationErrors {
  return validateRequiredName(data.name, 'name');
}

export function validateCollection(data: CollectionRequest): ValidationErrors {
  return validateRequiredName(data.name, 'name');
}

export function validateDocument(data: DocumentRequest): ValidationErrors {
  const errors: ValidationErrors = {};

  if (!data.title || data.title.trim().length === 0) {
    errors.title = 'Title is required.';
  } else if (data.title.trim().length > 255) {
    errors.title = 'Max 255 characters allowed.';
  }

  if (data.description !== undefined && data.description !== null && data.description.length > 2000) {
    errors.description = 'Max 2000 characters allowed.';
  }

  for (const field of ['filePath', 'fileName', 'contentType'] as const) {
    const value = data[field];
    if (value !== undefined && value !== null && value.length > 255) {
      errors[field] = 'Max 255 characters allowed.';
    }
  }

  if (
    data.contentType !== undefined &&
    data.contentType !== null &&
    data.contentType.trim() !== '' &&
    !/.+\/.+/.test(data.contentType.trim())
  ) {
    errors.contentType = 'Must be a valid media type, e.g. application/pdf.';
  }

  if (data.fileSize !== undefined && data.fileSize !== null) {
    if (!Number.isInteger(data.fileSize) || data.fileSize < 0) {
      errors.fileSize = 'Must be a positive whole number.';
    }
  }

  return errors;
}

export function validateSearch(query: string): ValidationErrors {
  const errors: ValidationErrors = {};
  if (query.length > 255) {
    errors.query = 'Max 255 characters allowed.';
  }
  return errors;
}
