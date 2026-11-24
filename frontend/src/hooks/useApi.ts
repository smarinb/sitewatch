import { useCallback, useEffect, useRef, useState } from 'react'
import type { Api } from '../api/client.ts'
import { ApiError } from '../api/client.ts'
import { useAuth } from '../auth/AuthContext.ts'

export interface ApiResource<T> {
  data: T | undefined
  error: ApiError | undefined
  /** First load, nothing to show yet. */
  loading: boolean
  /** Reloading while the previous data stays on screen. */
  refreshing: boolean
  reload(): void
}

interface Options {
  /** Reload this often while the tab is visible. */
  refreshMs?: number
}

/**
 * Loads data and keeps the previous result on screen while reloading. {@code key}
 * identifies the request: change it to refetch (for example when a route parameter or a filter changes).
 */
export function useApi<T>(load: (api: Api) => Promise<T>, key: string, options: Options = {}): ApiResource<T> {
  const { api } = useAuth()
  const [data, setData] = useState<T>()
  const [error, setError] = useState<ApiError>()
  const [version, setVersion] = useState(0)
  // The request that last finished. Loading is derived from it, so no state is set when a request starts.
  const [settled, setSettled] = useState<string>()
  const loadRef = useRef(load)
  const requestKey = `${key}#${version}`

  useEffect(() => {
    loadRef.current = load
  })

  useEffect(() => {
    // Set by the cleanup below: a response that arrives after the key changed is stale.
    let stale = false
    loadRef
      .current(api)
      .then((result) => {
        if (stale) return
        setData(result)
        setError(undefined)
      })
      .catch((e: unknown) => {
        if (stale) return
        setError(e instanceof ApiError ? e : new ApiError(0, 'Something went wrong'))
      })
      .finally(() => {
        if (!stale) setSettled(requestKey)
      })
    return () => {
      stale = true
    }
  }, [api, requestKey])

  const { refreshMs } = options
  useEffect(() => {
    if (!refreshMs) return
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') setVersion((v) => v + 1)
    }, refreshMs)
    return () => clearInterval(timer)
  }, [refreshMs])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return {
    data,
    error,
    loading: settled !== requestKey && data === undefined,
    refreshing: settled !== requestKey && data !== undefined,
    reload,
  }
}
