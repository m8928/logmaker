<script lang="ts">
	import { api } from '$lib/api';
	import type { DashboardData } from '$lib/types';

	let data = $state<DashboardData | null>(null);
	let loading = $state(true);
	let error = $state(false);

	async function fetchData() {
		loading = true;
		error = false;
		try {
			data = await api.getDashboard();
		} catch {
			error = true;
		} finally {
			loading = false;
		}
	}

	$effect(() => {
		fetchData();
		const interval = setInterval(fetchData, 5000);
		return () => clearInterval(interval);
	});

	const statCards = $derived([
		{
			label: 'Makers',
			value: data?.maker ?? 0,
			icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></svg>`,
			color: 'indigo',
			borderColor: 'var(--accent)',
			href: '/maker'
		},
		{
			label: 'Logs',
			value: data?.log ?? 0,
			icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>`,
			color: 'emerald',
			borderColor: 'var(--success)',
			href: '/log'
		},
		{
			label: 'Senders',
			value: data?.sender ?? 0,
			icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>`,
			color: 'sky',
			borderColor: 'var(--info)',
			href: '/sender'
		},
		{
			label: 'Plugins',
			value: data?.plugin ?? 0,
			icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20.24 12.24a6 6 0 00-8.49-8.49L5 10.5V19h8.5z"/><line x1="16" y1="8" x2="2" y2="22"/><line x1="17.5" y1="15" x2="9" y2="15"/></svg>`,
			color: 'rose',
			borderColor: 'var(--danger)',
			href: '/plugin'
		}
	]);
</script>

<svelte:head>
	<title>Dashboard — LogMaker</title>
</svelte:head>

<div class="page">
	<header class="page-header">
		<div>
			<h1 class="page-title">Dashboard</h1>
			<p class="page-subtitle">System overview — auto-refreshes every 5 seconds</p>
		</div>
		<div
			class="status-dot"
			class:active={!loading && !error}
			title={loading ? 'Updating…' : error ? 'Connection error' : 'Live'}
			aria-label="Connection status: {loading ? 'updating' : error ? 'error' : 'active'}"
		></div>
	</header>

	{#if error}
		<div class="error-banner">
			Unable to connect to server. Retrying automatically...
		</div>
	{/if}

	<!-- Stat cards -->
	<div class="stat-grid">
		{#each statCards as card}
			<a href={card.href} class="stat-card stat-{card.color}" style="border-top: 3px solid {card.borderColor}">
				<div class="stat-icon">{@html card.icon}</div>
				<div class="stat-body">
					<span class="stat-value">{loading ? '—' : card.value}</span>
					<span class="stat-label">{card.label}</span>
				</div>
			</a>
		{/each}
	</div>

	<!-- Performance row -->
	<div class="perf-grid">
		<!-- EPS -->
		<div class="perf-card">
			<div class="perf-header">
				<span class="perf-title">Events / Second</span>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="2">
					<polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
				</svg>
			</div>
			<div class="eps-display">
				<div class="eps-actual">
					<span class="eps-value">{loading ? '—' : (data?.actualEps ?? 0).toLocaleString()}</span>
					<span class="eps-unit">eps actual</span>
				</div>
				<div class="eps-divider">/</div>
				<div class="eps-target">
					<span class="eps-value secondary">{loading ? '—' : (data?.eps ?? 0).toLocaleString()}</span>
					<span class="eps-unit">eps target</span>
				</div>
			</div>
			{#if data && data.eps > 0}
				{@const epsPct = Math.min(100, Math.round((data.actualEps / data.eps) * 100))}
				<div class="progress-wrap">
					<div
						class="progress-bar"
						role="progressbar"
						aria-valuenow={epsPct}
						aria-valuemin={0}
						aria-valuemax={100}
						aria-label="EPS utilization"
					>
						<div class="progress-fill" style="width: {epsPct}%"></div>
					</div>
					<span class="progress-pct">{epsPct}%</span>
				</div>
			{/if}
		</div>

		<!-- CPU -->
		<div class="perf-card">
			<div class="perf-header">
				<span class="perf-title">CPU Usage</span>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--warning)" stroke-width="2">
					<rect x="4" y="4" width="16" height="16" rx="2" /><rect x="9" y="9" width="6" height="6" /><line x1="9" y1="1" x2="9" y2="4" /><line x1="15" y1="1" x2="15" y2="4" /><line x1="9" y1="20" x2="9" y2="23" /><line x1="15" y1="20" x2="15" y2="23" /><line x1="20" y1="9" x2="23" y2="9" /><line x1="20" y1="14" x2="23" y2="14" /><line x1="1" y1="9" x2="4" y2="9" /><line x1="1" y1="14" x2="4" y2="14" />
				</svg>
			</div>
			<div class="gauge-value">{loading ? '—' : `${data?.cpu ?? 0}%`}</div>
			<div class="progress-wrap">
				<div
					class="progress-bar"
					role="progressbar"
					aria-valuenow={loading ? 0 : Math.min(100, data?.cpu ?? 0)}
					aria-valuemin={0}
					aria-valuemax={100}
					aria-label="CPU usage"
				>
					<div
						class="progress-fill warning"
						style="width: {loading ? 0 : Math.min(100, data?.cpu ?? 0)}%"
					></div>
				</div>
			</div>
		</div>

		<!-- Memory -->
		<div class="perf-card">
			<div class="perf-header">
				<span class="perf-title">Memory</span>
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--info)" stroke-width="2">
					<path d="M4 14h16M4 10h16M9 6h6M9 18h6M6 6v12M18 6v12M3 6a2 2 0 012-2h14a2 2 0 012 2v12a2 2 0 01-2 2H5a2 2 0 01-2-2V6z"/>
				</svg>
			</div>
			<div class="gauge-value">{loading ? '—' : `${data?.memory ?? 0} MB`}</div>
			<div class="progress-wrap">
				<div
					class="progress-bar"
					role="progressbar"
					aria-valuenow={loading ? 0 : Math.min(100, Math.round(((data?.memory ?? 0) / 2048) * 100))}
					aria-valuemin={0}
					aria-valuemax={100}
					aria-label="Memory usage"
				>
					<div
						class="progress-fill info"
						style="width: {loading ? 0 : Math.min(100, ((data?.memory ?? 0) / 2048) * 100)}%"
					></div>
				</div>
				<span class="progress-pct">{loading ? '' : `${data?.memory ?? 0} MB`}</span>
			</div>
		</div>

		<!-- Threads -->
		{#if data?.thread !== undefined}
			<div class="perf-card">
				<div class="perf-header">
					<span class="perf-title">Threads</span>
					<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="var(--success)" stroke-width="2">
						<line x1="12" y1="2" x2="12" y2="6"/><line x1="12" y1="18" x2="12" y2="22"/><line x1="4.93" y1="4.93" x2="7.76" y2="7.76"/><line x1="16.24" y1="16.24" x2="19.07" y2="19.07"/><line x1="2" y1="12" x2="6" y2="12"/><line x1="18" y1="12" x2="22" y2="12"/><line x1="4.93" y1="19.07" x2="7.76" y2="16.24"/><line x1="16.24" y1="7.76" x2="19.07" y2="4.93"/>
					</svg>
				</div>
				<div class="gauge-value">{loading ? '—' : data.thread}</div>
			</div>
		{/if}
	</div>
</div>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 1.5rem;
		max-width: 1100px;
	}

	.page-header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
	}

	.page-title {
		font-size: 1.5rem;
		font-weight: 800;
		margin: 0 0 0.25rem;
		letter-spacing: -0.03em;
	}

	.page-subtitle {
		color: var(--text-secondary);
		font-size: 0.875rem;
		margin: 0;
	}

	.error-banner {
		padding: 0.75rem 1rem;
		background: var(--danger-light);
		color: var(--danger);
		border: 1px solid var(--danger);
		border-radius: var(--radius-sm);
		font-size: 0.875rem;
		font-weight: 500;
	}

	.status-dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		background: var(--border);
		margin-top: 0.5rem;
		transition: background 0.3s;
	}

	.status-dot.active {
		background: var(--success);
		animation: pulse-ring 2s ease-out infinite;
	}

	.stat-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 1rem;
	}

	.stat-card {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1.25rem;
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		text-decoration: none;
		transition: all 0.15s;
		box-shadow: var(--shadow-sm);
	}

	.stat-card:hover {
		transform: translateY(-2px);
		box-shadow: var(--shadow-md);
		border-color: var(--border-focus);
	}

	.stat-icon {
		width: 44px;
		height: 44px;
		border-radius: var(--radius-sm);
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.stat-indigo .stat-icon { background: var(--accent-light); color: var(--accent); }
	.stat-emerald .stat-icon { background: var(--success-light); color: var(--success); }
	.stat-sky .stat-icon { background: var(--info-light); color: var(--info); }
	.stat-rose .stat-icon { background: var(--danger-light); color: var(--danger); }

	.stat-body {
		display: flex;
		flex-direction: column;
	}

	.stat-value {
		font-size: 1.75rem;
		font-weight: 800;
		line-height: 1;
		letter-spacing: -0.04em;
		color: var(--text-primary);
	}

	.stat-label {
		font-size: 0.8125rem;
		color: var(--text-secondary);
		margin-top: 0.25rem;
	}

	.perf-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 1rem;
	}

	.perf-card {
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		padding: 1.25rem;
		box-shadow: var(--shadow-sm);
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	.perf-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.perf-title {
		font-size: 0.8125rem;
		font-weight: 600;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.eps-display {
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	.eps-actual,
	.eps-target {
		display: flex;
		flex-direction: column;
	}

	.eps-value {
		font-size: 1.5rem;
		font-weight: 800;
		letter-spacing: -0.04em;
		color: var(--text-primary);
	}

	.eps-value.secondary {
		color: var(--text-secondary);
		font-size: 1.125rem;
	}

	.eps-unit {
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	.eps-divider {
		font-size: 1.5rem;
		color: var(--border);
		font-weight: 300;
	}

	.gauge-value {
		font-size: 2rem;
		font-weight: 800;
		letter-spacing: -0.04em;
		color: var(--text-primary);
	}

	.progress-wrap {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.progress-bar {
		flex: 1;
		height: 6px;
		background: var(--bg-raised);
		border-radius: 3px;
		overflow: hidden;
	}

	.progress-fill {
		height: 100%;
		background: var(--accent);
		border-radius: 3px;
		transition: width 0.5s ease;
	}

	.progress-fill.warning {
		background: var(--warning);
	}

	.progress-fill.info {
		background: var(--info);
	}

	.progress-pct {
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-secondary);
		width: 3rem;
		text-align: right;
	}

	@media (max-width: 900px) {
		.stat-grid {
			grid-template-columns: repeat(2, 1fr);
		}
	}

	@media (max-width: 500px) {
		.stat-grid {
			grid-template-columns: 1fr 1fr;
		}
	}
</style>
