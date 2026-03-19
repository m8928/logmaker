import adapter from '@sveltejs/adapter-static';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

export default {
	preprocess: vitePreprocess(),
	kit: {
		adapter: adapter({
			pages: '../core/src/main/resources/static',
			assets: '../core/src/main/resources/static',
			fallback: 'index.html'
		})
	}
};
