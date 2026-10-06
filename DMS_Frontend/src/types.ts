export type DocumentStatus =
  | 'UPLOADED'
  | 'PROCESSING'
  | 'OCR_COMPLETE'
  | 'SUMMARY_GENERATED'
  | 'ARCHIVED';

export interface CollectionRequest {
  name: string;
}

export interface CollectionResponse {
  id: number;
  name: string;
  documentIds: number[];
}

export interface DocumentRequest {
  title: string;
  description?: string;
  filePath?: string;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
}

export interface DocumentResponse {
  id: number;
  title: string;
  description?: string;
  filePath?: string;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  status: DocumentStatus;
  ocrText?: string;
  summary?: string;
}
