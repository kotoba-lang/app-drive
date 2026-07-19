import { etzhayyimUIKit } from '@etzhayyim/design-system/plugin';

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './src/**/*.{html,js,svelte,ts}',
    '../../../../../packages/ts/design-system/dist/**/*.{svelte,js}',
    '../../../../../packages/ts/design-system/dist/**/*.{svelte,js}',
  ],
  darkMode: ['selector', '[data-theme="dark"]'],
  plugins: [etzhayyimUIKit],
};
