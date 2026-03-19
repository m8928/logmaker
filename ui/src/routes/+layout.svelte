<script lang="ts">
	import '../app.css';
	import Nav from '$lib/components/Nav.svelte';
	import Toast from '$lib/components/Toast.svelte';

	let { children } = $props();

	// Apply saved theme on mount
	$effect(() => {
		const saved = localStorage.getItem('theme');
		if (saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
			document.documentElement.classList.add('dark');
		}
	});
</script>

<div class="app-shell">
	<Nav />
	<main class="app-main">
		{@render children()}
	</main>
</div>

<Toast />

<style>
	.app-shell {
		display: flex;
		min-height: 100vh;
	}

	.app-main {
		flex: 1;
		margin-left: var(--sidebar-w);
		padding: 2rem;
		min-height: 100vh;
		display: flex;
		flex-direction: column;
	}

	@media (max-width: 768px) {
		.app-shell {
			flex-direction: column;
		}
		.app-main {
			margin-left: 0;
			padding: 1rem;
		}
	}
</style>
