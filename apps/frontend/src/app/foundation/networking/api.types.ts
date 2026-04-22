export interface ApiError {
  status: number;
  code?: string;
  message: string;
  requestId?: string;
  details?: Record<string, unknown>;
}

export interface ApiPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ApiEnvelope<T> {
  data: T;
  requestId?: string;
}

export type QueryParamPrimitive = string | number | boolean | Date;
export type QueryParamValue =
  | QueryParamPrimitive
  | readonly QueryParamPrimitive[]
  | null
  | undefined;

export type QueryParams = Record<string, QueryParamValue>;

export const asApiPage = <T>(content: T[], page = 0, size = content.length): ApiPage<T> => ({
  content,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
  page,
  size,
});
