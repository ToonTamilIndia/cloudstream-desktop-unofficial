import { useState, useCallback } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import {
  Box,
  Typography,
  Chip,
  Button,
  Tabs,
  Tab,
  Skeleton,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  List,
  ListItemButton,
  ListItemText,
  Rating,
} from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import { api } from '../api/client'
import { useApi, useApiLazy } from '../hooks/useApi'
import EpisodeCard from '../components/EpisodeCard'
import CategoryRow from '../components/CategoryRow'
import type {
  EpisodeData,
  EpisodeResponse,
  LinksResponse,
  LinkResult,
} from '../types'
import { desktopTheme } from '../theme/theme'

const SINGLE_PLAY_TYPES = ['Movie', 'AnimeMovie', 'OVA', 'Torrent', 'Documentary', 'NSFW', 'Others', 'Video', 'Music', 'AudioBook', 'Audio', 'Podcast']
const SERIES_TYPES = ['TvSeries', 'Anime', 'Cartoon', 'AsianDrama']

export default function DetailsScreen() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const url = searchParams.get('url') || ''
  const apiName = searchParams.get('api') || ''

  const { data: details, loading } = useApi(
    () => api.details(url, apiName),
    [url, apiName],
  )
  const { data: episodeResponse } = useApi(
    () => (details && SERIES_TYPES.includes(details.type) ? api.episodes(url, apiName) : Promise.resolve(null as any)),
    [url, apiName, details?.type],
  )
  const episodeData: EpisodeResponse | null = episodeResponse
  const [season, setSeason] = useState(0)

  const [linkDialogOpen, setLinkDialogOpen] = useState(false)
  const [fetchLinks, { data: currentLinks, loading: linksLoading }] = useApiLazy<LinksResponse>()
  const [selectedEpisode, setSelectedEpisode] = useState<EpisodeData | null>(null)

  const filteredEpisodes: EpisodeData[] = episodeData
    ? episodeData.episodes.filter((e) => e.season == null || e.season === season)
    : details?.episodes?.filter((e) => e.season == null || e.season === season) || []

  const seasons = new Set<number>()
  ;(episodeData?.episodes || details?.episodes || []).forEach((e) => {
    if (e.season != null) seasons.add(e.season)
  })
  const seasonList = Array.from(seasons).sort((a, b) => a - b)

  const handleEpisodeClick = useCallback((ep: EpisodeData) => {
    setSelectedEpisode(ep)
    setLinkDialogOpen(true)
    fetchLinks(() => api.links(ep.data, url, apiName))
  }, [url, fetchLinks])

  const handlePlayLink = useCallback((link: LinkResult) => {
    const playUrl = link.proxyUrl || link.url
    const drmInfo = link.drmKid || link.drmLicenseUrl
      ? {
          drmKid: link.drmKid,
          drmKey: link.drmKey,
          drmUuid: link.drmUuid,
          drmLicenseUrl: link.drmLicenseUrl,
        }
      : undefined
    const state = {
      subtitles: currentLinks?.subtitles || [],
      sessionId: currentLinks?.sessionId || null,
      proxyUrl: currentLinks?.proxyUrl || null,
      drm: drmInfo,
    }
    navigate(
      `/player?url=${encodeURIComponent(playUrl)}` +
      `&directUrl=${encodeURIComponent(link.proxyUrl ? link.url : '')}` +
      `&title=${encodeURIComponent(selectedEpisode?.name || details?.name || '')}` +
      `&poster=${encodeURIComponent(details?.posterUrl || '')}` +
      `&type=${link.type}&isM3u8=${link.isM3u8}&isDash=${link.isDash}` +
      `&sessionId=${encodeURIComponent(currentLinks?.sessionId || '')}` +
      `&subtitles=${encodeURIComponent(JSON.stringify(currentLinks?.subtitles || []))}` +
      (drmInfo ? `&drm=${encodeURIComponent(JSON.stringify(drmInfo))}` : ''),
      { state },
    )
    setLinkDialogOpen(false)
  }, [navigate, selectedEpisode, details, currentLinks])

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 3, mb: 2 }} />
        <Skeleton variant="text" width="60%" height={40} />
        <Skeleton variant="text" width="40%" />
        <Skeleton variant="text" width="80%" />
      </Box>
    )
  }

  if (!details) {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <Typography variant="h5" sx={{ color: desktopTheme.textMuted, mb: 2 }}>
          Failed to load details
        </Typography>
        <Button onClick={() => navigate('/')} variant="outlined">
          Go Home
        </Button>
      </Box>
    )
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box
        sx={{
          position: 'relative',
          height: 280,
          overflow: 'hidden',
          flexShrink: 0,
        }}
      >
        {details.backgroundPosterUrl ? (
          <Box
            component="img"
            src={details.backgroundPosterUrl}
            sx={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              filter: 'blur(24px) brightness(0.4)',
              transform: 'scale(1.1)',
            }}
          />
        ) : (
          <Box sx={{ width: '100%', height: '100%', bgcolor: desktopTheme.surfaceCard }} />
        )}

        <IconButton
          onClick={() => navigate('/')}
          sx={{
            position: 'absolute',
            top: 16,
            left: 16,
            width: 40,
            height: 40,
            borderRadius: '50%',
            bgcolor: `${desktopTheme.surfaceElevated}99`,
            color: desktopTheme.textPrimary,
            '&:hover': { bgcolor: desktopTheme.surfaceElevated },
            backdropFilter: 'blur(8px)',
          }}
        >
          <ArrowBackIcon />
        </IconButton>

        <Box
          sx={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            background: 'linear-gradient(transparent, #0C0C16)',
            px: 3,
            pb: 2,
            pt: 6,
          }}
        >
          <Typography variant="h2" sx={{ fontWeight: 700, mb: 0.5 }}>
            {details.name}
          </Typography>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            {details.type && <Chip label={details.displayType || details.type} size="small" sx={{ bgcolor: `${desktopTheme.accent}30`, color: desktopTheme.accent, fontWeight: 600 }} />}
            {details.showStatus && <Chip label={details.showStatus} size="small" variant="outlined" sx={{ color: desktopTheme.textMuted, borderColor: desktopTheme.divider }} />}
            {details.contentRating && <Chip label={details.contentRating} size="small" variant="outlined" sx={{ color: desktopTheme.textMuted, borderColor: desktopTheme.divider }} />}
            {details.year && <Typography variant="body2">{details.year}</Typography>}
            {details.duration && <Typography variant="body2">· {details.duration}m</Typography>}
            {details.score && (
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Rating value={parseFloat(details.score) / 2} precision={0.5} readOnly size="small" sx={{ color: desktopTheme.accent }} />
                <Typography variant="body2">{details.score}</Typography>
              </Box>
            )}
            {details.dubEpisodes && Object.keys(details.dubEpisodes).length > 0 && (
              <Typography variant="body2" sx={{ color: desktopTheme.textMuted }}>
                · {Object.entries(details.dubEpisodes).map(([k, v]) => `${k}: ${v}ep`).join(', ')}
              </Typography>
            )}
            {details.nextAiring && (
              <Typography variant="body2" sx={{ color: desktopTheme.accent }}>
                · Next: E{details.nextAiring.episode}{details.nextAiring.season != null ? ` S${details.nextAiring.season}` : ''} {new Date(details.nextAiring.unixTime * 1000).toLocaleDateString()}
              </Typography>
            )}
          </Box>
        </Box>
      </Box>

      <Box sx={{ flex: 1, overflow: 'auto', px: 3, py: 2 }}>
        {details.plot && (
          <Typography variant="body1" sx={{ color: desktopTheme.textMuted, mb: 2, lineHeight: 1.6 }}>
            {details.plot}
          </Typography>
        )}

        {details.tags && details.tags.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 2 }}>
            {details.tags.map((tag) => (
              <Chip key={tag} label={tag} size="small" variant="outlined" sx={{ color: desktopTheme.textMuted, borderColor: desktopTheme.divider }} />
            ))}
          </Box>
        )}

        {details.actors && details.actors.length > 0 && (
          <Box sx={{ mb: 2, display: 'flex', gap: 1, overflowX: 'auto', pb: 1 }}>
            {details.actors.map((actor) => (
              <Box
                key={actor.name}
                sx={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  minWidth: 72,
                  gap: 0.5,
                }}
              >
                <Box
                  sx={{
                    width: 56,
                    height: 56,
                    borderRadius: '50%',
                    overflow: 'hidden',
                    bgcolor: desktopTheme.surfaceElevated,
                  }}
                >
                  {actor.image ? (
                    <Box
                      component="img"
                      src={actor.image}
                      sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    <Box sx={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, color: desktopTheme.textMuted }}>
                      {actor.name[0]}
                    </Box>
                  )}
                </Box>
                <Typography variant="caption" sx={{ textAlign: 'center', lineHeight: 1.2 }}>
                  {actor.name}
                </Typography>
                {actor.role && (
                  <Typography variant="caption" sx={{ color: desktopTheme.textMuted, textAlign: 'center', lineHeight: 1.2 }}>
                    {actor.role}
                  </Typography>
                )}
                {actor.voiceActorName && (
                  <Typography variant="caption" sx={{ color: desktopTheme.accent, textAlign: 'center', lineHeight: 1.2, fontSize: '0.6rem' }}>
                    VA: {actor.voiceActorName}
                  </Typography>
                )}
              </Box>
            ))}
          </Box>
        )}

        {details.trailers && details.trailers.length > 0 && (
          <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
            {details.trailers.map((t, i) => (
              <Button
                key={i}
                variant="outlined"
                size="small"
                startIcon={<OpenInNewIcon />}
                onClick={() => window.open(t.url, '_blank')}
                sx={{ color: desktopTheme.textMuted, borderColor: desktopTheme.divider }}
              >
                Trailer {i + 1}
              </Button>
            ))}
          </Box>
        )}

        {SINGLE_PLAY_TYPES.includes(details.type) && (
          <Button
            variant="contained"
            startIcon={<PlayArrowIcon />}
            onClick={() => handleEpisodeClick({ data: url, name: details.name })}
            sx={{
              bgcolor: desktopTheme.accent,
              color: '#fff',
              fontWeight: 600,
              px: 4,
              py: 1,
              borderRadius: 2,
              '&:hover': { bgcolor: `${desktopTheme.accent}dd` },
              mb: 2,
            }}
          >
            Watch Now
          </Button>
        )}

        {(details.type === 'Live') && (
          <Button
            variant="contained"
            fullWidth
            startIcon={<PlayArrowIcon />}
            onClick={() => handleEpisodeClick({ data: url, name: details.name })}
            sx={{
              bgcolor: '#e53935',
              color: '#fff',
              fontWeight: 700,
              py: 1.5,
              borderRadius: 2,
              '&:hover': { bgcolor: '#c62828' },
              mb: 2,
            }}
          >
            Watch Live
          </Button>
        )}

        {seasonList.length > 0 && (
          <Box sx={{ mb: 1.5 }}>
            <Tabs
              value={season}
              onChange={(_, v) => setSeason(v)}
              variant="scrollable"
              scrollButtons="auto"
              sx={{
                minHeight: 36,
                '& .MuiTab-root': { minHeight: 36, py: 0.5, color: desktopTheme.textMuted },
                '& .Mui-selected': { color: desktopTheme.accent },
                '& .MuiTabs-indicator': { bgcolor: desktopTheme.accent },
              }}
            >
              {seasonList.map((s) => (
                <Tab key={s} label={`Season ${s}`} value={s} />
              ))}
            </Tabs>
          </Box>
        )}

        {filteredEpisodes.length > 0 && (
          <Box sx={{ mb: 2 }}>
            {filteredEpisodes.map((ep, i) => (
              <EpisodeCard
                key={`${ep.data}-${i}`}
                episode={ep}
                onClick={() => handleEpisodeClick(ep)}
              />
            ))}
          </Box>
        )}

        {details.recommendations && details.recommendations.length > 0 && (
          <CategoryRow
            title="Recommendations"
            items={details.recommendations}
            onItemClick={(item) =>
              navigate(`/details?url=${encodeURIComponent(item.url)}&api=${encodeURIComponent(item.apiName)}&name=${encodeURIComponent(item.name)}`)
            }
          />
        )}
      </Box>

      <Dialog
        open={linkDialogOpen}
        onClose={() => setLinkDialogOpen(false)}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            bgcolor: desktopTheme.surfaceCard,
            borderRadius: 3,
          },
        }}
      >
        <DialogTitle sx={{ color: desktopTheme.textPrimary, fontWeight: 600 }}>
          {selectedEpisode?.name || 'Select Source'}
        </DialogTitle>
        <DialogContent>
          {linksLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <Typography variant="body1" sx={{ color: desktopTheme.textMuted }}>
                Extracting links...
              </Typography>
            </Box>
          ) : currentLinks && currentLinks.links.length > 0 ? (
            <>
              {currentLinks.subtitles && currentLinks.subtitles.length > 0 && (
                <Typography variant="caption" sx={{ color: desktopTheme.textMuted, px: 2, pb: 1, display: 'block' }}>
                  Subtitles: {currentLinks.subtitles.map(s => s.lang).join(', ')}
                </Typography>
              )}
            <List dense>
              {currentLinks.links.map((link, i) => (
                <ListItemButton
                  key={i}
                  onClick={() => handlePlayLink(link)}
                  sx={{
                    borderRadius: 2,
                    mb: 0.5,
                    '&:hover': { bgcolor: desktopTheme.surfaceElevated },
                  }}
                >
                  <ListItemText
                    primary={link.name || `Link ${i + 1}`}
                    secondary={`${link.quality}p · ${link.type}`}
                    primaryTypographyProps={{ color: desktopTheme.textPrimary, fontWeight: 500 }}
                    secondaryTypographyProps={{ color: desktopTheme.textMuted }}
                  />
                </ListItemButton>
              ))}
            </List>
            </>
          ) : (
            <Typography variant="body1" sx={{ color: desktopTheme.textMuted, textAlign: 'center', py: 3 }}>
              No links available
            </Typography>
          )}
        </DialogContent>
      </Dialog>
    </Box>
  )
}
