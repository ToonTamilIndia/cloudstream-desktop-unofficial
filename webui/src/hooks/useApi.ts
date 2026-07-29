import { useState, useEffect, useCallback, useRef } from 'react'

export function useApi<T>(
  fetcher: () => Promise<T>,
  deps: any[],
): { data: T | null; loading: boolean; error: string | null } {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const cancelledRef = useRef(false)

  useEffect(() => {
    cancelledRef.current = false
    setLoading(true)
    setError(null)
    fetcher()
      .then((d) => { if (!cancelledRef.current) setData(d) })
      .catch((e: Error) => { if (!cancelledRef.current) setError(e.message) })
      .finally(() => { if (!cancelledRef.current) setLoading(false) })
    return () => { cancelledRef.current = true }
  }, deps)

  return { data, loading, error }
}

export function useApiLazy<T>(): [
  (fetcher: () => Promise<T>) => void,
  { data: T | null; loading: boolean; error: string | null },
] {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const execute = useCallback(async (fetcher: () => Promise<T>) => {
    setLoading(true)
    setError(null)
    try {
      const d = await fetcher()
      setData(d)
    } catch (e: any) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  return [execute, { data, loading, error }]
}
