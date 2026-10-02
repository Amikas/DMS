import type {
  CollectionRequest,
  CollectionResponse,
  DocumentRequest,
  DocumentResponse,
  TagRequest,
  TagResponse,
} from '../types';

const API_BASE = '';

export class ApiError extends Error {
  status: number;
  validationErrors?: Record<string, string>;

  constructor(status: number, message: string, validationErrors?: Record<string, string>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.validationErrors = validationErrors;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });

  if (res.status === 204) {
    return undefined as T;
  }

  const contentType = res.headers.get('content-type') ?? '';
  const body = contentType.includes('application/json')
    ? await res.json().catch(() => null)
    : await res.text().catch(() => null);

  if (!res.ok) {
    const message =
      typeof body === 'object' && body !== null && 'message' in body
        ? String((body as { message: unknown }).message)
        : res.statusText;
    const validationErrors =
      typeof body === 'object' && body !== null && 'validationErrors' in body
        ? (body as { validationErrors: Record<string, string> }).validationErrors
        : undefined;
    throw new ApiError(res.status, message, validationErrors);
  }

  return body as T;
}

function json(body: unknown): RequestInit {
  return { method: 'POST', body: JSON.stringify(body) };
}

// --- documents ---

export function listDocuments(): Promise<DocumentResponse[]> {
  return request<DocumentResponse[]>('/api/documents');
}

export function getDocument(id: number): Promise<DocumentResponse> {
  return request<DocumentResponse>(`/api/documents/${id}`);
}

export function createDocument(data: DocumentRequest): Promise<DocumentResponse> {
  return request<DocumentResponse>('/api/documents', json(data));
}

export function updateDocument(id: number, data: DocumentRequest): Promise<DocumentResponse> {
  return request<DocumentResponse>(`/api/documents/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deleteDocument(id: number): Promise<void> {
  return request<void>(`/api/documents/${id}`, { method: 'DELETE' });
}

// --- tags ---

export function listTags(): Promise<TagResponse[]> {
  return request<TagResponse[]>('/api/tags');
}

export function createTag(data: TagRequest): Promise<TagResponse> {
  return request<TagResponse>('/api/tags', json(data));
}

export function assignTag(documentId: number, tagId: number): Promise<void> {
  return request<void>(`/api/documents/${documentId}/tags/${tagId}`, { method: 'PUT' });
}

export function removeTag(documentId: number, tagId: number): Promise<void> {
  return request<void>(`/api/documents/${documentId}/tags/${tagId}`, { method: 'DELETE' });
}

// --- collections ---

export function listCollections(): Promise<CollectionResponse[]> {
  return request<CollectionResponse[]>('/api/collections');
}

export function getCollection(id: number): Promise<CollectionResponse> {
  return request<CollectionResponse>(`/api/collections/${id}`);
}

export function createCollection(data: CollectionRequest): Promise<CollectionResponse> {
  return request<CollectionResponse>('/api/collections', json(data));
}

export function renameCollection(id: number, data: CollectionRequest): Promise<CollectionResponse> {
  return request<CollectionResponse>(`/api/collections/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deleteCollection(id: number): Promise<void> {
  return request<void>(`/api/collections/${id}`, { method: 'DELETE' });
}

export function addDocumentToCollection(id: number, documentId: number): Promise<CollectionResponse> {
  return request<CollectionResponse>(`/api/collections/${id}/documents/${documentId}`, {
    method: 'PUT',
  });
}

export function removeDocumentFromCollection(id: number, documentId: number): Promise<void> {
  return request<void>(`/api/collections/${id}/documents/${documentId}`, { method: 'DELETE' });
}

// --- health ---

export async function checkHealth(): Promise<string> {
  const res = await fetch(`${API_BASE}/api/health`);
  if (!res.ok) {
    throw new ApiError(res.status, res.statusText);
  }
  return res.text();
}
