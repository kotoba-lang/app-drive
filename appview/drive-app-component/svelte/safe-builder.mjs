import { safeBuilder as upstreamSafeBuilder } from '../../../../../packages/svelte/vite-plugin-safe-builder/src/index.ts';

export function safeBuilder(options) {
  return upstreamSafeBuilder(options);
}
