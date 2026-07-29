import { useState } from 'react'
import { Box, Chip, Typography } from '@mui/material'
import type { SearchResultItem } from '../types'
import { desktopTheme } from '../theme/theme'

interface PosterCardProps {
  item: SearchResultItem
  onClick: () => void
  width?: number
}

export default function PosterCard({ item, onClick, width = 160 }: PosterCardProps) {
  const [hovered, setHovered] = useState(false)
  const aspectRatio = 2 / 3
  const height = width / aspectRatio

  const initials = item.name
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase()

  return (
    <Box
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      sx={{
        width,
        height,
        borderRadius: 3,
        overflow: 'hidden',
        position: 'relative',
        cursor: 'pointer',
        flexShrink: 0,
        bgcolor: desktopTheme.surfaceElevated,
        transition: 'transform 0.18s ease, box-shadow 0.18s ease',
        transform: hovered ? 'scale(1.05)' : 'scale(1)',
        boxShadow: hovered
          ? `0 0 0 2px ${desktopTheme.accent}, 0 8px 24px rgba(0,0,0,0.3)`
          : '0 2px 8px rgba(0,0,0,0.2)',
      }}
    >
      {item.posterUrl ? (
        <Box
          component="img"
          src={item.posterUrl}
          alt={item.name}
          sx={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            display: 'block',
          }}
          onError={(e) => {
            (e.target as HTMLImageElement).style.display = 'none'
          }}
        />
      ) : (
        <Box
          sx={{
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: desktopTheme.surfaceElevated,
          }}
        >
          <Typography variant="h5" sx={{ color: desktopTheme.textMuted, fontWeight: 700 }}>
            {initials}
          </Typography>
        </Box>
      )}

      <Box
        sx={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          background: 'linear-gradient(transparent, rgba(0,0,0,0.85))',
          p: 1,
          pt: 3,
        }}
      >
        {(item.displayType || item.type) && (
          <Chip
            label={item.displayType || item.type}
            size="small"
            sx={{
              height: 18,
              fontSize: '0.65rem',
              fontWeight: 600,
              bgcolor: desktopTheme.accent,
              color: '#fff',
              mb: 0.5,
              borderRadius: 1,
            }}
          />
        )}
        <Typography
          variant="body2"
          sx={{
            color: '#fff',
            fontWeight: 500,
            lineHeight: 1.2,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
          }}
        >
          {item.name}
        </Typography>
      </Box>
    </Box>
  )
}
