import { Box, Typography, LinearProgress } from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import type { EpisodeData } from '../types'
import { desktopTheme } from '../theme/theme'

interface EpisodeCardProps {
  episode: EpisodeData
  season?: number
  progress?: number
  onClick: () => void
}

export default function EpisodeCard({ episode, progress, onClick }: EpisodeCardProps) {
  return (
    <Box
      onClick={onClick}
      sx={{
        display: 'flex',
        gap: 1.5,
        p: 1,
        borderRadius: 2,
        bgcolor: desktopTheme.surfaceElevated,
        cursor: 'pointer',
        transition: 'background 0.15s',
        '&:hover': { bgcolor: `${desktopTheme.accent}18` },
        mb: 1,
      }}
    >
      <Box
        sx={{
          width: 120,
          minWidth: 120,
          height: 68,
          borderRadius: 1.5,
          overflow: 'hidden',
          bgcolor: desktopTheme.surfaceCard,
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {episode.posterUrl ? (
          <Box
            component="img"
            src={episode.posterUrl}
            alt=""
            sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <PlayArrowIcon sx={{ color: desktopTheme.textMuted, fontSize: 28 }} />
        )}
      </Box>

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body1" sx={{ fontWeight: 600, lineHeight: 1.2, mb: 0.25 }}>
          {episode.name || `Episode ${episode.episode}`}
        </Typography>

        <Typography variant="caption" sx={{ display: 'block', mb: 0.5 }}>
          {episode.season != null && `S${episode.season} `}
          {episode.episode != null && `E${episode.episode}`}
          {episode.runtime != null && ` · ${episode.runtime}m`}
        </Typography>

        {episode.description && (
          <Typography
            variant="body2"
            sx={{
              color: desktopTheme.textMuted,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              display: '-webkit-box',
              WebkitLineClamp: 1,
              WebkitBoxOrient: 'vertical',
            }}
          >
            {episode.description}
          </Typography>
        )}

        {progress != null && progress > 0 && (
          <LinearProgress
            variant="determinate"
            value={Math.min(progress, 100)}
            sx={{
              mt: 0.5,
              height: 3,
              borderRadius: 2,
              bgcolor: desktopTheme.divider,
              '& .MuiLinearProgress-bar': { bgcolor: desktopTheme.accent },
            }}
          />
        )}
      </Box>
    </Box>
  )
}
