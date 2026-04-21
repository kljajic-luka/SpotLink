export type LoadState = 'idle' | 'loading' | 'success' | 'empty' | 'error';

export interface ViewState<T> {
  state: LoadState;
  data: T | null;
  error: string | null;
}

export const idleViewState = <T>(): ViewState<T> => ({
  state: 'idle',
  data: null,
  error: null,
});

export const loadingViewState = <T>(data: T | null = null): ViewState<T> => ({
  state: 'loading',
  data,
  error: null,
});

export const successViewState = <T>(data: T): ViewState<T> => ({
  state: 'success',
  data,
  error: null,
});

export const emptyViewState = <T>(): ViewState<T> => ({
  state: 'empty',
  data: null,
  error: null,
});

export const errorViewState = <T>(error: string, data: T | null = null): ViewState<T> => ({
  state: 'error',
  data,
  error,
});
