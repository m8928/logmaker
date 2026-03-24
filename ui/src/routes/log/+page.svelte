<script lang="ts">
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
	let expandedOutputs = $state(new Set<string>());
	let viewMode = $state<'grid' | 'table'>('grid');

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

	let formatTextarea = $state<HTMLDivElement | null>(null);

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
		setTimeout(() => {
			if (formatTextarea) formatTextarea.textContent = item.format;
			highlightFormat();
		}, 10);
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
			formatTextarea.focus();
			document.execCommand('insertText', false, token);
			formFormat = formatTextarea.textContent ?? '';
		}
		runPreview();
	}

	// Highlight <makerName> in contenteditable
	function highlightFormat() {
		if (!formatTextarea || !formFormat) return;
		// Save cursor position
		const sel = window.getSelection();
		let cursorOffset = 0;
		if (sel && sel.rangeCount > 0) {
			const range = sel.getRangeAt(0);
			const preRange = document.createRange();
			preRange.selectNodeContents(formatTextarea);
			preRange.setEnd(range.startContainer, range.startOffset);
			cursorOffset = preRange.toString().length;
		}
		// Build highlighted HTML
		const segments = parseFormatSegments(formFormat);
		let html = '';
		for (const seg of segments) {
			if (seg.maker) {
				html += `<span class="hl-maker">${seg.text.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</span>`;
			} else {
				html += seg.text.replace(/</g, '&lt;').replace(/>/g, '&gt;');
			}
		}
		formatTextarea.innerHTML = html;
		// Restore cursor
		try {
			const newSel = window.getSelection();
			if (newSel) {
				const newRange = document.createRange();
				const node = formatTextarea;
				let offset = 0;
				const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
				let textNode: Node | null = null;
				while ((textNode = walker.nextNode())) {
					const len = (textNode.textContent ?? '').length;
					if (offset + len >= cursorOffset) {
						newRange.setStart(textNode, cursorOffset - offset);
						newRange.collapse(true);
						newSel.removeAllRanges();
						newSel.addRange(newRange);
						break;
					}
					offset += len;
				}
			}
		} catch { /* ignore cursor restore errors */ }
	}

	$effect(() => {
		if (formFormat !== undefined) {
			// Small delay to not interfere with typing
			const id = setTimeout(highlightFormat, 50);
			return () => clearTimeout(id);
		}
	});

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
		const makerSlots: string[] = [];
		for (const seg of segments) {
			if (seg.maker) {
				makerSlots.push(seg.maker);
			}
		}
		if (makerSlots.length === 0) return [{ text: sample }];

		let remaining = sample;
		const result: Array<{ text: string; maker?: string }> = [];
		let segIdx = 0;
		for (const seg of segments) {
			if (!seg.maker) {
				const pos = remaining.indexOf(seg.text);
				if (pos > 0) {
					result.push({ text: remaining.slice(0, pos) });
				}
				if (pos >= 0) {
					result.push({ text: seg.text });
					remaining = remaining.slice(pos + seg.text.length);
				} else {
					result.push({ text: remaining });
					remaining = '';
					break;
				}
			} else {
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

	// Truncate format string for table display, keep <maker> parts visually intact
	function truncateFormat(format: string, maxLen = 52): string {
		if (format.length <= maxLen) return format;
		return format.slice(0, maxLen) + '…';
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
			<span class="page-hint">Log pipelines — combine makers into a format template, set EPS, and send to destinations.</span>
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
			<div class="view-toggle" role="radiogroup" aria-label="View mode">
				<button
					class="toggle-btn"
					class:active={viewMode === 'grid'}
					onclick={() => (viewMode = 'grid')}
					aria-label="Grid view"
					aria-pressed={viewMode === 'grid'}
				>
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
				</button>
				<button
					class="toggle-btn"
					class:active={viewMode === 'table'}
					onclick={() => (viewMode = 'table')}
					aria-label="Table view"
					aria-pressed={viewMode === 'table'}
				>
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
				</button>
			</div>
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
	{:else if viewMode === 'grid'}
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
						<div class="output-line mono" class:collapsed={(item.sample || item.format).length > 120 && !expandedOutputs.has(item.name)}>{#if item.sample}{#each mapSampleToFormat(item.format, item.sample) as seg}{#if seg.maker}<Tooltip title={getMakerTitle(seg.maker)} text={getMakerDetail(seg.maker)} position="top"><span class="out-maker">{seg.text}</span></Tooltip>{:else}<span class="out-static">{seg.text}</span>{/if}{/each}{:else}{#each parseFormatSegments(item.format) as seg}{#if seg.maker}<Tooltip title={getMakerTitle(seg.maker)} text={getMakerDetail(seg.maker)} position="top"><span class="out-maker">{seg.text}</span></Tooltip>{:else}<span class="out-static">{seg.text}</span>{/if}{/each}{/if}</div>
						{#if (item.sample || item.format).length > 120}
							<button
								class="output-toggle"
								onclick={(e) => { e.stopPropagation(); const s = new Set(expandedOutputs); if (s.has(item.name)) s.delete(item.name); else s.add(item.name); expandedOutputs = s; }}
							>
								{expandedOutputs.has(item.name) ? '▲ Less' : '▼ More'}
							</button>
						{/if}
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
	{:else}
		<!-- Table view -->
		<div class="table-wrap">
			<table class="table" aria-label="Log list">
				<thead>
					<tr>
						<th>Name</th>
						<th>Status</th>
						<th>Format</th>
						<th class="right">EPS</th>
						<th class="right">Count</th>
						<th>Senders</th>
						<th class="right">Actions</th>
					</tr>
				</thead>
				<tbody>
					{#each filtered as item}
						{@const running = item.status === true || item.currentEps > 0}
						{@const pct = epsPct(item)}
						{@const formatSegs = parseFormatSegments(truncateFormat(item.format))}
						<tr
							class="table-row-clickable"
							onclick={() => openEdit(item)}
							onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && openEdit(item)}
							tabindex="0"
							role="button"
							aria-label="Edit log {item.name}"
						>
							<td>
								<span class="tbl-name mono">{item.name}</span>
							</td>
							<td>
								<span class="tbl-status" class:tbl-status-running={running}>
									<span class="tbl-status-dot" class:pulse={running}></span>
									{running ? 'Run' : 'Stop'}
								</span>
							</td>
							<td class="tbl-format-cell">
								<span class="tbl-format mono">{#each formatSegs as seg}{#if seg.maker}<span class="tbl-format-maker">{seg.text}</span>{:else}<span class="tbl-format-static">{seg.text}</span>{/if}{/each}</span>
							</td>
							<td class="right">
								<span class="tbl-eps">
									<span class="tbl-eps-actual" class:live={running}>{item.currentEps.toLocaleString()}</span>
									<span class="tbl-eps-sep">/</span>
									<span class="tbl-eps-target">{item.eps.toLocaleString()}</span>
								</span>
							</td>
							<td class="right">
								<span class="tbl-count mono">{item.count.toLocaleString()}</span>
							</td>
							<td>
								<span class="tbl-senders">
									{#if item.sender.length > 0}
										{item.sender.slice(0, 2).join(', ')}{item.sender.length > 2 ? ` +${item.sender.length - 2}` : ''}
									{:else}
										<span class="tbl-empty">—</span>
									{/if}
								</span>
							</td>
							<td class="right">
								<div class="row-actions">
									<button
										class="icon-btn"
										onclick={(e) => { e.stopPropagation(); openCopy(item); }}
										title="Duplicate"
										aria-label="Duplicate {item.name}"
									>
										<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
									</button>
									<button
										class="icon-btn"
										onclick={(e) => { e.stopPropagation(); openEdit(item); }}
										title="Edit"
										aria-label="Edit {item.name}"
									>
										<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
									</button>
									<button
										class="icon-btn danger"
										onclick={(e) => { e.stopPropagation(); askDelete(item.name); }}
										title="Delete"
										aria-label="Delete {item.name}"
									>
										<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
									</button>
								</div>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
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
				<!-- Section 1: Basic Info -->
				<div class="pipeline-section">
					<div class="basic-info-row">
						<div class="field field-name">
							<label class="field-label" for="log-name">NAME <span class="required">*</span></label>
							<input id="log-name" class="input" type="text" bind:value={formName} disabled={editMode} placeholder="my-log" />
						</div>
						<div class="field field-eps">
							<label class="field-label" for="log-eps">EPS <span class="required">*</span></label>
							<input id="log-eps" class="input" type="number" bind:value={formEps} min="0" placeholder="1000" />
						</div>
					</div>
				</div>

				<!-- Section 2: Maker Palette (always visible) -->
				<div class="pipeline-section">
					<div class="section-header">
						<span class="field-label">AVAILABLE MAKERS</span>
					</div>
					<div class="maker-palette">
						{#if makers.length === 0}
							<span class="palette-empty">No makers available — create one first</span>
						{:else}
							{#each makers as maker}
								<button
									class="palette-chip"
									type="button"
									onclick={() => insertMaker(maker.name)}
									title="Insert &lt;{maker.name}&gt;"
								>
									<span class="palette-chip-name">{maker.name}</span>
									{#if maker.type}
										<span class="palette-chip-type">{maker.type}</span>
									{/if}
								</button>
							{/each}
						{/if}
					</div>
				</div>

				<!-- Section 3: Format Editor -->
				<div class="pipeline-section">
					<div class="section-header">
						<label class="field-label" for="log-format">FORMAT TEMPLATE <span class="required">*</span></label>
					</div>
					<div
						id="log-format"
						class="format-editable mono"
						contenteditable="true"
						spellcheck="false"
						bind:this={formatTextarea}
						oninput={(e) => {
							formFormat = e.currentTarget.textContent ?? '';
							runPreview();
						}}
						onpaste={(e) => {
							e.preventDefault();
							const text = e.clipboardData?.getData('text/plain') ?? '';
							document.execCommand('insertText', false, text);
						}}
						role="textbox"
						aria-multiline="true"
						data-placeholder="<maker1> <maker2> some static text"
					></div>
				</div>

				<!-- Section 4: Live Preview -->
				<div class="pipeline-section preview-section">
					<div class="section-header">
						<span class="field-label">
							LIVE PREVIEW
							{#if previewLoading}
								<span class="preview-spinner"></span>
							{/if}
						</span>
					</div>
					<div class="preview-box mono">{#if previewText}{#each mapSampleToFormat(formFormat, previewText) as seg}{#if seg.maker}<span class="hl-maker">{seg.text}</span>{:else}<span class="hl-static">{seg.text}</span>{/if}{/each}{:else}<span class="hl-placeholder">Output will appear here…</span>{/if}</div>
				</div>

				<!-- Section 5: Senders -->
				<div class="pipeline-section last-section">
					<div class="section-header">
						<span class="field-label">SEND TO <span class="required">*</span></span>
					</div>
					<div class="sender-chips">
						{#if senders.length === 0}
							<span class="palette-empty">No senders available</span>
						{:else}
							{#each senders as s}
								<label class="sender-chip" class:selected={formSenders.includes(s.name)}>
									<input
										type="checkbox"
										class="sr-only"
										checked={formSenders.includes(s.name)}
										onchange={() => toggleSender(s.name)}
									/>
									<span class="sender-chip-check" aria-hidden="true">
										{#if formSenders.includes(s.name)}
											<svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5"><polyline points="20 6 9 17 4 12"/></svg>
										{/if}
									</span>
									<span class="sender-chip-name">{s.name}</span>
									<span class="sender-chip-type">{s.type}</span>
								</label>
							{/each}
						{/if}
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
		transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
		display: flex;
		flex-direction: column;
	}

	.pipeline-card:hover {
		border-color: var(--accent);
		background: color-mix(in srgb, var(--bg-surface) 96%, var(--accent));
	}

	.pipeline-card.running {
		border-left: 2px solid var(--accent);
	}

	.pipeline-card:focus {
		outline: none;
		border-color: var(--accent);
		background: color-mix(in srgb, var(--bg-surface) 96%, var(--accent));
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
		transition: max-height 0.2s ease;
	}

	.output-line.collapsed {
		max-height: 2.8em;
		overflow: hidden;
		position: relative;
	}

	.output-line.collapsed::after {
		content: '';
		position: absolute;
		bottom: 0;
		left: 0;
		right: 0;
		height: 1.4em;
		background: linear-gradient(transparent, var(--bg-base));
		pointer-events: none;
	}

	.output-toggle {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		margin-top: 0.25rem;
		padding: 0.125rem 0.5rem;
		background: none;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-muted);
		font-size: 0.625rem;
		font-family: var(--font-ui);
		cursor: pointer;
		transition: color 0.12s, border-color 0.12s;
	}

	.output-toggle:hover {
		color: var(--accent);
		border-color: var(--accent);
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

	/* ── Table view ── */
	.table-row-clickable {
		cursor: pointer;
	}

	.table-row-clickable:focus {
		outline: none;
	}

	.table-row-clickable:focus td {
		background: color-mix(in srgb, var(--accent) 6%, transparent);
	}

	.tbl-name {
		font-weight: 600;
		color: var(--text-primary);
		font-size: 0.8125rem;
	}

	.tbl-status {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		padding: 0.175rem 0.4375rem;
		border-radius: 4px;
		font-size: 0.6875rem;
		font-weight: 600;
		background: var(--bg-raised);
		color: var(--text-muted);
		border: 1px solid var(--border);
		white-space: nowrap;
	}

	.tbl-status.tbl-status-running {
		background: var(--success-light);
		color: var(--success);
		border-color: color-mix(in srgb, var(--success) 25%, transparent);
	}

	.tbl-status-dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
		flex-shrink: 0;
	}

	.tbl-status-dot.pulse {
		animation: pulse-ring 2s ease-out infinite;
	}

	.tbl-format-cell {
		max-width: 280px;
	}

	.tbl-format {
		font-size: 0.75rem;
		white-space: nowrap;
		overflow: hidden;
		display: inline-block;
		max-width: 100%;
		text-overflow: ellipsis;
		vertical-align: middle;
	}

	.tbl-format-maker {
		color: var(--accent);
		font-weight: 600;
	}

	.tbl-format-static {
		color: var(--text-secondary);
	}

	.tbl-eps {
		display: inline-flex;
		align-items: baseline;
		gap: 0.2rem;
		font-family: var(--font-mono);
	}

	.tbl-eps-actual {
		font-size: 0.875rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	.tbl-eps-actual.live {
		color: var(--accent);
	}

	.tbl-eps-sep {
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	.tbl-eps-target {
		font-size: 0.75rem;
		color: var(--text-muted);
		font-weight: 500;
	}

	.tbl-count {
		font-size: 0.875rem;
		font-weight: 600;
		color: var(--text-primary);
	}

	.tbl-senders {
		font-size: 0.75rem;
		color: var(--text-secondary);
		font-family: var(--font-mono);
		white-space: nowrap;
	}

	.tbl-empty {
		color: var(--text-muted);
		font-size: 0.75rem;
	}

	/* ── Pipeline Builder Dialog ── */

	.pipeline-section {
		padding: 0.875rem 0;
		border-bottom: 1px solid var(--border);
	}

	.pipeline-section.last-section {
		border-bottom: none;
		padding-bottom: 0;
	}

	.pipeline-section.preview-section {
		padding-top: 0.625rem;
	}

	.section-header {
		margin-bottom: 0.5rem;
	}

	.basic-info-row {
		display: flex;
		gap: 0.75rem;
		align-items: flex-end;
	}

	.field-name {
		flex: 1;
		margin-bottom: 0;
	}

	.field-eps {
		width: 120px;
		flex-shrink: 0;
		margin-bottom: 0;
	}

	.maker-palette {
		display: flex;
		flex-wrap: wrap;
		gap: 0.375rem;
		padding: 0.625rem 0.75rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		min-height: 40px;
		align-items: center;
	}

	.palette-empty {
		font-size: 0.8125rem;
		color: var(--text-muted);
		font-style: italic;
	}

	.palette-chip {
		display: inline-flex;
		flex-direction: column;
		align-items: center;
		gap: 0.0625rem;
		padding: 0.25rem 0.625rem;
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: 5px;
		cursor: pointer;
		transition: background 0.12s, border-color 0.12s, color 0.12s;
		line-height: 1.2;
	}

	.palette-chip:hover {
		background: var(--accent-light);
		border-color: color-mix(in srgb, var(--accent) 45%, transparent);
	}

	.palette-chip-name {
		font-size: 0.75rem;
		font-family: var(--font-mono);
		font-weight: 600;
		color: var(--text-secondary);
		transition: color 0.12s;
	}

	.palette-chip:hover .palette-chip-name {
		color: var(--accent);
	}

	.palette-chip-type {
		font-size: 0.5625rem;
		font-family: var(--font-ui);
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.04em;
		font-weight: 500;
	}

	.format-editable {
		padding: 0.625rem 0.75rem;
		background: var(--bg-base);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-size: 0.8125rem;
		line-height: 1.7;
		min-height: 100px;
		color: var(--text-secondary);
		white-space: pre-wrap;
		word-break: break-all;
		outline: none;
		transition: border-color 0.15s, box-shadow 0.15s;
		cursor: text;
	}

	.format-editable:focus {
		border-color: var(--border-focus);
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 18%, transparent);
	}

	.format-editable:empty::before {
		content: attr(data-placeholder);
		color: var(--text-muted);
		pointer-events: none;
	}

	.format-editable :global(.hl-maker),
	.hl-maker {
		color: var(--accent);
		font-weight: 600;
		background: var(--accent-light);
		border-radius: 2px;
		padding: 0 2px;
	}

	.hl-static {
		color: var(--text-secondary);
	}

	.hl-placeholder {
		color: var(--text-muted);
	}

	.preview-box {
		margin: 0;
		padding: 0.625rem 0.75rem;
		background: var(--bg-base);
		border: 1px solid var(--border);
		border-top: none;
		border-radius: 0 0 var(--radius-sm) var(--radius-sm);
		font-family: var(--font-mono);
		font-size: 0.8125rem;
		white-space: pre-wrap;
		word-break: break-all;
		min-height: 44px;
		line-height: 1.7;
		position: relative;
	}

	.preview-box::before {
		content: '';
		position: absolute;
		top: 0;
		left: 0.75rem;
		right: 0.75rem;
		height: 1px;
		background: color-mix(in srgb, var(--accent) 22%, transparent);
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

	.sender-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 0.375rem;
	}

	.sender-chip {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		padding: 0.3125rem 0.625rem 0.3125rem 0.5rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		cursor: pointer;
		font-size: 0.8125rem;
		transition: background 0.12s, border-color 0.12s;
		user-select: none;
	}

	.sender-chip:hover {
		background: var(--bg-surface);
		border-color: var(--text-muted);
	}

	.sender-chip.selected {
		background: var(--accent-light);
		border-color: color-mix(in srgb, var(--accent) 35%, transparent);
	}

	.sender-chip-check {
		width: 14px;
		height: 14px;
		border: 1px solid var(--border);
		border-radius: 3px;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		color: var(--accent);
		background: var(--bg-base);
		transition: background 0.12s, border-color 0.12s;
	}

	.sender-chip.selected .sender-chip-check {
		background: var(--accent);
		border-color: var(--accent);
		color: #000;
	}

	.sender-chip-name {
		font-weight: 500;
		color: var(--text-primary);
	}

	.sender-chip.selected .sender-chip-name {
		color: var(--accent);
	}

	.sender-chip-type {
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
		.tbl-format-cell { max-width: 140px; }
	}
</style>
