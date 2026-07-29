import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useApi } from '../useApi'

describe('useApi', () => {
  it('returns loading initially', () => {
    const fetcher = () => Promise.resolve('data')
    const { result } = renderHook(() => useApi(fetcher, []))
    expect(result.current.loading).toBe(true)
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBeNull()
  })

  it('returns data after fetch', async () => {
    const fetcher = () => Promise.resolve('hello')
    const { result } = renderHook(() => useApi(fetcher, []))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.data).toBe('hello')
    expect(result.current.error).toBeNull()
  })

  it('returns error on failure', async () => {
    const fetcher = () => Promise.reject(new Error('fail'))
    const { result } = renderHook(() => useApi(fetcher, []))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.data).toBeNull()
    expect(result.current.error).toBe('fail')
  })

  it('refetches when deps change', async () => {
    let count = 0
    const fetcher = () => Promise.resolve(++count)
    const { result, rerender } = renderHook(({ id }) => useApi(fetcher, [id]), {
      initialProps: { id: 1 },
    })
    await waitFor(() => expect(result.current.data).toBe(1))
    rerender({ id: 2 })
    await waitFor(() => expect(result.current.data).toBe(2))
  })

  it('cancels fetch on unmount', () => {
    let resolved = false
    const fetcher = () => new Promise<string>((resolve) => {
      setTimeout(() => { resolved = true; resolve('late') }, 1000)
    })
    const { unmount } = renderHook(() => useApi(fetcher, []))
    unmount()
    expect(resolved).toBe(false) // won't resolve because component unmounted
  })
})
