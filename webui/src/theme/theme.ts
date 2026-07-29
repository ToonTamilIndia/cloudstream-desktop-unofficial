import { createTheme } from '@mui/material/styles'

export const accentColors = {
  purple: '#7C6BFF',
  blue: '#3B82F6',
  green: '#10B981',
  red: '#EF4444',
  orange: '#F59E0B',
}

const bg = '#0C0C16'
const surface = '#161624'
const surfaceElevated = '#20202E'
const textPrimary = '#F3F4F6'
const textMuted = '#9CA3AF'
const divider = '#2A2A38'

export const darkTheme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: accentColors.purple },
    secondary: { main: accentColors.blue },
    background: { default: bg, paper: surface },
    text: { primary: textPrimary, secondary: textMuted },
    divider,
  },
  typography: {
    fontFamily: "'Roboto', sans-serif",
    h1: { fontSize: '1.75rem', fontWeight: 700 },
    h2: { fontSize: '1.5rem', fontWeight: 600 },
    h3: { fontSize: '1.25rem', fontWeight: 600 },
    body1: { fontSize: '0.9rem' },
    body2: { fontSize: '0.8rem', color: textMuted },
    caption: { fontSize: '0.75rem', color: textMuted },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: bg, color: textPrimary },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundColor: surface,
          backgroundImage: 'none',
          borderRadius: 12,
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          borderRadius: 8,
          fontWeight: 500,
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          fontWeight: 500,
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          textTransform: 'none',
          fontWeight: 500,
        },
      },
    },
    MuiIconButton: {
      styleOverrides: {
        root: {
          borderRadius: 12,
        },
      },
    },
  },
})

export interface DesktopTheme {
  accent: string
  background: string
  surfaceCard: string
  surfaceElevated: string
  textPrimary: string
  textMuted: string
  divider: string
}

export const desktopTheme: DesktopTheme = {
  accent: accentColors.purple,
  background: bg,
  surfaceCard: surface,
  surfaceElevated,
  textPrimary,
  textMuted,
  divider,
}
