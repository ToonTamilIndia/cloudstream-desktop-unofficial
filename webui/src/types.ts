export interface SourceInfo {
  name: string
  url: string
  lang: string
  hasMainPage: boolean
  supportedTypes: string[]
  hasQuickSearch: boolean
  providerType: string
}

export interface SearchResultItem {
  name: string
  url: string
  apiName: string
  type?: string
  displayType?: string
  posterUrl?: string
  id?: number
  quality?: string
  score?: string
  year?: number
}

export interface SearchResponse {
  results: SearchResultItem[]
  hasNext: boolean
  query: string
  page: number
}

export interface LinkResult {
  name: string
  url: string
  quality: number
  type: string
  headers?: Record<string, string>
  isM3u8: boolean
  isDash: boolean
  proxyUrl?: string
  drmKid?: string
  drmKey?: string
  drmUuid?: string
  drmLicenseUrl?: string
}

export interface SubtitleResult {
  lang: string
  url: string
  headers?: Record<string, string>
  proxyUrl?: string
}

export interface LinksResponse {
  links: LinkResult[]
  subtitles: SubtitleResult[]
  sessionId?: string
  proxyUrl?: string
}

export interface ActorData {
  name: string
  image?: string
  role?: string
}

export interface TrailerData {
  url: string
  type: string
}

export interface EpisodeData {
  data: string
  name?: string
  season?: number
  episode?: number
  posterUrl?: string
  description?: string
  date?: number
  runtime?: number
}

export interface EpisodeResponse {
  name: string
  type: string
  episodes: (EpisodeData & { dubStatus?: string })[]
}

export interface DetailsResponse {
  name: string
  url: string
  apiName: string
  type: string
  displayType?: string
  posterUrl?: string
  year?: number
  plot?: string
  score?: string
  tags?: string[]
  duration?: number
  recommendations?: SearchResultItem[]
  actors?: ActorData[]
  comingSoon: boolean
  backgroundPosterUrl?: string
  logoUrl?: string
  contentRating?: string
  trailers?: TrailerData[]
  episodes?: EpisodeData[]
  dubEpisodes?: Record<string, number>
}

export interface LibraryItem {
  id: string
  name: string
  posterUrl?: string
  apiName: string
  url: string
  type: string
  episode?: number
  season?: number
  progress?: number
  lastWatched?: number
  isFavorite: boolean
}

export interface LibraryUpdate {
  id: string
  episode?: number
  season?: number
  progress?: number
  bookmark?: boolean
}

export interface PluginInfo {
  name: string
  version?: string
  fileName?: string
  url?: string
  enabled: boolean
  hasUpdate: boolean
}

export interface PluginResponse {
  plugins: PluginInfo[]
}

export interface RemotePlugin {
  name: string
  internalName: string
  version: number
  description?: string
  iconUrl?: string
  jarUrl: string
  language?: string
  tvTypes?: string[]
  repoUrl?: string
}

export interface BrowseResponse {
  plugins: RemotePlugin[]
}

export interface PluginSetting {
  key: string
  type: string
  defaultValue: string
  value: string
  isGlobal: boolean
}

export interface SettingsResponse {
  settings: PluginSetting[]
  prefName: string
}

export interface HealthResponse {
  status: string
  version: string
  sources: number
}

export interface MainPageCategory {
  name: string
  isHorizontalImages: boolean
  items: SearchResultItem[]
}

export interface MainPageResponse {
  categories: MainPageCategory[]
}
