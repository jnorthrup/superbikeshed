// CouchDB Document Types
export type DocumentId = string;
export type RevisionId = string;
export type DatabaseName = string;

export interface CouchDocument {
  _id: DocumentId;
  _rev?: RevisionId;
  [key: string]: any;
}

export interface CouchView {
  map: string;
  reduce?: string;
}

export interface CouchDesignDocument extends CouchDocument {
  views: {
    [viewName: string]: CouchView;
  };
  language: string;
}

export interface CouchDatabaseInfo {
  db_name: DatabaseName;
  doc_count: number;
  doc_del_count: number;
  update_seq: string;
  purge_seq: string;
  compact_running: boolean;
  disk_size: number;
  data_size: number;
  instance_start_time: string;
  disk_format_version: number;
  committed_update_seq: string;
  compacted_seq: string;
  uuid: string;
}

export interface CouchSecurity {
  admins: {
    names: string[];
    roles: string[];
  };
  members: {
    names: string[];
    roles: string[];
  };
}

// Admin Party Mode Types
export interface AdminPartyConfig {
  enabled: boolean;
  admin_roles: string[];
}

// Response Types
export interface CouchResponse {
  ok: boolean;
  id: DocumentId;
  rev: RevisionId;
}

export interface CouchError {
  error: string;
  reason: string;
}

// View Query Types
export interface ViewQueryParams {
  key?: any;
  keys?: any[];
  startkey?: any;
  endkey?: any;
  startkey_docid?: DocumentId;
  endkey_docid?: DocumentId;
  limit?: number;
  skip?: number;
  descending?: boolean;
  include_docs?: boolean;
  reduce?: boolean;
  group?: boolean;
  group_level?: number;
}

export interface ViewResponse<T = any> {
  total_rows: number;
  offset: number;
  rows: Array<{
    id: DocumentId;
    key: any;
    value: T;
    doc?: CouchDocument;
  }>;
} 