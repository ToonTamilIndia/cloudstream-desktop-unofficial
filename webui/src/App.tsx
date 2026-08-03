import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom'
import { ThemeProvider, CssBaseline, Box } from '@mui/material'
import { darkTheme } from './theme/theme'
import NavigationDock from './components/NavigationDock'
import HomeScreen from './screens/HomeScreen'
import DetailsScreen from './screens/DetailsScreen'
import LibraryScreen from './screens/LibraryScreen'
import ExtensionsScreen from './screens/ExtensionsScreen'
import PlayerScreen from './screens/PlayerScreen'
import SettingsScreen from './screens/SettingsScreen'
import IptvScreen from './screens/IptvScreen'
import DownloadsScreen from './screens/DownloadsScreen'

const dockRoutes = ['/', '/library', '/extensions', '/iptv', '/downloads', '/settings']

function AppShell() {
  const location = useLocation()
  const showDock = dockRoutes.includes(location.pathname)
  const isPlayer = location.pathname === '/player'

  if (isPlayer) {
    return (
      <Box sx={{ width: '100vw', height: '100vh', bgcolor: '#000' }}>
        <Routes>
          <Route path="/player" element={<PlayerScreen />} />
        </Routes>
      </Box>
    )
  }

  return (
    <Box sx={{ width: '100vw', height: '100vh', display: 'flex', overflow: 'hidden' }}>
      {showDock && <NavigationDock />}
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Routes>
          <Route path="/" element={<HomeScreen />} />
          <Route path="/library" element={<LibraryScreen />} />
          <Route path="/extensions" element={<ExtensionsScreen />} />
          <Route path="/details" element={<DetailsScreen />} />
          <Route path="/settings" element={<SettingsScreen />} />
          <Route path="/iptv" element={<IptvScreen />} />
          <Route path="/downloads" element={<DownloadsScreen />} />
        </Routes>
      </Box>
    </Box>
  )
}

export default function App() {
  return (
    <ThemeProvider theme={darkTheme}>
      <CssBaseline />
      <BrowserRouter>
        <AppShell />
      </BrowserRouter>
    </ThemeProvider>
  )
}
