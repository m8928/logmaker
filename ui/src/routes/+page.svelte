<script lang="ts">
	import { api } from '$lib/api';
	import type { DashboardData, Log } from '$lib/types';

	let data = $state<DashboardData | null>(null);
	let logs = $state<Log[]>([]);
	let loading = $state(true);
	let error = $state(false);
	let lastUpdated = $state(0);
	let secondsAgo = $state(0);

	async function fetchData() {
		loading = true;
		error = false;
		try {
			[data, logs] = await Promise.all([api.getDashboard(), api.getLogs()]);
			lastUpdated = Date.now();
			secondsAgo = 0;
		} catch {
			error = true;
		} finally {
			loading = false;
		}
	}

	$effect(() => {
		fetchData();
		const fetchInterval = setInterval(fetchData, 5000);
		const clockInterval = setInterval(() => {
			if (lastUpdated > 0) {
				secondsAgo = Math.floor((Date.now() - lastUpdated) / 1000);
			}
		}, 1000);
		return () => {
			clearInterval(fetchInterval);
			clearInterval(clockInterval);
		};
	});

	const runningLogs = $derived(logs.filter((l) => l.status === true || l.currentEps > 0));

	const metricTiles = $derived([
		{
			label: 'Makers',
			value: data?.maker ?? 0,
			icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></svg>`,
			accent: 'var(--accent)',
			bg: 'var(--accent-light)',
			href: '/maker'
		},
		{
			label: 'Senders',
			value: data?.sender ?? 0,
			icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>`,
			accent: 'var(--info)',
			bg: 'var(--info-light)',
			href: '/sender'
		},
		{
			label: 'Logs Running',
			value: runningLogs.length,
			icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="10 15 15 12 10 9 10 15"/></svg>`,
			accent: 'var(--success)',
			bg: 'var(--success-light)',
			href: '/log'
		},
		{
			label: 'Plugins',
			value: data?.plugin ?? 0,
			icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.24 12.24a6 6 0 00-8.49-8.49L5 10.5V19h8.5z"/><line x1="16" y1="8" x2="2" y2="22"/><line x1="17.5" y1="15" x2="9" y2="15"/></svg>`,
			accent: 'var(--warning)',
			bg: 'var(--warning-light)',
			href: '/plugin'
		}
	]);

	function epsPct(log: Log): number {
		if (log.eps <= 0) return 0;
		return Math.min(100, Math.round((log.currentEps / log.eps) * 100));
	}

	function extractMakers(format: string): string[] {
		const matches = format.match(/<([^>]+)>/g);
		if (!matches) return [];
		return [...new Set(matches.map((m) => m.slice(1, -1)))];
	}

	const cpuPct = $derived(Math.min(100, data?.cpu ?? 0));
	const memPct = $derived(
		data
			? Math.min(100, Math.round(((data.memory ?? 0) / 2048) * 100))
			: 0
	);
	const totalEpsPct = $derived(
		data && data.eps > 0
			? Math.min(100, Math.round((data.actualEps / data.eps) * 100))
			: 0
	);
</script>

<svelte:head>
	<title>Dashboard — LogMaker</title>
</svelte:head>

<div class="dash">
	<!-- Top bar -->
	<div class="topbar">
		<div class="topbar-left">
			<h1 class="dash-title">Dashboard</h1>
			<div class="live-badge" class:live={!loading && !error} class:err={error}>
				<span class="live-dot"></span>
				{#if error}
					Connection error
				{:else if loading && secondsAgo === 0}
					Connecting…
				{:else}
					Live · updated {secondsAgo}s ago
				{/if}
			</div>
		</div>
		<div class="topbar-right">
			{#if error}
				<div class="error-chip">
					<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
					Server unreachable
				</div>
			{/if}
			<button class="btn btn-ghost btn-sm" onclick={fetchData} disabled={loading} aria-label="Refresh dashboard">
				<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class:spin={loading}><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				Refresh
			</button>
		</div>
	</div>

	<!-- Metric strip -->
	<div class="metric-strip">
		{#each metricTiles as tile}
			<a href={tile.href} class="metric-tile" style="--tile-accent:{tile.accent};--tile-bg:{tile.bg}">
				<div class="metric-icon">
					{@html tile.icon}
				</div>
				<div class="metric-body">
					<span class="metric-value">{loading ? '—' : tile.value.toLocaleString()}</span>
					<span class="metric-label">{tile.label}</span>
				</div>
			</a>
		{/each}
	</div>

	<!-- Main 2-col layout -->
	<div class="main-grid">
		<!-- Left: Live Activity Panel -->
		<div class="panel activity-panel">
			<div class="panel-header">
				<span class="panel-title">
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="10 15 15 12 10 9 10 15"/></svg>
					Live Activity
				</span>
				<span class="panel-meta">{runningLogs.length} active</span>
			</div>
			<div class="activity-list">
				{#if loading && logs.length === 0}
					<div class="activity-loading">
						<span class="spinner-muted"></span>
						<span>Loading…</span>
					</div>
				{:else if runningLogs.length === 0}
					<div class="activity-empty">
						<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1"><circle cx="12" cy="12" r="10"/><polyline points="10 15 15 12 10 9 10 15"/></svg>
						<span>No active log generators</span>
						<a href="/log" class="btn btn-ghost btn-sm">Go to Logs</a>
					</div>
				{:else}
					{#each runningLogs as log}
						{@const makers = extractMakers(log.format)}
						{@const pct = epsPct(log)}
						<div class="activity-card">
							<div class="activity-card-top">
								<div class="activity-name">
									<span class="running-dot"></span>
									{log.name}
								</div>
								<div class="activity-eps">
									<span class="eps-actual-val">{log.currentEps.toLocaleString()}</span>
									<span class="eps-sep">/</span>
									<span class="eps-target-val">{log.eps.toLocaleString()}</span>
									<span class="eps-unit">eps</span>
								</div>
							</div>
							<div class="activity-format mono">{log.format.length > 60 ? log.format.slice(0, 60) + '…' : log.format}</div>
							<div class="activity-card-bottom">
								<div class="sender-tags">
									{#each log.sender.slice(0, 3) as s}
										<span class="stag">{s}</span>
									{/each}
									{#if log.sender.length > 3}
										<span class="stag stag-more">+{log.sender.length - 3}</span>
									{/if}
								</div>
								<div class="activity-bar-wrap">
									<div class="activity-bar">
										<div class="activity-bar-fill" style="width:{pct}%;background:{pct >= 90 ? 'var(--success)' : pct >= 50 ? 'var(--accent)' : 'var(--warning)'}"></div>
									</div>
									<span class="activity-pct">{pct}%</span>
								</div>
							</div>
						</div>
					{/each}
				{/if}
			</div>
		</div>

		<!-- Right: System Health Panel -->
		<div class="panel health-panel">
			<div class="panel-header">
				<span class="panel-title">
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
					System Health
				</span>
			</div>

			<div class="health-body">
				<!-- EPS overview -->
				<div class="health-section">
					<div class="health-section-label">Events / Second</div>
					<div class="eps-overview">
						<div class="eps-big">
							<span class="eps-big-val">{loading ? '—' : (data?.actualEps ?? 0).toLocaleString()}</span>
							<span class="eps-big-unit">actual</span>
						</div>
						<div class="eps-divider">/</div>
						<div class="eps-big eps-big-dim">
							<span class="eps-big-val">{loading ? '—' : (data?.eps ?? 0).toLocaleString()}</span>
							<span class="eps-big-unit">target</span>
						</div>
					</div>
					<div class="h-bar-wrap">
						<div class="h-bar">
							<div class="h-bar-fill" style="width:{totalEpsPct}%;background:var(--accent)"></div>
						</div>
						<span class="h-bar-pct">{totalEpsPct}%</span>
					</div>
				</div>

				<div class="health-divider"></div>

				<!-- Gauges row -->
				<div class="gauges-row">
					<!-- CPU -->
					<div class="gauge-block">
						<div class="gauge-ring-wrap">
							<svg class="gauge-ring" viewBox="0 0 80 80" width="80" height="80">
								<circle cx="40" cy="40" r="30" fill="none" stroke="var(--bg-raised)" stroke-width="8"/>
								<circle
									cx="40" cy="40" r="30"
									fill="none"
									stroke="var(--warning)"
									stroke-width="8"
									stroke-linecap="round"
									stroke-dasharray="{188.5}"
									stroke-dashoffset="{188.5 - (188.5 * cpuPct / 100)}"
									transform="rotate(-90 40 40)"
									style="transition: stroke-dashoffset 0.6s ease"
								/>
							</svg>
							<div class="gauge-inner">
								<span class="gauge-val">{loading ? '—' : `${cpuPct}%`}</span>
							</div>
						</div>
						<div class="gauge-label">CPU</div>
					</div>

					<!-- Memory -->
					<div class="gauge-block">
						<div class="gauge-ring-wrap">
							<svg class="gauge-ring" viewBox="0 0 80 80" width="80" height="80">
								<circle cx="40" cy="40" r="30" fill="none" stroke="var(--bg-raised)" stroke-width="8"/>
								<circle
									cx="40" cy="40" r="30"
									fill="none"
									stroke="var(--info)"
									stroke-width="8"
									stroke-linecap="round"
									stroke-dasharray="188.5"
									stroke-dashoffset="{188.5 - (188.5 * memPct / 100)}"
									transform="rotate(-90 40 40)"
									style="transition: stroke-dashoffset 0.6s ease"
								/>
							</svg>
							<div class="gauge-inner">
								<span class="gauge-val" style="font-size:0.75rem">{loading ? '—' : `${data?.memory ?? 0}MB`}</span>
							</div>
						</div>
						<div class="gauge-label">Memory</div>
					</div>

					<!-- Threads -->
					{#if data?.thread !== undefined}
						<div class="gauge-block">
							<div class="gauge-ring-wrap">
								<svg class="gauge-ring" viewBox="0 0 80 80" width="80" height="80">
									<circle cx="40" cy="40" r="30" fill="none" stroke="var(--bg-raised)" stroke-width="8"/>
									<circle
										cx="40" cy="40" r="30"
										fill="none"
										stroke="var(--success)"
										stroke-width="8"
										stroke-linecap="round"
										stroke-dasharray="188.5"
										stroke-dashoffset="{188.5 - (188.5 * Math.min(100, (data.thread / 200) * 100) / 100)}"
										transform="rotate(-90 40 40)"
										style="transition: stroke-dashoffset 0.6s ease"
									/>
								</svg>
								<div class="gauge-inner">
									<span class="gauge-val">{data.thread}</span>
								</div>
							</div>
							<div class="gauge-label">Threads</div>
						</div>
					{/if}
				</div>

				<div class="health-divider"></div>

				<!-- Log count stat -->
				<div class="health-section">
					<div class="health-stat-row">
						<div class="health-stat">
							<span class="health-stat-val">{loading ? '—' : (data?.log ?? 0)}</span>
							<span class="health-stat-label">Total Logs</span>
						</div>
						<div class="health-stat">
							<span class="health-stat-val" style="color:var(--success)">{runningLogs.length}</span>
							<span class="health-stat-label">Running</span>
						</div>
						<div class="health-stat">
							<span class="health-stat-val" style="color:var(--text-muted)">{(data?.log ?? 0) - runningLogs.length}</span>
							<span class="health-stat-label">Stopped</span>
						</div>
					</div>
				</div>
			</div>
		</div>
	</div>
</div>

<style>
	.dash {
		display: flex;
		flex-direction: column;
		gap: 0;
		width: 100%;
	}

	/* ── Top bar ── */
	.topbar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding-bottom: 1.25rem;
		border-bottom: 1px solid var(--border);
		margin-bottom: 1.25rem;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.topbar-left {
		display: flex;
		align-items: center;
		gap: 1rem;
	}

	.topbar-right {
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	.dash-title {
		font-size: 1.375rem;
		font-weight: 700;
		margin: 0;
		letter-spacing: -0.025em;
	}

	.live-badge {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
		padding: 0.25rem 0.625rem;
		border: 1px solid var(--border);
		border-radius: 100px;
		font-size: 0.75rem;
		color: var(--text-muted);
		background: var(--bg-raised);
	}

	.live-badge.live {
		border-color: color-mix(in srgb, var(--success) 40%, transparent);
		color: var(--success);
		background: var(--success-light);
	}

	.live-badge.err {
		border-color: color-mix(in srgb, var(--danger) 40%, transparent);
		color: var(--danger);
		background: var(--danger-light);
	}

	.live-dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: currentColor;
		flex-shrink: 0;
	}

	.live-badge.live .live-dot {
		animation: pulse-ring 2s ease-out infinite;
	}

	.error-chip {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
		padding: 0.25rem 0.625rem;
		background: var(--danger-light);
		color: var(--danger);
		border-radius: var(--radius-sm);
		font-size: 0.75rem;
		font-weight: 500;
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}

	.spin {
		animation: spin 0.8s linear infinite;
	}

	/* ── Metric strip ── */
	.metric-strip {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		overflow: hidden;
		margin-bottom: 1.25rem;
		background: var(--bg-surface);
	}

	.metric-tile {
		display: flex;
		align-items: center;
		gap: 0.875rem;
		padding: 1rem 1.25rem;
		text-decoration: none;
		border-right: 1px solid var(--border);
		transition: background 0.15s;
		border-top: 2px solid var(--tile-accent);
	}

	.metric-tile:last-child {
		border-right: none;
	}

	.metric-tile:hover {
		background: var(--bg-raised);
	}

	.metric-icon {
		width: 32px;
		height: 32px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: var(--radius-sm);
		background: var(--tile-bg);
		color: var(--tile-accent);
		flex-shrink: 0;
	}

	.metric-body {
		display: flex;
		flex-direction: column;
		gap: 0.1rem;
	}

	.metric-value {
		font-size: 1.5rem;
		font-weight: 700;
		letter-spacing: -0.04em;
		line-height: 1;
		color: var(--text-primary);
	}

	.metric-label {
		font-size: 0.75rem;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
		font-weight: 500;
	}

	/* ── Main 2-col grid ── */
	.main-grid {
		display: grid;
		grid-template-columns: 3fr 2fr;
		gap: 1.25rem;
		align-items: start;
	}

	/* ── Panel ── */
	.panel {
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-surface);
		overflow: hidden;
	}

	.panel-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0.75rem 1rem;
		border-bottom: 1px solid var(--border);
		background: var(--bg-raised);
	}

	.panel-title {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-size: 0.8125rem;
		font-weight: 600;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.panel-meta {
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	/* ── Activity panel ── */
	.activity-list {
		display: flex;
		flex-direction: column;
		gap: 0;
	}

	.activity-loading {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 1.5rem;
		color: var(--text-muted);
		font-size: 0.875rem;
	}

	.activity-empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.75rem;
		padding: 3rem 1rem;
		color: var(--text-muted);
		font-size: 0.8125rem;
		text-align: center;
	}

	.activity-card {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		padding: 0.875rem 1rem;
		border-bottom: 1px solid var(--border);
		transition: background 0.12s;
	}

	.activity-card:last-child {
		border-bottom: none;
	}

	.activity-card:hover {
		background: var(--bg-raised);
	}

	.activity-card-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
	}

	.activity-name {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-size: 0.875rem;
		font-weight: 600;
		color: var(--text-primary);
	}

	.running-dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--success);
		flex-shrink: 0;
		animation: pulse-ring 2s ease-out infinite;
	}

	.activity-eps {
		display: flex;
		align-items: baseline;
		gap: 0.25rem;
		font-size: 0.8125rem;
		white-space: nowrap;
	}

	.eps-actual-val {
		font-weight: 700;
		color: var(--text-primary);
	}

	.eps-sep {
		color: var(--text-muted);
	}

	.eps-target-val {
		color: var(--text-secondary);
	}

	.eps-unit {
		font-size: 0.6875rem;
		color: var(--text-muted);
		margin-left: 1px;
	}

	.activity-format {
		font-size: 0.75rem;
		color: var(--text-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.activity-card-bottom {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
	}

	.sender-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 0.3rem;
	}

	.stag {
		display: inline-block;
		padding: 0.125rem 0.5rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: 100px;
		font-size: 0.6875rem;
		color: var(--text-secondary);
		font-weight: 500;
	}

	.stag-more {
		color: var(--text-muted);
	}

	.activity-bar-wrap {
		display: flex;
		align-items: center;
		gap: 0.375rem;
		flex-shrink: 0;
		min-width: 100px;
	}

	.activity-bar {
		flex: 1;
		height: 4px;
		background: var(--bg-raised);
		border-radius: 2px;
		overflow: hidden;
	}

	.activity-bar-fill {
		height: 100%;
		border-radius: 2px;
		transition: width 0.5s ease;
	}

	.activity-pct {
		font-size: 0.6875rem;
		font-weight: 600;
		color: var(--text-muted);
		width: 2.5rem;
		text-align: right;
	}

	/* ── Health panel ── */
	.health-body {
		display: flex;
		flex-direction: column;
	}

	.health-section {
		padding: 1rem;
	}

	.health-divider {
		height: 1px;
		background: var(--border);
	}

	.health-section-label {
		font-size: 0.6875rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		color: var(--text-muted);
		margin-bottom: 0.75rem;
	}

	.eps-overview {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		margin-bottom: 0.75rem;
	}

	.eps-big {
		display: flex;
		flex-direction: column;
	}

	.eps-big-val {
		font-size: 1.75rem;
		font-weight: 700;
		letter-spacing: -0.04em;
		line-height: 1;
		color: var(--text-primary);
	}

	.eps-big-dim .eps-big-val {
		color: var(--text-secondary);
		font-size: 1.25rem;
	}

	.eps-big-unit {
		font-size: 0.6875rem;
		color: var(--text-muted);
		margin-top: 0.125rem;
	}

	.eps-divider {
		font-size: 1.5rem;
		color: var(--border);
		font-weight: 300;
	}

	.h-bar-wrap {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.h-bar {
		flex: 1;
		height: 5px;
		background: var(--bg-raised);
		border-radius: 3px;
		overflow: hidden;
	}

	.h-bar-fill {
		height: 100%;
		border-radius: 3px;
		transition: width 0.6s ease;
	}

	.h-bar-pct {
		font-size: 0.6875rem;
		font-weight: 600;
		color: var(--text-muted);
		width: 2.5rem;
		text-align: right;
	}

	/* ── Gauge rings ── */
	.gauges-row {
		display: flex;
		align-items: flex-start;
		justify-content: space-around;
		padding: 1rem;
		gap: 0.5rem;
	}

	.gauge-block {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.5rem;
	}

	.gauge-ring-wrap {
		position: relative;
		width: 80px;
		height: 80px;
	}

	.gauge-ring {
		display: block;
	}

	.gauge-inner {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.gauge-val {
		font-size: 0.875rem;
		font-weight: 700;
		letter-spacing: -0.03em;
		color: var(--text-primary);
	}

	.gauge-label {
		font-size: 0.6875rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		color: var(--text-muted);
	}

	/* ── Health stats ── */
	.health-stat-row {
		display: flex;
		gap: 1.5rem;
	}

	.health-stat {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
	}

	.health-stat-val {
		font-size: 1.375rem;
		font-weight: 700;
		letter-spacing: -0.04em;
		line-height: 1;
		color: var(--text-primary);
	}

	.health-stat-label {
		font-size: 0.6875rem;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	/* ── Responsive ── */
	@media (max-width: 1100px) {
		.main-grid {
			grid-template-columns: 1fr;
		}
	}

	@media (max-width: 700px) {
		.metric-strip {
			grid-template-columns: repeat(2, 1fr);
		}
		.metric-tile {
			border-bottom: 1px solid var(--border);
		}
		.metric-tile:nth-child(2n) {
			border-right: none;
		}
		.metric-tile:nth-last-child(-n+2) {
			border-bottom: none;
		}
	}
</style>
