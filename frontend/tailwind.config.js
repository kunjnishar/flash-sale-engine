/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  safelist: [
    'border-pess', 'bg-pess/10', 'bg-pess', 'text-pess',
    'border-opt', 'bg-opt/10', 'bg-opt', 'text-opt',
    'border-dist', 'bg-dist/10', 'bg-dist', 'text-dist',
    'border-lua', 'bg-lua/10', 'bg-lua', 'text-lua',
    'bg-limit', 'text-limit',
  ],
  theme: {
    extend: {
      colors: {
        void: '#0A0E14',
        panel: '#121822',
        line: '#1E2733',
        ink: '#E8ECF1',
        mute: '#6B7684',
        flash: '#FF6B35',
        mint: '#3DDC97',
        danger: '#FF5470',
        pess: '#5B8DEF',
        opt: '#F5A623',
        dist: '#A78BFA',
        lua: '#14B8A6',
        limit: '#F472B6',
      },
      fontFamily: {
        display: ['"Space Grotesk"', 'sans-serif'],
        sans: ['Inter', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
    },
  },
  plugins: [],
};