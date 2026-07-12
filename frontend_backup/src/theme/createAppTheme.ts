import { createTheme, type ThemeOptions } from '@mui/material/styles';
import { darkPalette, lightPalette, fontFamily } from './tokens';

export type ColorMode = 'dark' | 'light';

export function createAppTheme(mode: ColorMode) {
  const p = mode === 'dark' ? darkPalette : lightPalette;

  const options: ThemeOptions = {
    palette: {
      mode,
      background: { default: p.background, paper: p.surface },
      text: { primary: p.textPrimary, secondary: p.textSecondary },
      primary: { main: p.accent },
      divider: p.border,
      success: { main: p.online },
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: fontFamily.body,
      h1: { fontFamily: fontFamily.display, fontWeight: 600 },
      h2: { fontFamily: fontFamily.display, fontWeight: 600 },
      h3: { fontFamily: fontFamily.display, fontWeight: 600 },
      h4: { fontFamily: fontFamily.display, fontWeight: 600, letterSpacing: -0.3 },
      h5: { fontFamily: fontFamily.display, fontWeight: 600 },
      h6: { fontFamily: fontFamily.display, fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            backgroundColor: p.background,
            scrollbarColor: `${p.border} transparent`,
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            border: `1px solid ${p.border}`,
          },
        },
      },
      MuiButton: {
        styleOverrides: { root: { borderRadius: 8 } },
      },
      MuiChip: {
        styleOverrides: { root: { fontFamily: fontFamily.mono, fontWeight: 600 } },
      },
    },
  };

  return createTheme(options);
}
