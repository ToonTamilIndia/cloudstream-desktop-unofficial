import { Box, IconButton, Typography } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { desktopTheme } from '../theme/theme'

interface TopBarProps {
  title?: string
  onBack?: () => void
  showBack?: boolean
  transparent?: boolean
  rightAction?: React.ReactNode
}

export default function TopBar({ title, onBack, showBack, transparent, rightAction }: TopBarProps) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        px: 2,
        py: 1,
        gap: 1,
        bgcolor: transparent ? 'transparent' : desktopTheme.surfaceCard,
        borderBottom: transparent ? 'none' : `1px solid ${desktopTheme.divider}`,
        zIndex: 20,
        minHeight: 56,
      }}
    >
      {showBack && onBack && (
        <IconButton
          onClick={onBack}
          sx={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            bgcolor: `${desktopTheme.surfaceElevated}80`,
            color: desktopTheme.textPrimary,
            '&:hover': { bgcolor: desktopTheme.surfaceElevated },
          }}
        >
          <ArrowBackIcon />
        </IconButton>
      )}

      {title && (
        <Typography variant="h3" sx={{ flexGrow: 1, fontWeight: 600 }}>
          {title}
        </Typography>
      )}

      <Box sx={{ flexGrow: title ? 0 : 1 }} />

      {rightAction}
    </Box>
  )
}
