import type {
  SourceInfo,
  SearchResponse,
  DetailsResponse,
  EpisodeResponse,
  LinksResponse,
  LibraryItem,
  LibraryUpdate,
  PluginResponse,
  BrowseResponse,
  SettingsResponse,
  HealthResponse,
  MainPageResponse,
  DownloadTask,
  DownloadsResponse,
} from '../types'

async function get<T>(path: string, params?: Record<string, string>): Promise<T> {
  const url = new URL(path, window.location.origin)
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== '') url.searchParams.set(k, v)
    })
  }
  const res = await fetch(url.toString())
  if (!res.ok) {
    const text = await res.text().catch(() => 'Unknown error')
    throw new Error(`API ${res.status}: ${text}`)
  }
  return res.json()
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => 'Unknown error')
    throw new Error(`API ${res.status}: ${text}`)
  }
  return res.json()
}

async function del(path: string): Promise<void> {
  const res = await fetch(path, { method: 'DELETE' })
  if (!res.ok) {
    const text = await res.text().catch(() => 'Unknown error')
    throw new Error(`API ${res.status}: ${text}`)
  }
}

export const api = {
  get: <T>(path: string, params?: Record<string, string>) => get<T>(path, params),
  post: <T>(path: string, body: unknown) => post<T>(path, body),
  del: (path: string) => del(path),
  health: () => get<HealthResponse>('/api/health'),
  sources: () => get<{ sources: SourceInfo[] }>('/api/sources'),
  source: (name: string) => get<SourceInfo>(`/api/sources/${encodeURIComponent(name)}`),
  search: (q: string, page = 1, source?: string) =>
    get<SearchResponse>('/api/search', { q, page: String(page), ...(source ? { source } : {}) }),
  quicksearch: (q: string) => get<SearchResponse>('/api/quicksearch', { q }),
  mainpage: (source: string) => get<MainPageResponse>('/api/mainpage', { source }),
  details: (url: string, api: string) =>
    get<DetailsResponse>('/api/details', { url, api }),
  episodes: (url: string, api: string) =>
    get<EpisodeResponse>('/api/episodes', { url, api }),
  links: (data: string, referer: string, apiName?: string) => {
    const body: Record<string, string> = { data, referer }
    if (apiName) body.apiName = apiName
    return post<LinksResponse>('/api/links', body)
  },
  library: {
    list: () => get<{ items: LibraryItem[] }>('/api/library'),
    get: (id: string) => get<LibraryItem>(`/api/library/${encodeURIComponent(id)}`),
    update: (body: LibraryUpdate) => post<LibraryItem>('/api/library', body),
    delete: (id: string) => del(`/api/library/${encodeURIComponent(id)}`),
  },
  plugins: () => get<PluginResponse>('/api/plugins'),
  repositories: () => get<{ repositories: string[] }>('/api/repositories'),
  addRepo: (url: string) => post<{ repositories: string[] }>('/api/repositories', { url }),
  deleteRepo: (url: string) => del(`/api/repositories/${encodeURIComponent(url)}`),
  browse: () => get<BrowseResponse>('/api/plugins/browse'),
  installPlugin: (internalName: string, jarUrl: string, forceBypass?: boolean) =>
    post<{ success?: boolean; name?: string; needsBypass?: boolean; message?: string }>(
      '/api/plugins/install', { internalName, jarUrl, ...(forceBypass ? { forceBypass: true } : {}) },
    ),
  deletePlugin: (name: string) => del(`/api/plugins/${encodeURIComponent(name)}`),
  pluginSettings: (name: string) =>
    get<SettingsResponse>(`/api/plugins/${encodeURIComponent(name)}/settings`),
  savePluginSetting: (name: string, key: string, value: string) =>
    post<{ success: boolean }>(`/api/plugins/${encodeURIComponent(name)}/settings`, { key, value }),
  downloads: {
    list: () => get<DownloadsResponse>('/api/downloads'),
    start: (body: {
      url: string
      title?: string
      isM3u8?: boolean
      isDash?: boolean
      headers?: Record<string, string>
    }) => post<DownloadTask>('/api/downloads', body),
    status: (id: string) => get<DownloadTask>(`/api/downloads/${encodeURIComponent(id)}/status`),
    cancel: (id: string) => del(`/api/downloads/${encodeURIComponent(id)}`),
    fileUrl: (id: string) => `/api/downloads/${encodeURIComponent(id)}/file`,
  },
}
