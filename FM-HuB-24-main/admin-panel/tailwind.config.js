/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      colors: {
        brand: {
          50: '#fff7ed',
          100: '#ffedd5',
          500: '#FF6B35',
          600: '#ea580c',
          700: '#c2410c',
        },
        dark: {
          50: '#f8fafc',
          100: '#0f172a',
          200: '#1e293b',
          300: '#334155',
          800: '#0a0a0a',
          900: '#000000',
        }
      }
    },
  },
  plugins: [],
}
