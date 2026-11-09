export type ApiErrorCode =
  | "UNAUTHORIZED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "VALIDATION"
  | "ACCOUNT_DISABLED"
  | "CONFLICT"
  | "NETWORK"
  | "ABORTED"
  | "NOT_IMPLEMENTED";

export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  constructor(code: ApiErrorCode, message: string, status = 400) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

export function errorMessage(error: unknown, fallback = "Ocorreu um erro inesperado."): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
