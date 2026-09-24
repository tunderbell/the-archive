/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        apex: {
          darkest: '#0a0d11',    // Deepest background & navigation rail
          surface: '#11161d',    // Buffer interior background
          header:  '#151b22',    // Buffer title bar & sub-tab bar
          border:  '#212832',    // 1px hairline industrial borders
          borderLight: '#303b4b',// Slightly brighter border for active/focused elements
          amber:   '#f08c00',    // Primary APEX action buttons ([VIEW], [START], [HARVEST])
          amberHover: '#ff9900', // Hover state for action buttons
          cyan:    '#3898ec',    // Data keys, coordinates, status highlights
          green:   '#3fb950',    // Success & online connection indicators
          muted:   '#6b7a8d',    // Timestamps, tags, inactive tabs
          text:    '#e2e8f0',    // High-contrast clean white text
        }
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', 'Fira Code', 'Consolas', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
