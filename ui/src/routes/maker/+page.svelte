<script lang="ts">
	import { api } from '$lib/api';
	import DynamicInput from '$lib/components/DynamicInput.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import { addToast } from '$lib/stores/toast.svelte';
	import type { Maker, PluginType } from '$lib/types';

	let items = $state<Maker[]>([]);
	let types = $state<PluginType[]>([]);
	let loading = $state(false);

	// Dialog state
	let dialogOpen = $state(false);
	let editMode = $state(false);
	let importOpen = $state(false);
	let confirmOpen = $state(false);
	let confirmName = $state('');
	let confirmLoading = $state(false);

	// Form state
	let formName = $state('');
	let formType = $state('');
	let formArgs = $state<Record<string, string | number | boolean | string[]>>({});

	async function fetchItems() {
		loading = true;
		try {
			items = await api.getMakers();
		} catch {
			/* toast already shown */
		} finally {
			loading = false;
		}
	}

	async function fetchTypes() {
		try {
			types = await api.getMakerTypes();
		} catch { /* ignored */ }
	}

	function openAdd() {
		editMode = false;
		formName = '';
		formType = '';
		formArgs = {};
		dialogOpen = true;
		fetchTypes();
	}

	function openEdit(item: Maker) {
		editMode = true;
		formName = item.name;
		formType = item.type;
		formArgs = { ...item.args };
		dialogOpen = true;
		fetchTypes();
	}

	function openCopy(item: Maker) {
		editMode = false;
		formName = 'copy-of-' + item.name;
		formType = item.type;
		formArgs = { ...item.args };
		dialogOpen = true;
		fetchTypes();
	}

	function closeDialog() {
		dialogOpen = false;
	}

	function getCurrentArgs() {
		const t = types.find((t) => t.type === formType);
		return t ? t.args : {};
	}

	function handleTypeChange() {
		formArgs = {};
	}

	function handleArgChange(name: string, value: string | number | boolean | string[]) {
		formArgs = { ...formArgs, [name]: value };
	}

	async function submit() {
		if (!formName.trim() || !formType) {
			addToast('warning', 'Name and Type are required');
			return;
		}
		loading = true;
		try {
			const payload = { name: formName, type: formType, args: formArgs };
			if (editMode) {
				await api.updateMaker(formName, payload);
			} else {
				await api.createMaker(payload);
			}
			closeDialog();
			await fetchItems();
		} catch { /* toast shown */ } finally {
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
			await api.deleteMaker(confirmName);
			confirmOpen = false;
			await fetchItems();
		} catch { /* toast shown */ } finally {
			confirmLoading = false;
		}
	}

	async function exportData() {
		const data = await api.getMakers();
		const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = 'logmaker-maker.json';
		a.click();
	}

	let importInput = $state<HTMLInputElement | null>(null);

	async function handleImport(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0];
		if (!file) return;
		loading = true;
		try {
			const formData = new FormData();
			formData.append('file', file);
			const res = await fetch('/api/v1/maker:import-file', { method: 'POST', body: formData });
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

	$effect(() => { fetchItems(); });
</script>

<svelte:head><title>Maker — LogMaker</title></svelte:head>

<div class="page">
	<header class="page-header">
		<h1 class="page-title">Maker</h1>
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
				Add Maker
			</button>
		</div>
	</header>

	<div class="table-wrap">
		<table class="table">
			<thead>
				<tr>
					<th>Name</th>
					<th>Type</th>
					<th class="right">Sample</th>
					<th class="right">Size</th>
					<th class="right">Used</th>
					<th class="right"></th>
				</tr>
			</thead>
			<tbody>
				{#if loading && items.length === 0}
					<tr><td colspan="6" class="empty">Loading…</td></tr>
				{:else if items.length === 0}
					<tr><td colspan="6" class="empty">No makers configured yet</td></tr>
				{:else}
					{#each items as item}
						<tr>
							<td class="name-cell">{item.name}</td>
							<td><span class="badge">{item.type}</span></td>
							<td class="right mono text-muted">{item.sample ?? '—'}</td>
							<td class="right">{item.size ?? '—'}</td>
							<td class="right">
								{#if item.ref > 0}
									<span class="badge-ref">{item.ref}</span>
								{:else}
									<span class="text-muted">0</span>
								{/if}
							</td>
							<td class="right">
								<div class="row-actions">
									<button class="icon-btn" onclick={() => openCopy(item)} title="Copy" aria-label="Copy {item.name}">
										<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
									</button>
									<button class="icon-btn" onclick={() => openEdit(item)} title="Edit" aria-label="Edit {item.name}">
										<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
									</button>
									<button
										class="icon-btn danger"
										onclick={() => askDelete(item.name)}
										disabled={item.ref > 0}
										title={item.ref > 0 ? 'In use by logs' : 'Delete'}
										aria-label="Delete {item.name}"
									>
										<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
									</button>
								</div>
							</td>
						</tr>
					{/each}
				{/if}
			</tbody>
		</table>
	</div>
</div>

<!-- Add/Edit Dialog -->
{#if dialogOpen}
	<div class="overlay" role="presentation" onclick={closeDialog}>
		<div class="dialog" role="dialog" aria-modal="true" tabindex="-1" aria-label="{editMode ? 'Edit' : 'Add'} Maker" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.key === 'Escape' && closeDialog()}>
			<div class="dialog-header">
				<h2 class="dialog-title">{editMode ? 'Edit Maker' : 'Add Maker'}</h2>
				<button class="close-btn" onclick={closeDialog} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<div class="field">
					<label class="field-label" for="maker-name">NAME <span class="required">*</span></label>
					<input id="maker-name" class="input" type="text" bind:value={formName} disabled={editMode} placeholder="my-maker" />
				</div>
				<div class="field">
					<label class="field-label" for="maker-type">TYPE <span class="required">*</span></label>
					<select id="maker-type" class="input" bind:value={formType} disabled={editMode} onchange={handleTypeChange}>
						<option value="" disabled>Select type…</option>
						{#each types as t}
							<option value={t.type}>{t.type}</option>
						{/each}
					</select>
				</div>
				{#if formType}
					{@const args = getCurrentArgs()}
					{#if Object.keys(args).length > 0}
						<div class="args-divider">
							<span>Arguments</span>
						</div>
						{#each Object.entries(args) as [key, arg]}
							<DynamicInput
								name={key}
								type={arg.type}
								value={formArgs[key] ?? (arg.type === 'java.lang.Boolean' ? false : arg.type === 'java.util.ArrayList' ? [] : arg.type === 'java.lang.Integer' || arg.type === 'java.lang.Long' ? 0 : '')}
								required={arg.required}
								onchange={handleArgChange}
							/>
						{/each}
					{/if}
				{/if}
			</div>
			<div class="dialog-footer">
				<button class="btn btn-ghost" onclick={closeDialog} disabled={loading}>Cancel</button>
				<button class="btn btn-primary" onclick={submit} disabled={loading}>
					{#if loading}<span class="spinner"></span>{/if}
					{editMode ? 'Save Changes' : 'Add Maker'}
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
				<h2 class="dialog-title">Import Makers</h2>
				<button class="close-btn" onclick={() => (importOpen = false)} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<label class="upload-zone">
					<input bind:this={importInput} type="file" accept=".json" class="sr-only" onchange={handleImport} />
					<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.5"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
					<span class="upload-label">Drop JSON file here or click to browse</span>
					<span class="upload-hint">Exported maker configuration file</span>
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
	title="Delete Maker"
	message="Delete '{confirmName}'? This cannot be undone."
	loading={confirmLoading}
	onconfirm={confirmDelete}
	oncancel={() => (confirmOpen = false)}
/>

<style>
	.page { display: flex; flex-direction: column; gap: 1.5rem; }

	.page-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: 1rem;
	}

	.page-title { font-size: 1.5rem; font-weight: 800; margin: 0; letter-spacing: -0.03em; }

	.actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }

	.table-wrap {
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		overflow: hidden;
		box-shadow: var(--shadow-sm);
	}

	.table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.875rem;
	}

	.table th {
		padding: 0.75rem 1rem;
		text-align: left;
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.05em;
		border-bottom: 1px solid var(--border);
		background: var(--bg-raised);
	}

	.table th.right { text-align: right; }

	.table td {
		padding: 0.875rem 1rem;
		border-bottom: 1px solid var(--border);
		color: var(--text-primary);
		vertical-align: middle;
	}

	.table tr:last-child td { border-bottom: none; }

	.table tr:hover td { background: var(--bg-raised); }

	.table td.right { text-align: right; }

	.name-cell { font-weight: 600; }

	.text-muted { color: var(--text-muted); }

	.mono { font-family: var(--font-mono); font-size: 0.8125rem; }

	.empty { text-align: center; padding: 3rem 1rem; color: var(--text-muted); }

	.badge {
		display: inline-block;
		padding: 0.2rem 0.6rem;
		background: var(--accent-light);
		color: var(--accent);
		border-radius: 100px;
		font-size: 0.75rem;
		font-weight: 600;
	}

	.badge-ref {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 22px;
		height: 22px;
		background: var(--warning-light);
		color: var(--warning);
		border-radius: 50%;
		font-size: 0.75rem;
		font-weight: 700;
	}

	.row-actions { display: flex; gap: 0.25rem; justify-content: flex-end; }

	.icon-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 30px;
		height: 30px;
		border: none;
		background: none;
		border-radius: var(--radius-sm);
		color: var(--text-secondary);
		cursor: pointer;
		transition: all 0.15s;
	}

	.icon-btn:hover { background: var(--bg-raised); color: var(--text-primary); }
	.icon-btn.danger:hover:not(:disabled) { background: var(--danger-light); color: var(--danger); }
	.icon-btn:disabled { opacity: 0.35; cursor: not-allowed; }

	/* Buttons */
	.btn {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
		padding: 0.5rem 0.875rem;
		border-radius: var(--radius-sm);
		font-size: 0.8125rem;
		font-weight: 500;
		border: 1px solid transparent;
		cursor: pointer;
		transition: all 0.15s;
		white-space: nowrap;
	}

	.btn:disabled { opacity: 0.6; cursor: not-allowed; }

	.btn-primary { background: var(--accent); color: white; border-color: var(--accent); }
	.btn-primary:hover:not(:disabled) { background: var(--accent-hover); }

	.btn-ghost { background: transparent; border-color: var(--border); color: var(--text-secondary); }
	.btn-ghost:hover:not(:disabled) { background: var(--bg-raised); color: var(--text-primary); }

	/* Dialog */
	.overlay {
		position: fixed; inset: 0;
		background: var(--bg-overlay);
		display: flex; align-items: center; justify-content: center;
		z-index: 500; backdrop-filter: blur(2px);
	}

	.dialog {
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-lg);
		width: 90%;
		max-width: 440px;
		max-height: 90vh;
		display: flex;
		flex-direction: column;
		box-shadow: var(--shadow-lg);
		animation: pop-in 0.15s ease-out;
	}

	.dialog.narrow { max-width: 380px; }

	@keyframes pop-in {
		from { opacity: 0; transform: scale(0.97); }
		to { opacity: 1; transform: scale(1); }
	}

	.dialog-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 1.25rem 1.5rem 1rem;
		border-bottom: 1px solid var(--border);
	}

	.dialog-title { font-size: 1rem; font-weight: 700; margin: 0; }

	.close-btn {
		background: none; border: none; cursor: pointer;
		color: var(--text-muted); padding: 0.25rem;
		border-radius: var(--radius-sm); transition: all 0.15s;
	}
	.close-btn:hover { color: var(--text-primary); background: var(--bg-raised); }

	.dialog-body { padding: 1.25rem 1.5rem; overflow-y: auto; flex: 1; }

	.dialog-footer {
		padding: 1rem 1.5rem;
		border-top: 1px solid var(--border);
		display: flex;
		justify-content: flex-end;
		gap: 0.5rem;
	}

	/* Form fields */
	.field { display: flex; flex-direction: column; gap: 0.375rem; margin-bottom: 1rem; }

	.field-label {
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-secondary);
		letter-spacing: 0.04em;
	}

	.required { color: var(--danger); margin-left: 2px; }

	.input {
		width: 100%;
		padding: 0.5rem 0.75rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-primary);
		font-size: 0.875rem;
		font-family: inherit;
		transition: border-color 0.15s, box-shadow 0.15s;
	}

	.input:focus {
		outline: none;
		border-color: var(--border-focus);
		box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 15%, transparent);
	}

	.input:disabled { opacity: 0.6; cursor: not-allowed; }

	.args-divider {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		margin: 0.5rem 0 1rem;
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.args-divider::before, .args-divider::after {
		content: '';
		flex: 1;
		height: 1px;
		background: var(--border);
	}

	.upload-zone {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.75rem;
		padding: 2.5rem 1.5rem;
		border: 2px dashed var(--border);
		border-radius: var(--radius-md);
		cursor: pointer;
		text-align: center;
		transition: all 0.15s;
	}

	.upload-zone:hover { border-color: var(--accent); background: var(--accent-light); }

	.upload-label { font-size: 0.875rem; font-weight: 600; color: var(--text-primary); }
	.upload-hint { font-size: 0.8125rem; color: var(--text-muted); }

	.spinner {
		width: 14px; height: 14px;
		border: 2px solid rgba(255, 255, 255, 0.3);
		border-top-color: white;
		border-radius: 50%;
		animation: spin 0.6s linear infinite;
	}

	@keyframes spin { to { transform: rotate(360deg); } }
</style>
