<script lang="ts">
	import { page } from '$app/stores';

	let isDark = $state(
		typeof document !== 'undefined' && document.documentElement.classList.contains('dark')
	);

	function toggleDark() {
		isDark = !isDark;
		document.documentElement.classList.toggle('dark', isDark);
		localStorage.setItem('theme', isDark ? 'dark' : 'light');
	}

	const navItems = [
		{
			href: '/',
			label: 'Dashboard',
			icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`
		},
		{
			href: '/maker',
			label: 'Maker',
			icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></svg>`
		},
		{
			href: '/sender',
			label: 'Sender',
			icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>`
		},
		{
			href: '/log',
			label: 'Log',
			icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>`
		},
		{
			href: '/plugin',
			label: 'Plugin',
			icon: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20.24 12.24a6 6 0 00-8.49-8.49L5 10.5V19h8.5z"/><line x1="16" y1="8" x2="2" y2="22"/><line x1="17.5" y1="15" x2="9" y2="15"/></svg>`
		}
	];

	function isActive(href: string) {
		if (href === '/') return $page.url.pathname === '/';
		return $page.url.pathname.startsWith(href);
	}
</script>

<nav class="nav-sidebar">
	<div class="nav-brand">
		<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2.5" aria-hidden="true">
			<polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
		</svg>
		<span class="nav-brand-text">LogMaker</span>
	</div>

	<ul class="nav-items">
		{#each navItems as item}
			<li>
				<a href={item.href} class="nav-item" class:active={isActive(item.href)}>
					<span class="nav-icon">{@html item.icon}</span>
					<span class="nav-label">{item.label}</span>
				</a>
			</li>
		{/each}
	</ul>

	<div class="nav-footer">
		<button
			onclick={toggleDark}
			class="theme-toggle"
			aria-label="Toggle dark mode"
			title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
		>
			{#if isDark}
				<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
					<circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
				</svg>
				<span>Light Mode</span>
			{:else}
				<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
					<path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z"/>
				</svg>
				<span>Dark Mode</span>
			{/if}
		</button>
	</div>
</nav>

<style>
	.nav-sidebar {
		width: var(--sidebar-w);
		height: 100vh;
		position: fixed;
		left: 0;
		top: 0;
		display: flex;
		flex-direction: column;
		background: var(--bg-surface);
		border-right: 1px solid var(--border);
		padding: 1rem 0.75rem;
		z-index: 100;
	}

	.nav-brand {
		display: flex;
		align-items: center;
		gap: 0.625rem;
		padding: 0.5rem 0.75rem 1.25rem;
		border-bottom: 1px solid var(--border);
		margin-bottom: 0.75rem;
	}

	.nav-brand-text {
		font-weight: 800;
		font-size: 1rem;
		letter-spacing: -0.02em;
		color: var(--text-primary);
	}

	.nav-items {
		list-style: none;
		margin: 0;
		padding: 0;
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.nav-item {
		display: flex;
		align-items: center;
		gap: 0.625rem;
		padding: 0.5rem 0.75rem;
		border-radius: var(--radius-sm);
		color: var(--text-secondary);
		text-decoration: none;
		font-size: 0.875rem;
		font-weight: 500;
		transition: all 0.15s;
	}

	.nav-item:hover {
		background: var(--bg-raised);
		color: var(--text-primary);
	}

	.nav-item.active {
		background: var(--accent-light);
		color: var(--accent);
		box-shadow: inset 3px 0 0 var(--accent);
		font-weight: 600;
	}

	.nav-icon {
		display: flex;
		align-items: center;
		flex-shrink: 0;
	}

	.nav-footer {
		padding-top: 0.75rem;
		border-top: 1px solid var(--border);
	}

	.theme-toggle {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		width: 100%;
		padding: 0.5rem 0.75rem;
		border-radius: var(--radius-sm);
		color: var(--text-secondary);
		font-size: 0.8125rem;
		font-weight: 500;
		background: none;
		border: none;
		cursor: pointer;
		transition: all 0.15s;
	}

	.theme-toggle:hover {
		background: var(--bg-raised);
		color: var(--text-primary);
	}

	@media (max-width: 768px) {
		.nav-sidebar {
			width: 100%;
			height: auto;
			position: sticky;
			top: 0;
			flex-direction: row;
			align-items: center;
			padding: 0 1rem;
			border-right: none;
			border-bottom: 1px solid var(--border);
		}

		.nav-brand {
			padding: 0.75rem 0.75rem 0.75rem 0;
			border-bottom: none;
			margin-bottom: 0;
		}

		.nav-items {
			flex-direction: row;
			flex: 1;
			gap: 2px;
			overflow-x: auto;
		}

		.nav-label {
			display: none;
		}

		.nav-item {
			padding: 0.5rem;
		}

		.nav-footer {
			padding-top: 0;
			border-top: none;
			padding-left: 0.5rem;
		}

		.theme-toggle span {
			display: none;
		}
	}
</style>
