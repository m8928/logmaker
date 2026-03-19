<script lang="ts">
	import { tick } from 'svelte';
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
	let errors = $state<Record<string, string>>({});

	// Focus restore
	let triggerElement = $state<HTMLElement | null>(null);

	function validate(): boolean {
		errors = {};
		if (!formName.trim()) errors.name = 'Name is required';
		if (formName && !/^[a-z0-9][a-z0-9-]*$/.test(formName)) errors.name = 'Only lowercase letters, numbers, and hyphens allowed';
		if (!formType) errors.type = 'Type is required';
		return Object.keys(errors).length === 0;
	}

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

	function openAdd(e?: MouseEvent) {
		triggerElement = (e?.currentTarget as HTMLElement) ?? null;
		editMode = false;
		formName = '';
		formType = '';
		formArgs = {};
		errors = {};
		dialogOpen = true;
		fetchTypes();
	}

	function openEdit(item: Maker, e?: MouseEvent) {
		triggerElement = (e?.currentTarget as HTMLElement) ?? null;
		editMode = true;
		formName = item.name;
		formType = item.type;
		formArgs = { ...item.args };
		errors = {};
		dialogOpen = true;
		fetchTypes();
	}

	function openCopy(item: Maker, e?: MouseEvent) {
		triggerElement = (e?.currentTarget as HTMLElement) ?? null;
		editMode = false;
		formName = 'copy-of-' + item.name;
		formType = item.type;
		formArgs = { ...item.args };
		errors = {};
		dialogOpen = true;
		fetchTypes();
	}

	function closeDialog() {
		dialogOpen = false;
		triggerElement?.focus();
		triggerElement = null;
	}

	$effect(() => {
		if (dialogOpen) {
			tick().then(() => {
				const firstInput = document.querySelector<HTMLElement>('.dialog input:not([disabled]), .dialog select');
				firstInput?.focus();
			});
		}
	});

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
		if (!validate()) return;
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
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = 'logmaker-maker.json';
		a.click();
		setTimeout(() => URL.revokeObjectURL(url), 1000);
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
				const failed = result
					.filter((r: { type: string; message?: string }) => r.type === 'ERROR')
					.map((r: { type: string; message?: string }) => r.message ?? 'unknown')
					.join(', ');
				addToast('error', `Import failed for: ${failed}`);
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
				{#if loading}
					<span class="spinner-muted"></span>
				{:else}
					<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				{/if}
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
			<button class="btn btn-primary" onclick={(e) => openAdd(e)} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Maker
			</button>
		</div>
	</header>

	<div class="table-wrap">
		<table class="table" aria-label="Maker list">
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
					<tr>
						<td colspan="6">
							<div class="empty-state">
								<div class="empty-state-icon">
									<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.77-3.77a6 6 0 01-7.94 7.94l-6.91 6.91a2.12 2.12 0 01-3-3l6.91-6.91a6 6 0 017.94-7.94l-3.76 3.76z"/></svg>
								</div>
								<p class="empty-state-title">No makers yet</p>
								<p class="empty-state-desc">Create your first data generator to start building log templates</p>
								<button class="btn btn-primary" onclick={(e) => openAdd(e)}>
									<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
									Add Maker
								</button>
							</div>
						</td>
					</tr>
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
								description={arg.description}
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
	/* Page-specific styles only — shared rules live in app.css */
	.mono { font-family: var(--font-mono); font-size: 0.8125rem; }
</style>
