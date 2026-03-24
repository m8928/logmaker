<script lang="ts">
	import { slide } from 'svelte/transition';
	import { api } from '$lib/api';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import Tooltip from '$lib/components/Tooltip.svelte';
	import { addToast } from '$lib/stores/toast.svelte';
	import type { Log, Maker, Sender } from '$lib/types';

	let items = $state<Log[]>([]);
	let senders = $state<Sender[]>([]);
	let makers = $state<Maker[]>([]);
	let loading = $state(false);
	let search = $state('');

	let dialogOpen = $state(false);
	let editMode = $state(false);
	let importOpen = $state(false);
	let confirmOpen = $state(false);
	let confirmName = $state('');
	let confirmLoading = $state(false);

	// Form
	let formName = $state('');
	let formFormat = $state('');
	let formEps = $state(0);
	let formSenders = $state<string[]>([]);
	let previewText = $state('');
	let previewLoading = $state(false);
	let showMakerHelper = $state(true);
	let makerHelperShowName = $state(true);

	let formatTextarea = $state<HTMLTextAreaElement | null>(null);

	const filtered = $derived(
		search.trim()
			? items.filter((i) => i.name.toLowerCase().includes(search.toLowerCase()))
			: items
	);

	async function fetchItems() {
		loading = true;
		try {
			items = await api.getLogs();
		} catch {
			/* toast shown */
		} finally {
			loading = false;
		}
	}

	async function fetchSupport() {
		try {
			[senders, makers] = await Promise.all([api.getSenders(), api.getMakers()]);
		} catch {
			/* ignored */
		}
	}

	function openAdd() {
		editMode = false;
		formName = '';
		formFormat = '';
		formEps = 0;
		formSenders = [];
		previewText = '';
		dialogOpen = true;
		fetchSupport();
	}

	function openEdit(item: Log) {
		editMode = true;
		formName = item.name;
		formFormat = item.format;
		formEps = item.eps;
		formSenders = [...item.sender];
		previewText = '';
		dialogOpen = true;
		fetchSupport();
		runPreview();
	}

	function openCopy(item: Log) {
		editMode = false;
		formName = 'copy-of-' + item.name;
		formFormat = item.format;
		formEps = item.eps;
		formSenders = [...item.sender];
		previewText = '';
		dialogOpen = true;
		fetchSupport();
	}

	function closeDialog() {
		dialogOpen = false;
		previewText = '';
	}

	async function runPreview() {
		if (!formFormat) return;
		previewLoading = true;
		try {
			const result = await api.previewLog({
				name: formName,
				format: formFormat,
				eps: formEps,
				sender: formSenders
			});
			previewText = result.message ?? '';
		} catch (err: unknown) {
			previewText = err instanceof Error ? err.message : 'Preview error';
		} finally {
			previewLoading = false;
		}
	}

	function insertMaker(makerName: string) {
		const token = `<${makerName}>`;
		if (!formatTextarea) {
			formFormat = formFormat + token;
		} else {
			const start = formatTextarea.selectionStart;
			const end = formatTextarea.selectionEnd;
			formFormat = formFormat.slice(0, start) + token + formFormat.slice(end);
			setTimeout(() => {
				if (formatTextarea) {
					formatTextarea.selectionStart = formatTextarea.selectionEnd = start + token.length;
					formatTextarea.focus();
				}
			}, 0);
		}
		runPreview();
	}

	function toggleSender(name: string) {
		if (formSenders.includes(name)) {
			formSenders = formSenders.filter((s) => s !== name);
		} else {
			formSenders = [...formSenders, name];
		}
	}

	async function submit() {
		if (!formName.trim() || !formFormat.trim()) {
			addToast('warning', 'Name and Format are required');
			return;
		}
		if (formSenders.length === 0) {
			addToast('warning', 'At least one sender must be selected');
			return;
		}
		loading = true;
		try {
			const payload = { name: formName, format: formFormat, eps: formEps, sender: formSenders };
			if (editMode) await api.updateLog(formName, payload);
			else await api.createLog(payload);
			closeDialog();
			await fetchItems();
		} catch {
			/* toast shown */
		} finally {
			loading = false;
		}
	}

	function askDelete(name: string) {
		confirmName = name;
		confirmOpen = true;
	}

	async function confirmDelete() {
		confirmLoading = true;
		try {
			await api.deleteLog(confirmName);
			confirmOpen = false;
			await fetchItems();
		} catch {
			/* toast shown */
		} finally {
			confirmLoading = false;
		}
	}

	async function exportData() {
		const data = await api.getLogs();
		const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = 'logmaker-log.json';
		a.click();
	}

	async function handleImport(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0];
		if (!file) return;
		loading = true;
		try {
			const fd = new FormData();
			fd.append('file', file);
			const res = await fetch('/api/v1/log:import-file', { method: 'POST', body: fd });
			const result = await res.json();
			if (Array.isArray(result) && result.some((r: { type: string }) => r.type === 'ERROR')) {
				addToast('error', 'Import failed for some entries');
			} else {
				addToast('success', 'Import successful');
				importOpen = false;
			}
			await fetchItems();
		} catch {
			addToast('error', 'Import failed');
		} finally {
			loading = false;
		}
	}

	// Extract maker names from format string
	function extractMakers(format: string): string[] {
		const matches = format.match(/<([^>]+)>/g);
		if (!matches) return [];
		return [...new Set(matches.map((m) => m.slice(1, -1)))];
	}

	// Parse format into segments: [{text, maker?}]
	function parseFormatSegments(format: string): Array<{ text: string; maker?: string }> {
		const segments: Array<{ text: string; maker?: string }> = [];
		const regex = /<([^>]+)>/g;
		let lastIndex = 0;
		let match;
		while ((match = regex.exec(format)) !== null) {
			if (match.index > lastIndex) {
				segments.push({ text: format.slice(lastIndex, match.index) });
			}
			segments.push({ text: `<${match[1]}>`, maker: match[1] });
			lastIndex = regex.lastIndex;
		}
		if (lastIndex < format.length) {
			segments.push({ text: format.slice(lastIndex) });
		}
		return segments;
	}

	function getMakerTitle(name: string): string {
		const mk = makers.find(m => m.name === name);
		return mk ? `${name} — ${mk.type}` : name;
	}

	function getMakerDetail(name: string): string {
		const mk = makers.find(m => m.name === name);
		if (!mk) return `name: ${name}`;
		const lines: string[] = [];
		for (const [k, v] of Object.entries(mk.args || {})) {
			const val = Array.isArray(v) ? v.join(', ') : String(v);
			lines.push(`${k}: ${val}`);
		}
		lines.push(`ref: ${mk.ref}`);
		return lines.join('\n');
	}

	function getSenderTitle(name: string): string {
		const sn = senders.find(s => s.name === name);
		return sn ? `${name} — ${sn.type}` : name;
	}

	function getSenderDetail(name: string): string {
		const sn = senders.find(s => s.name === name);
		if (!sn) return '';
		const entries = Object.entries(sn.args || {}).slice(0, 4);
		const lines = entries.map(([k, v]) => `${k}: ${v}`);
		lines.push(`output: ${(sn.output ?? 0).toLocaleString()}`);
		return lines.join('\n');
	}

	// Map sample output back to format: find which parts of the sample came from which maker
	function mapSampleToFormat(format: string, sample: string): Array<{ text: string; maker?: string }> {
		if (!sample || !format) return [{ text: sample || format || '' }];
		const segments = parseFormatSegments(format);
		// Collect static text pieces to use as delimiters
		const statics: string[] = [];
		const makerSlots: string[] = [];
		for (const seg of segments) {
			if (seg.maker) {
				makerSlots.push(seg.maker);
			} else {
				statics.push(seg.text);
			}
		}
		if (makerSlots.length === 0) return [{ text: sample }];

		// Build regex: escape static parts, capture maker values between them
		let remaining = sample;
		const result: Array<{ text: string; maker?: string }> = [];
		let segIdx = 0;
		for (const seg of segments) {
			if (!seg.maker) {
				// Static text: find it in remaining
				const pos = remaining.indexOf(seg.text);
				if (pos > 0) {
					// Text before this static part is unmatched — shouldn't happen normally
					result.push({ text: remaining.slice(0, pos) });
				}
				if (pos >= 0) {
					result.push({ text: seg.text });
					remaining = remaining.slice(pos + seg.text.length);
				} else {
					// Static not found — just push remaining and bail
					result.push({ text: remaining });
					remaining = '';
					break;
				}
			} else {
				// Maker: find the next static text to know where maker value ends
				const nextStatic = segments.slice(segIdx + 1).find(s => !s.maker);
				if (nextStatic) {
					const endPos = remaining.indexOf(nextStatic.text);
					if (endPos >= 0) {
						result.push({ text: remaining.slice(0, endPos), maker: seg.maker });
						remaining = remaining.slice(endPos);
					} else {
						result.push({ text: remaining, maker: seg.maker });
						remaining = '';
						break;
					}
				} else {
					// Last segment is a maker — rest of string is the value
					result.push({ text: remaining, maker: seg.maker });
					remaining = '';
				}
			}
			segIdx++;
		}
		if (remaining) result.push({ text: remaining });
		return result;
	}

	function epsPct(log: Log): number {
		if (log.eps <= 0) return 0;
		return Math.min(100, Math.round((log.currentEps / log.eps) * 100));
	}

	// Auto-refresh every 5 seconds for live EPS/count
	$effect(() => {
		fetchItems();
		const interval = setInterval(fetchItems, 5000);
		return () => clearInterval(interval);
	});
</script>

<svelte:head><title>Log — LogMaker</title></svelte:head>

<div class="page">
	<header class="page-header">
		<div class="header-left">
			<h1 class="page-title">Log</h1>
			<span class="item-count">{filtered.length} of {items.length}</span>
		</div>
		<div class="header-actions">
			<div class="search-wrap">
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="search-icon"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
				<input class="search-input" type="search" placeholder="Search logs…" bind:value={search} aria-label="Search logs" />
			</div>
			<button class="btn btn-ghost" onclick={fetchItems} disabled={loading}>
				{#if loading}
					<span class="spinner-muted"></span>
				{:else}
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				{/if}
				Reload
			</button>
			<button class="btn btn-ghost" onclick={() => (importOpen = true)} disabled={loading}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
				Import
			</button>
			<button class="btn btn-ghost" onclick={exportData} disabled={loading}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
				Export
			</button>
			<button class="btn btn-primary" onclick={openAdd} disabled={loading}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Log
			</button>
		</div>
	</header>

	{#if loading && items.length === 0}
		<div class="loading-state">
			<span class="spinner-muted"></span>
			<span>Loading logs…</span>
		</div>
	{:else if items.length === 0}
		<div class="empty-state">
			<div class="empty-state-icon">
				<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
			</div>
			<p class="empty-state-title">No logs yet</p>
			<p class="empty-state-desc">Create a log definition to start generating and sending events</p>
			<button class="btn btn-primary" onclick={openAdd}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Log
			</button>
		</div>
	{:else if filtered.length === 0}
		<div class="empty-state">
			<div class="empty-state-icon">
				<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
			</div>
			<p class="empty-state-title">No results for "{search}"</p>
			<p class="empty-state-desc">Try a different search term</p>
		</div>
	{:else}
		<div class="pipeline-grid" role="list" aria-label="Log pipeline list">
			{#each filtered as item}
				{@const running = item.status === true || item.currentEps > 0}
				{@const pct = epsPct(item)}
				{@const makerNames = extractMakers(item.format)}
				<div
					class="pipeline-card"
					class:running
					role="button"
					onclick={() => openEdit(item)}
					onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && openEdit(item)}
					tabindex="0"
					aria-label="Edit log {item.name}"
				>
					<!-- Card header row -->
					<div class="pipeline-header">
						<div class="pipeline-name-block">
							<span class="pipeline-name">{item.name}</span>
							{#if item.description}
								<span class="pipeline-desc">{item.description}</span>
							{/if}
						</div>
						<div class="pipeline-status" class:running>
							<span class="status-dot" class:pulse={running}></span>
							{running ? 'Running' : 'Stopped'}
						</div>
					</div>

					<!-- Makers / Senders inline bar -->
					<div class="pipeline-bar">
						<div class="bar-section">
							<span class="bar-label">Makers</span>
							<div class="bar-chips">
								{#each makerNames as m}
									<Tooltip title={getMakerTitle(m)} text={getMakerDetail(m)} position="bottom">
										<span class="chip chip-maker">{m}</span>
									</Tooltip>
								{/each}
								{#if makerNames.length === 0}
									<span class="chip chip-empty">none</span>
								{/if}
							</div>
						</div>
						<div class="bar-sep" aria-hidden="true"></div>
						<div class="bar-section">
							<span class="bar-label">Senders</span>
							<div class="bar-chips">
								{#each item.sender as s}
									<Tooltip title={getSenderTitle(s)} text={getSenderDetail(s)} position="bottom">
										<span class="chip chip-sender">{s}</span>
									</Tooltip>
								{/each}
								{#if item.sender.length === 0}
									<span class="chip chip-empty">none</span>
								{/if}
							</div>
						</div>
					</div>

					<!-- Log output: sample with hoverable maker-generated parts -->
					<div class="pipeline-body">
						<div class="body-label">Output</div>
						<div class="output-line mono">{#if item.sample}{#each mapSampleToFormat(item.format, item.sample) as seg}{#if seg.maker}<Tooltip title={getMakerTitle(seg.maker)} text={getMakerDetail(seg.maker)} position="top"><span class="out-maker">{seg.text}</span></Tooltip>{:else}<span class="out-static">{seg.text}</span>{/if}{/each}{:else}{#each parseFormatSegments(item.format) as seg}{#if seg.maker}<Tooltip title={getMakerTitle(seg.maker)} text={getMakerDetail(seg.maker)} position="top"><span class="out-maker">{seg.text}</span></Tooltip>{:else}<span class="out-static">{seg.text}</span>{/if}{/each}{/if}</div>
					</div>

					<!-- EPS + Count metrics row -->
					<div class="pipeline-metrics">
						<div class="metric-item">
							<span class="metric-label-sm">EPS</span>
							<div class="metric-eps">
								<span class="metric-actual" class:live={running}>{item.currentEps.toLocaleString()}</span>
								<span class="metric-sep">/</span>
								<span class="metric-target">{item.eps.toLocaleString()}</span>
							</div>
						</div>
						<div class="eps-bar-wrap">
							<div class="eps-bar">
								<div
									class="eps-bar-fill"
									style="width:{pct}%;background:{pct >= 90 ? 'var(--success)' : pct >= 50 ? 'var(--accent)' : running ? 'var(--warning)' : 'var(--border)'}"
								></div>
							</div>
							<span class="eps-pct">{pct}%</span>
						</div>
						<div class="metric-item metric-count">
							<span class="metric-label-sm">Count</span>
							<span class="metric-count-val">{item.count.toLocaleString()}</span>
						</div>
					</div>

					<!-- Card footer actions -->
					<div
						class="pipeline-footer"
						role="group"
						aria-label="Actions"
					>
						<button
							class="btn btn-ghost btn-sm"
							onclick={(e) => { e.stopPropagation(); openCopy(item); }}
							aria-label="Duplicate {item.name}"
						>
							<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
							Duplicate
						</button>
						<button
							class="btn btn-ghost btn-sm"
							onclick={(e) => { e.stopPropagation(); openEdit(item); }}
							aria-label="Edit {item.name}"
						>
							<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
							Edit
						</button>
						<button
							class="btn btn-ghost btn-sm btn-danger-ghost"
							onclick={(e) => { e.stopPropagation(); askDelete(item.name); }}
							aria-label="Delete {item.name}"
						>
							<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
							Delete
						</button>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>

<!-- Add/Edit Dialog -->
{#if dialogOpen}
	<div class="overlay" role="presentation" onclick={closeDialog}>
		<div
			class="dialog wide"
			role="dialog"
			aria-modal="true"
			tabindex="-1"
			onclick={(e) => e.stopPropagation()}
			onkeydown={(e) => e.key === 'Escape' && closeDialog()}
		>
			<div class="dialog-header">
				<h2 class="dialog-title">{editMode ? 'Edit Log' : 'Add Log'}</h2>
				<button class="close-btn" onclick={closeDialog} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<div class="form-cols">
					<div class="form-col">
						<div class="field">
							<label class="field-label" for="log-name">NAME <span class="required">*</span></label>
							<input id="log-name" class="input" type="text" bind:value={formName} disabled={editMode} placeholder="my-log" />
						</div>

						<div class="field">
							<label class="field-label" for="log-format">FORMAT <span class="required">*</span></label>
							<div class="format-editor">
								<div class="format-backdrop mono" aria-hidden="true">{#each parseFormatSegments(formFormat || '') as seg}{#if seg.maker}<span class="hl-maker">&lt;{seg.maker}&gt;</span>{:else}<span class="hl-static">{seg.text}</span>{/if}{/each}{#if !formFormat}<span class="hl-placeholder">&lt;maker1&gt; &lt;maker2&gt; some static text</span>{/if}&#8203;</div>
								<textarea
									id="log-format"
									class="format-input mono"
									bind:value={formFormat}
									bind:this={formatTextarea}
									oninput={runPreview}
									rows="4"
									spellcheck="false"
								></textarea>
							</div>
						</div>

						<!-- Maker helper -->
						<div class="maker-helper">
							<button
								class="helper-toggle"
								type="button"
								onclick={() => (showMakerHelper = !showMakerHelper)}
							>
								<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></svg>
								Maker Palette
								<svg
									width="12" height="12"
									viewBox="0 0 24 24"
									fill="none"
									stroke="currentColor"
									stroke-width="2"
									style="transform: rotate({showMakerHelper ? 90 : 0}deg); transition: transform 0.15s"
								><polyline points="9 18 15 12 9 6"/></svg>
							</button>

							{#if showMakerHelper}
								<div class="helper-panel" transition:slide={{ duration: 150 }}>
									<div class="helper-controls">
										<button class="mode-pill" class:active={makerHelperShowName} onclick={() => (makerHelperShowName = true)}>Name</button>
										<button class="mode-pill" class:active={!makerHelperShowName} onclick={() => (makerHelperShowName = false)}>Sample</button>
									</div>
									<div class="maker-chips">
										{#if makers.length === 0}
											<span class="text-muted" style="font-size:0.8125rem">No makers available</span>
										{:else}
											{#each makers as maker}
												<button
													class="maker-chip"
													onclick={() => insertMaker(maker.name)}
													title={makerHelperShowName ? (maker.sample ?? maker.name) : maker.name}
												>
													{makerHelperShowName ? maker.name : (maker.sample ?? maker.name)}
												</button>
											{/each}
										{/if}
									</div>
								</div>
							{/if}
						</div>

						<!-- Preview -->
						<div class="field">
							<span class="field-label">
								PREVIEW
								{#if previewLoading}
									<span class="preview-spinner"></span>
								{/if}
							</span>
							<div class="preview-box mono">{#if previewText}{#each mapSampleToFormat(formFormat, previewText) as seg}{#if seg.maker}<span class="hl-maker">{seg.text}</span>{:else}<span class="hl-static">{seg.text}</span>{/if}{/each}{:else}<span class="hl-placeholder">Type a format above to see preview…</span>{/if}</div>
						</div>
					</div>

					<div class="form-col">
						<div class="field">
							<label class="field-label" for="log-eps">EVENTS / SECOND <span class="required">*</span></label>
							<input id="log-eps" class="input" type="number" bind:value={formEps} min="0" />
						</div>

						<div class="field">
							<span class="field-label">SENDERS <span class="required">*</span></span>
							<div class="sender-list">
								{#if senders.length === 0}
									<p class="text-muted" style="font-size:0.8125rem;margin:0">No senders available</p>
								{:else}
									{#each senders as s}
										<label class="sender-option" class:selected={formSenders.includes(s.name)}>
											<input
												type="checkbox"
												class="sr-only"
												checked={formSenders.includes(s.name)}
												onchange={() => toggleSender(s.name)}
											/>
											<span class="sender-check">
												{#if formSenders.includes(s.name)}
													<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
												{/if}
											</span>
											<span>{s.name}</span>
											<span class="sender-type">{s.type}</span>
										</label>
									{/each}
								{/if}
							</div>
						</div>
					</div>
				</div>
			</div>
			<div class="dialog-footer">
				<button class="btn btn-ghost" onclick={closeDialog} disabled={loading}>Cancel</button>
				<button class="btn btn-primary" onclick={submit} disabled={loading}>
					{#if loading}<span class="spinner"></span>{/if}
					{editMode ? 'Save Changes' : 'Add Log'}
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- Import Dialog -->
{#if importOpen}
	<div class="overlay" role="presentation" onclick={() => (importOpen = false)}>
		<div
			class="dialog narrow"
			role="dialog"
			aria-modal="true"
			tabindex="-1"
			onclick={(e) => e.stopPropagation()}
			onkeydown={(e) => e.key === 'Escape' && (importOpen = false)}
		>
			<div class="dialog-header">
				<h2 class="dialog-title">Import Logs</h2>
				<button class="close-btn" onclick={() => (importOpen = false)} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<label class="upload-zone">
					<input type="file" accept=".json" class="sr-only" onchange={handleImport} />
					<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.5"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
					<span class="upload-label">Drop JSON file here or click to browse</span>
				</label>
			</div>
			<div class="dialog-footer">
				<button class="btn btn-ghost" onclick={() => (importOpen = false)}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<ConfirmDialog
	open={confirmOpen}
	title="Delete Log"
	message="Delete '{confirmName}'? This cannot be undone."
	loading={confirmLoading}
	onconfirm={confirmDelete}
	oncancel={() => (confirmOpen = false)}
/>

<style>
	.header-left {
		display: flex;
		align-items: baseline;
		gap: 0.75rem;
	}

	.item-count {
		font-size: 0.8125rem;
		color: var(--text-muted);
	}

	.search-wrap {
		position: relative;
		display: flex;
		align-items: center;
	}

	.search-icon {
		position: absolute;
		left: 0.5rem;
		color: var(--text-muted);
		pointer-events: none;
	}

	.search-input {
		padding: 0.375rem 0.75rem 0.375rem 1.875rem;
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-primary);
		font-size: 0.8125rem;
		font-family: var(--font-ui);
		width: 192px;
		transition: border-color 0.15s, width 0.2s;
	}

	.search-input::placeholder { color: var(--text-muted); }

	.search-input:focus {
		outline: none;
		border-color: var(--border-focus);
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 18%, transparent);
		width: 240px;
	}

	.loading-state {
		display: flex;
		align-items: center;
		gap: 0.625rem;
		padding: 2rem;
		color: var(--text-muted);
		font-size: 0.875rem;
	}

	/* ── Pipeline grid ── */
	.pipeline-grid {
		display: grid;
		grid-template-columns: 1fr;
		gap: 0.75rem;
	}

	/* ── Pipeline card ── */
	.pipeline-card {
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		cursor: pointer;
		transition: border-color 0.15s;
		display: flex;
		flex-direction: column;
	}

	.pipeline-card:hover {
		border-color: var(--accent);
	}

	.pipeline-card.running {
		border-left: 2px solid var(--accent);
	}

	.pipeline-card:focus {
		outline: none;
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 25%, transparent);
	}

	/* Card header */
	.pipeline-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0.75rem 0.875rem;
		border-bottom: 1px solid var(--border);
		gap: 1rem;
	}

	.pipeline-name-block {
		display: flex;
		flex-direction: column;
		gap: 0.125rem;
		min-width: 0;
	}

	.pipeline-name {
		font-size: 0.9375rem;
		font-weight: 600;
		color: var(--text-primary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.pipeline-desc {
		font-size: 0.6875rem;
		color: var(--text-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.pipeline-status {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		padding: 0.2rem 0.5rem;
		border-radius: 4px;
		font-size: 0.6875rem;
		font-weight: 600;
		white-space: nowrap;
		background: var(--bg-raised);
		color: var(--text-muted);
		border: 1px solid var(--border);
		flex-shrink: 0;
		letter-spacing: 0.02em;
	}

	.pipeline-status.running {
		background: var(--success-light);
		color: var(--success);
		border-color: color-mix(in srgb, var(--success) 25%, transparent);
	}

	.status-dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
		flex-shrink: 0;
	}

	.status-dot.pulse {
		animation: pulse-ring 2s ease-out infinite;
	}

	/* Makers/Senders bar */
	.pipeline-bar {
		display: flex;
		align-items: center;
		padding: 0.5rem 0.875rem;
		gap: 0.5rem;
		border-bottom: 1px solid var(--border);
		background: var(--bg-raised);
	}

	.bar-section {
		display: flex;
		align-items: center;
		gap: 0.375rem;
		min-width: 0;
		flex-shrink: 0;
	}

	.bar-label {
		font-size: 0.5625rem;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--text-muted);
		white-space: nowrap;
		flex-shrink: 0;
	}

	.bar-chips {
		display: flex;
		align-items: center;
		gap: 0.25rem;
		flex-wrap: wrap;
	}

	.chip {
		display: inline-block;
		padding: 0.1rem 0.4375rem;
		border-radius: 4px;
		font-size: 0.625rem;
		font-weight: 500;
		white-space: nowrap;
		font-family: var(--font-mono);
	}

	.chip-maker {
		background: var(--accent-light);
		color: var(--accent);
		border: 1px solid color-mix(in srgb, var(--accent) 20%, transparent);
	}

	.chip-sender {
		background: var(--bg-surface);
		color: var(--text-secondary);
		border: 1px solid var(--border);
	}

	.chip-empty {
		color: var(--text-muted);
		border: 1px dashed var(--border);
		background: transparent;
	}

	.bar-sep {
		width: 1px;
		height: 16px;
		background: var(--border);
		flex-shrink: 0;
		margin: 0 0.125rem;
	}

	/* Pipeline body */
	.pipeline-body {
		padding: 0.625rem 0.875rem;
		border-bottom: 1px solid var(--border);
	}

	.body-label {
		font-size: 0.5625rem;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--text-muted);
		margin-bottom: 0.25rem;
	}

	.output-line {
		font-size: 0.75rem;
		line-height: 1.6;
		color: var(--text-primary);
		background: var(--bg-base);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 0.5rem 0.625rem;
		white-space: pre-wrap;
		word-break: break-all;
		font-family: var(--font-mono);
	}

	.out-static {
		color: var(--text-secondary);
	}

	.out-maker {
		color: var(--accent);
		font-weight: 600;
		border-bottom: 1px dotted color-mix(in srgb, var(--accent) 50%, transparent);
		cursor: help;
		transition: background 0.12s;
		border-radius: 2px;
		padding: 0 0.1rem;
	}

	.out-maker:hover {
		background: var(--accent-light);
		border-bottom-color: var(--accent);
	}

	/* Metrics row */
	.pipeline-metrics {
		display: flex;
		align-items: center;
		gap: 0.875rem;
		padding: 0.5rem 0.875rem;
		border-bottom: 1px solid var(--border);
	}

	.metric-item {
		display: flex;
		align-items: baseline;
		gap: 0.3rem;
		flex-shrink: 0;
	}

	.metric-count {
		margin-left: auto;
	}

	.metric-label-sm {
		font-size: 0.5625rem;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		color: var(--text-muted);
	}

	.metric-eps {
		display: flex;
		align-items: baseline;
		gap: 0.2rem;
	}

	.metric-actual {
		font-size: 0.9375rem;
		font-weight: 700;
		letter-spacing: -0.03em;
		color: var(--text-primary);
		font-family: var(--font-mono);
	}

	.metric-actual.live {
		color: var(--accent);
	}

	.metric-sep {
		color: var(--text-muted);
		font-size: 0.75rem;
	}

	.metric-target {
		font-size: 0.8125rem;
		color: var(--text-secondary);
		font-weight: 500;
		font-family: var(--font-mono);
	}

	.eps-bar-wrap {
		flex: 1;
		display: flex;
		align-items: center;
		gap: 0.375rem;
	}

	.eps-bar {
		flex: 1;
		height: 3px;
		background: var(--bg-raised);
		border-radius: 2px;
		overflow: hidden;
	}

	.eps-bar-fill {
		height: 100%;
		background: var(--accent);
		border-radius: 2px;
		transition: width 0.5s ease;
	}

	.eps-pct {
		font-size: 0.625rem;
		font-weight: 600;
		color: var(--text-muted);
		width: 2.25rem;
		text-align: right;
		font-family: var(--font-mono);
	}

	.metric-count-val {
		font-size: 0.875rem;
		font-weight: 700;
		letter-spacing: -0.03em;
		color: var(--text-primary);
		font-family: var(--font-mono);
	}

	/* Card footer */
	.pipeline-footer {
		display: flex;
		align-items: center;
		gap: 0.375rem;
		padding: 0.5rem 0.625rem;
		background: var(--bg-raised);
		border-radius: 0 0 var(--radius-md) var(--radius-md);
	}

	.btn-danger-ghost:hover:not(:disabled) {
		color: var(--danger);
		background: var(--danger-light);
		border-color: color-mix(in srgb, var(--danger) 25%, transparent);
	}

	/* Dialog form */
	.form-cols {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1.5rem;
	}

	@media (max-width: 600px) {
		.form-cols { grid-template-columns: 1fr; }
	}

	/* Format editor: transparent textarea over highlighted backdrop */
	.format-editor {
		position: relative;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-base);
		transition: border-color 0.15s, box-shadow 0.15s;
	}

	.format-editor:focus-within {
		border-color: var(--border-focus);
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 18%, transparent);
	}

	.format-backdrop {
		padding: 0.5rem 0.75rem;
		font-size: 0.8125rem;
		line-height: 1.6;
		white-space: pre-wrap;
		word-break: break-all;
		min-height: 80px;
		pointer-events: none;
		color: transparent;
	}

	.format-input {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		padding: 0.5rem 0.75rem;
		font-family: var(--font-mono);
		font-size: 0.8125rem;
		line-height: 1.6;
		background: transparent;
		border: none;
		color: transparent;
		caret-color: var(--text-primary);
		resize: none;
		overflow: hidden;
	}

	.format-input:focus {
		outline: none;
	}

	/* Syntax highlighting */
	.hl-maker {
		color: var(--accent);
		font-weight: 600;
		background: var(--accent-light);
		border-radius: 2px;
		padding: 0 1px;
	}

	.hl-static {
		color: var(--text-secondary);
	}

	.hl-placeholder {
		color: var(--text-muted);
	}

	.maker-helper { margin-bottom: 1rem; }

	.helper-toggle {
		display: flex;
		align-items: center;
		gap: 0.375rem;
		background: none;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 0.375rem 0.75rem;
		font-size: 0.8125rem;
		font-family: var(--font-ui);
		color: var(--text-secondary);
		cursor: pointer;
		transition: background 0.12s, color 0.12s;
	}

	.helper-toggle:hover {
		background: var(--bg-raised);
		color: var(--text-primary);
	}

	.helper-panel {
		margin-top: 0.5rem;
		padding: 0.75rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
	}

	.helper-controls {
		display: flex;
		gap: 0.375rem;
		margin-bottom: 0.625rem;
	}

	.mode-pill {
		padding: 0.2rem 0.5rem;
		border-radius: 4px;
		font-size: 0.6875rem;
		font-weight: 600;
		border: 1px solid var(--border);
		background: none;
		color: var(--text-muted);
		cursor: pointer;
		transition: background 0.12s, color 0.12s;
	}

	.mode-pill.active {
		background: var(--accent);
		border-color: var(--accent);
		color: #000;
	}

	.maker-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 0.3rem;
	}

	.maker-chip {
		padding: 0.2rem 0.5rem;
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: 4px;
		font-size: 0.75rem;
		font-family: var(--font-mono);
		cursor: pointer;
		color: var(--text-secondary);
		transition: background 0.12s, border-color 0.12s, color 0.12s;
	}

	.maker-chip:hover {
		background: var(--accent-light);
		border-color: color-mix(in srgb, var(--accent) 40%, transparent);
		color: var(--accent);
	}

	.preview-box {
		margin: 0;
		padding: 0.5rem 0.75rem;
		background: var(--bg-base);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-family: var(--font-mono);
		font-size: 0.8125rem;
		white-space: pre-wrap;
		word-break: break-all;
		min-height: 44px;
		line-height: 1.6;
	}

	.preview-spinner {
		display: inline-block;
		width: 10px;
		height: 10px;
		border: 2px solid var(--border);
		border-top-color: var(--accent);
		border-radius: 50%;
		animation: spin 0.6s linear infinite;
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}

	.sender-list {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
		max-height: 200px;
		overflow-y: auto;
		padding: 0.25rem;
		background: var(--bg-base);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
	}

	.sender-option {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.4375rem 0.5rem;
		border-radius: var(--radius-sm);
		cursor: pointer;
		font-size: 0.8125rem;
		transition: background 0.12s;
	}

	.sender-option:hover { background: var(--bg-raised); }
	.sender-option.selected { background: var(--accent-light); }

	.sender-check {
		width: 14px;
		height: 14px;
		border: 1px solid var(--border);
		border-radius: 3px;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		color: var(--accent);
	}

	.sender-option.selected .sender-check {
		background: var(--accent);
		border-color: var(--accent);
		color: #000;
	}

	.sender-type {
		margin-left: auto;
		font-size: 0.6875rem;
		color: var(--text-muted);
		font-family: var(--font-mono);
	}

	@media (max-width: 700px) {
		.pipeline-grid { grid-template-columns: 1fr; }
		.pipeline-bar { flex-wrap: wrap; gap: 0.375rem; }
		.bar-sep { display: none; }
		.search-input { width: 150px; }
		.search-input:focus { width: 150px; }
	}
</style>
