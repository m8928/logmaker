<script lang="ts">
	import { api } from '$lib/api';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import { addToast } from '$lib/stores/toast.svelte';
	import type { Log, Maker, Sender } from '$lib/types';

	let items = $state<Log[]>([]);
	let senders = $state<Sender[]>([]);
	let makers = $state<Maker[]>([]);
	let loading = $state(false);
	let expandedRows = $state<Set<string>>(new Set());

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
	let showMakerHelper = $state(false);
	let makerHelperShowName = $state(true);

	// Format textarea ref for cursor position insertion
	let formatTextarea = $state<HTMLTextAreaElement | null>(null);

	async function fetchItems() {
		loading = true;
		try { items = await api.getLogs(); }
		catch { /* toast shown */ }
		finally { loading = false; }
	}

	async function fetchSupport() {
		try {
			[senders, makers] = await Promise.all([api.getSenders(), api.getMakers()]);
		} catch { /* ignored */ }
	}

	function toggleExpand(name: string) {
		const next = new Set(expandedRows);
		if (next.has(name)) next.delete(name);
		else next.add(name);
		expandedRows = next;
	}

	function openAdd() {
		editMode = false;
		formName = ''; formFormat = ''; formEps = 0; formSenders = []; previewText = '';
		dialogOpen = true;
		fetchSupport();
	}

	function openEdit(item: Log) {
		editMode = true;
		formName = item.name; formFormat = item.format; formEps = item.eps;
		formSenders = [...item.sender]; previewText = '';
		dialogOpen = true;
		fetchSupport();
		runPreview();
	}

	function openCopy(item: Log) {
		editMode = false;
		formName = 'copy-of-' + item.name; formFormat = item.format;
		formEps = item.eps; formSenders = [...item.sender]; previewText = '';
		dialogOpen = true;
		fetchSupport();
	}

	function closeDialog() { dialogOpen = false; previewText = ''; }

	async function runPreview() {
		if (!formFormat) return;
		previewLoading = true;
		try {
			const result = await api.previewLog({ name: formName, format: formFormat, eps: formEps, sender: formSenders });
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
			// restore cursor after insertion
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
		} catch { /* toast shown */ }
		finally { loading = false; }
	}

	function askDelete(name: string) { confirmName = name; confirmOpen = true; }

	async function confirmDelete() {
		confirmLoading = true;
		try { await api.deleteLog(confirmName); confirmOpen = false; await fetchItems(); }
		catch { /* toast shown */ }
		finally { confirmLoading = false; }
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
		} catch { addToast('error', 'Import failed'); }
		finally { loading = false; }
	}

	$effect(() => { fetchItems(); });
</script>

<svelte:head><title>Log — LogMaker</title></svelte:head>

<div class="page">
	<header class="page-header">
		<h1 class="page-title">Log</h1>
		<div class="actions">
			<button class="btn btn-ghost" onclick={fetchItems} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				Reload
			</button>
			<button class="btn btn-ghost" onclick={() => (importOpen = true)} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
				Import
			</button>
			<button class="btn btn-ghost" onclick={exportData} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
				Export
			</button>
			<button class="btn btn-primary" onclick={openAdd} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Log
			</button>
		</div>
	</header>

	<div class="table-wrap">
		<table class="table">
			<thead>
				<tr>
					<th style="width:30px"></th>
					<th>Name</th>
					<th class="right">Target EPS</th>
					<th class="right">Actual EPS</th>
					<th class="right">Count</th>
					<th class="right"></th>
				</tr>
			</thead>
			<tbody>
				{#if loading && items.length === 0}
					<tr><td colspan="6" class="empty">Loading…</td></tr>
				{:else if items.length === 0}
					<tr><td colspan="6" class="empty">No logs configured yet</td></tr>
				{:else}
					{#each items as item}
						<tr class="data-row" onclick={() => toggleExpand(item.name)}>
							<td class="expand-cell">
								<span class="expand-icon" class:open={expandedRows.has(item.name)}>
									<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
								</span>
							</td>
							<td class="name-cell">{item.name}</td>
							<td class="right">{item.eps.toLocaleString()}</td>
							<td class="right">
								<span class:running={item.currentEps > 0}>{item.currentEps.toLocaleString()}</span>
							</td>
							<td class="right">{item.count.toLocaleString()}</td>
							<td class="right" onclick={(e) => e.stopPropagation()}>
								<div class="row-actions">
									<button class="icon-btn" onclick={() => openCopy(item)} title="Copy" aria-label="Copy {item.name}">
										<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
									</button>
									<button class="icon-btn" onclick={() => openEdit(item)} title="Edit" aria-label="Edit {item.name}">
										<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
									</button>
									<button class="icon-btn danger" onclick={() => askDelete(item.name)} title="Delete" aria-label="Delete {item.name}">
										<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
									</button>
								</div>
							</td>
						</tr>
						{#if expandedRows.has(item.name)}
							<tr class="expand-row">
								<td colspan="6">
									<div class="expand-content">
										<div class="detail-grid">
											<div class="detail-item">
												<span class="detail-key">Format</span>
												<pre class="detail-value mono">{item.format}</pre>
											</div>
											{#if item.sample}
												<div class="detail-item">
													<span class="detail-key">Sample</span>
													<pre class="detail-value mono">{item.sample}</pre>
												</div>
											{/if}
											<div class="detail-item">
												<span class="detail-key">Senders</span>
												<div class="detail-value">
													{#each item.sender as s}
														<span class="badge">{s}</span>
													{/each}
												</div>
											</div>
										</div>
									</div>
								</td>
							</tr>
						{/if}
					{/each}
				{/if}
			</tbody>
		</table>
	</div>
</div>

<!-- Add/Edit Dialog -->
{#if dialogOpen}
	<div class="overlay" role="presentation" onclick={closeDialog}>
		<div class="dialog wide" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.key === 'Escape' && closeDialog()}>
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
							<textarea
								id="log-format"
								class="input mono-input"
								bind:value={formFormat}
								bind:this={formatTextarea}
								oninput={runPreview}
								rows="4"
								placeholder="<maker1> <maker2> some static text"
							></textarea>
						</div>

						<!-- Maker helper -->
						<div class="maker-helper">
							<button
								class="helper-toggle"
								type="button"
								onclick={() => (showMakerHelper = !showMakerHelper)}
							>
								<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
								Maker Palette
								<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="transform: rotate({showMakerHelper ? 90 : 0}deg); transition: transform 0.15s"><polyline points="9 18 15 12 9 6"/></svg>
							</button>

							{#if showMakerHelper}
								<div class="helper-panel">
									<div class="helper-controls">
										<button
											class="mode-pill"
											class:active={makerHelperShowName}
											onclick={() => (makerHelperShowName = true)}
										>Name</button>
										<button
											class="mode-pill"
											class:active={!makerHelperShowName}
											onclick={() => (makerHelperShowName = false)}
										>Sample</button>
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
							<pre class="preview-box mono">{previewText || 'Type a format above to see preview…'}</pre>
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
		<div class="dialog narrow" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.key === 'Escape' && (importOpen = false)}>
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
	.page { display: flex; flex-direction: column; gap: 1.5rem; }
	.page-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem; }
	.page-title { font-size: 1.5rem; font-weight: 800; margin: 0; letter-spacing: -0.03em; }
	.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }

	.table-wrap { background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; box-shadow: var(--shadow-sm); }
	.table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
	.table th { padding: 0.75rem 1rem; text-align: left; font-size: 0.75rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 1px solid var(--border); background: var(--bg-raised); }
	.table th.right { text-align: right; }
	.table td { padding: 0.875rem 1rem; border-bottom: 1px solid var(--border); color: var(--text-primary); vertical-align: middle; }
	.table tr:last-child td { border-bottom: none; }
	.data-row { cursor: pointer; }
	.data-row:hover td { background: var(--bg-raised); }
	.table td.right { text-align: right; }
	.name-cell { font-weight: 600; }
	.text-muted { color: var(--text-muted); }
	.empty { text-align: center; padding: 3rem 1rem; color: var(--text-muted); }

	.expand-cell { padding: 0.875rem 0.5rem 0.875rem 1rem; }
	.expand-icon { display: flex; align-items: center; color: var(--text-muted); transition: transform 0.15s; }
	.expand-icon.open { transform: rotate(90deg); }

	.expand-row td { padding: 0; background: var(--bg-raised); border-bottom: 1px solid var(--border); }
	.expand-content { padding: 1rem 1.5rem; }
	.detail-grid { display: flex; flex-direction: column; gap: 0.75rem; }
	.detail-item { display: flex; flex-direction: column; gap: 0.25rem; }
	.detail-key { font-size: 0.75rem; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
	.detail-value { color: var(--text-secondary); font-size: 0.875rem; }
	.detail-value.mono { font-family: var(--font-mono); font-size: 0.8125rem; white-space: pre-wrap; word-break: break-all; margin: 0; padding: 0.5rem 0.75rem; background: var(--bg-surface); border-radius: var(--radius-sm); border: 1px solid var(--border); }

	.running { color: var(--success); font-weight: 600; }

	.badge { display: inline-block; padding: 0.2rem 0.6rem; background: var(--accent-light); color: var(--accent); border-radius: 100px; font-size: 0.75rem; font-weight: 600; margin-right: 0.25rem; }

	.row-actions { display: flex; gap: 0.25rem; justify-content: flex-end; }
	.icon-btn { display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; border: none; background: none; border-radius: var(--radius-sm); color: var(--text-secondary); cursor: pointer; transition: all 0.15s; }
	.icon-btn:hover { background: var(--bg-raised); color: var(--text-primary); }
	.icon-btn.danger:hover { background: var(--danger-light); color: var(--danger); }

	.btn { display: inline-flex; align-items: center; gap: 0.375rem; padding: 0.5rem 0.875rem; border-radius: var(--radius-sm); font-size: 0.8125rem; font-weight: 500; border: 1px solid transparent; cursor: pointer; transition: all 0.15s; white-space: nowrap; }
	.btn:disabled { opacity: 0.6; cursor: not-allowed; }
	.btn-primary { background: var(--accent); color: white; border-color: var(--accent); }
	.btn-primary:hover:not(:disabled) { background: var(--accent-hover); }
	.btn-ghost { background: transparent; border-color: var(--border); color: var(--text-secondary); }
	.btn-ghost:hover:not(:disabled) { background: var(--bg-raised); color: var(--text-primary); }

	.overlay { position: fixed; inset: 0; background: var(--bg-overlay); display: flex; align-items: center; justify-content: center; z-index: 500; backdrop-filter: blur(2px); }
	.dialog { background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius-lg); width: 90%; max-width: 440px; max-height: 90vh; display: flex; flex-direction: column; box-shadow: var(--shadow-lg); animation: pop-in 0.15s ease-out; }
	.dialog.wide { max-width: 780px; }
	.dialog.narrow { max-width: 380px; }
	@keyframes pop-in { from { opacity: 0; transform: scale(0.97); } to { opacity: 1; transform: scale(1); } }
	.dialog-header { display: flex; align-items: center; justify-content: space-between; padding: 1.25rem 1.5rem 1rem; border-bottom: 1px solid var(--border); flex-shrink: 0; }
	.dialog-title { font-size: 1rem; font-weight: 700; margin: 0; }
	.close-btn { background: none; border: none; cursor: pointer; color: var(--text-muted); padding: 0.25rem; border-radius: var(--radius-sm); transition: all 0.15s; }
	.close-btn:hover { color: var(--text-primary); background: var(--bg-raised); }
	.dialog-body { padding: 1.25rem 1.5rem; overflow-y: auto; flex: 1; }
	.dialog-footer { padding: 1rem 1.5rem; border-top: 1px solid var(--border); display: flex; justify-content: flex-end; gap: 0.5rem; flex-shrink: 0; }

	.form-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
	@media (max-width: 600px) { .form-cols { grid-template-columns: 1fr; } }

	.field { display: flex; flex-direction: column; gap: 0.375rem; margin-bottom: 1rem; }
	.field-label { font-size: 0.75rem; font-weight: 600; color: var(--text-secondary); letter-spacing: 0.04em; display: flex; align-items: center; gap: 0.375rem; }
	.required { color: var(--danger); margin-left: 2px; }
	.input { width: 100%; padding: 0.5rem 0.75rem; background: var(--bg-raised); border: 1px solid var(--border); border-radius: var(--radius-sm); color: var(--text-primary); font-size: 0.875rem; font-family: inherit; transition: border-color 0.15s, box-shadow 0.15s; }
	.input:focus { outline: none; border-color: var(--border-focus); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 15%, transparent); }
	.input:disabled { opacity: 0.6; cursor: not-allowed; }
	.mono-input { font-family: var(--font-mono); font-size: 0.8125rem; resize: vertical; }

	.maker-helper { margin-bottom: 1rem; }
	.helper-toggle { display: flex; align-items: center; gap: 0.375rem; background: none; border: 1px solid var(--border); border-radius: var(--radius-sm); padding: 0.4rem 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); cursor: pointer; transition: all 0.15s; }
	.helper-toggle:hover { background: var(--bg-raised); color: var(--text-primary); }
	.helper-panel { margin-top: 0.625rem; padding: 0.875rem; background: var(--bg-raised); border: 1px solid var(--border); border-radius: var(--radius-sm); }
	.helper-controls { display: flex; gap: 0.375rem; margin-bottom: 0.75rem; }
	.mode-pill { padding: 0.25rem 0.625rem; border-radius: 100px; font-size: 0.75rem; font-weight: 600; border: 1px solid var(--border); background: none; color: var(--text-muted); cursor: pointer; transition: all 0.15s; }
	.mode-pill.active { background: var(--accent); border-color: var(--accent); color: white; }
	.maker-chips { display: flex; flex-wrap: wrap; gap: 0.375rem; }
	.maker-chip { padding: 0.25rem 0.625rem; background: var(--bg-surface); border: 1px solid var(--border); border-radius: 100px; font-size: 0.8125rem; font-family: var(--font-mono); cursor: pointer; color: var(--text-primary); transition: all 0.15s; }
	.maker-chip:hover { background: var(--accent-light); border-color: var(--accent); color: var(--accent); }

	.preview-box { margin: 0; padding: 0.625rem 0.75rem; background: var(--bg-raised); border: 1px solid var(--border); border-radius: var(--radius-sm); font-family: var(--font-mono); font-size: 0.8125rem; color: var(--text-secondary); white-space: pre-wrap; word-break: break-all; min-height: 48px; }
	.preview-spinner { display: inline-block; width: 10px; height: 10px; border: 2px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.6s linear infinite; }

	.sender-list { display: flex; flex-direction: column; gap: 0.375rem; max-height: 200px; overflow-y: auto; padding: 0.375rem; background: var(--bg-raised); border: 1px solid var(--border); border-radius: var(--radius-sm); }
	.sender-option { display: flex; align-items: center; gap: 0.625rem; padding: 0.5rem 0.625rem; border-radius: var(--radius-sm); cursor: pointer; font-size: 0.875rem; transition: background 0.15s; }
	.sender-option:hover { background: var(--bg-surface); }
	.sender-option.selected { background: var(--accent-light); }
	.sender-check { width: 16px; height: 16px; border: 2px solid var(--border); border-radius: 4px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; color: var(--accent); }
	.sender-option.selected .sender-check { background: var(--accent); border-color: var(--accent); color: white; }
	.sender-type { margin-left: auto; font-size: 0.75rem; color: var(--text-muted); }

	.upload-zone { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 2.5rem 1.5rem; border: 2px dashed var(--border); border-radius: var(--radius-md); cursor: pointer; text-align: center; transition: all 0.15s; }
	.upload-zone:hover { border-color: var(--accent); background: var(--accent-light); }
	.upload-label { font-size: 0.875rem; font-weight: 600; color: var(--text-primary); }
	.spinner { width: 14px; height: 14px; border: 2px solid rgba(255,255,255,0.3); border-top-color: white; border-radius: 50%; animation: spin 0.6s linear infinite; }
	@keyframes spin { to { transform: rotate(360deg); } }
</style>
