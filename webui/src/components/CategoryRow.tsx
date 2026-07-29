import { Box, Typography } from '@mui/material'
import PosterCard from './PosterCard'
import type { SearchResultItem } from '../types'
import { desktopTheme } from '../theme/theme'

interface CategoryRowProps {
  title: string
  items: SearchResultItem[]
  onItemClick: (item: SearchResultItem) => void
}

export default function CategoryRow({ title, items, onItemClick }: CategoryRowProps) {
  if (!items.length) return null

  return (
    <Box sx={{ mb: 2.5 }}>
      <Typography
        variant="h3"
        sx={{
          px: 2,
          mb: 1,
          fontWeight: 600,
          color: desktopTheme.textPrimary,
        }}
      >
        {title}
      </Typography>
      <Box
        sx={{
          display: 'flex',
          gap: 1.5,
          overflowX: 'auto',
          px: 2,
          pb: 1,
          '&::-webkit-scrollbar': { height: 6 },
          '&::-webkit-scrollbar-thumb': {
            bgcolor: desktopTheme.divider,
            borderRadius: 3,
          },
        }}
      >
        {items.map((item, i) => (
          <PosterCard
            key={`${item.url}-${i}`}
            item={item}
            onClick={() => onItemClick(item)}
          />
        ))}
      </Box>
    </Box>
  )
}
