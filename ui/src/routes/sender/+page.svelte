<script lang="ts">
	import { api } from '$lib/api';
	import DynamicInput from '$lib/components/DynamicInput.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import { addToast } from '$lib/stores/toast.svelte';
	import type { Sender, PluginType } from '$lib/types';

	let items = $state<Sender[]>([]);
	let types = $state<PluginType[]>([]);
	let loading = $state(false);

	let dialogOpen = $state(false);
	let editMode = $state(false);
	let importOpen = $state(false);
	let confirmOpen = $state(false);
	let confirmName = $state('');
	let confirmLoading = $state(false);

	let formName = $state('');
	let formType = $state('');
	let formArgs = $state<Record<string, string | number | boolean | string[]>>({});

	async function fetchItems() {
		loading = true;
		try { items = await api.getSenders(); }
		catch { /* toast shown */ }
		finally { loading = false; }
	}

	async function fetchTypes() {
		try { types = await api.getSenderTypes(); }
		catch { /* ignored */ }
	}

	function openAdd() {
		editMode = false; formName = ''; formType = ''; formArgs = {};
		dialogOpen = true; fetchTypes();
	}

	function openEdit(item: Sender) {
		editMode = true; formName = item.name; formType = item.type; formArgs = { ...item.args };
		dialogOpen = true; fetchTypes();
	}

	function openCopy(item: Sender) {
		editMode = false; formName = 'copy-of-' + item.name; formType = item.type; formArgs = { ...item.args };
		dialogOpen = true; fetchTypes();
	}

	function closeDialog() { dialogOpen = false; }

	function getCurrentArgs() {
		const t = types.find((t) => t.type === formType);
		return t ? t.args : {};
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
			if (editMode) await api.updateSender(formName, payload);
			else await api.createSender(payload);
			closeDialog();
			await fetchItems();
		} catch { /* toast shown */ }
		finally { loading = false; }
	}

	function askDelete(name: string) { confirmName = name; confirmOpen = true; }

	async function confirmDelete() {
		confirmLoading = true;
		try { await api.deleteSender(confirmName); confirmOpen = false; await fetchItems(); }
		catch { /* toast shown */ }
		finally { confirmLoading = false; }
	}

	async function exportData() {
		const data = await api.getSenders();
		const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = 'logmaker-sender.json';
		a.click();
	}

	async function handleImport(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0];
		if (!file) return;
		loading = true;
		try {
			const formData = new FormData();
			formData.append('file', file);
			const res = await fetch('/api/v1/sender:import-file', { method: 'POST', body: formData });
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

<svelte:head><title>Sender — LogMaker</title></svelte:head>

<div class="page">
	<header class="page-header">
		<h1 class="page-title">Sender</h1>
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
			<button class="btn btn-primary" onclick={openAdd} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Sender
			</button>
		</div>
	</header>

	<div class="table-wrap">
		<table class="table" aria-label="Sender list">
			<thead>
				<tr>
					<th>Name</th>
					<th>Type</th>
					<th class="right">Count</th>
					<th class="right">Used</th>
					<th class="right"></th>
				</tr>
			</thead>
			<tbody>
				{#if loading && items.length === 0}
					<tr><td colspan="5" class="empty">Loading…</td></tr>
				{:else if items.length === 0}
					<tr>
						<td colspan="5">
							<div class="empty-state">
								<div class="empty-state-icon">
									<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
								</div>
								<p class="empty-state-title">No senders yet</p>
								<p class="empty-state-desc">Add a sender to define where your generated logs will be delivered</p>
								<button class="btn btn-primary" onclick={openAdd}>
									<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
									Add Sender
								</button>
							</div>
						</td>
					</tr>
				{:else}
					{#each items as item}
						<tr>
							<td class="name-cell">{item.name}</td>
							<td><span class="badge">{item.type}</span></td>
							<td class="right">{item.count ?? '—'}</td>
							<td class="right">
								{#if (item.ref ?? 0) > 0}
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
										disabled={(item.ref ?? 0) > 0}
										title={(item.ref ?? 0) > 0 ? 'In use by logs' : 'Delete'}
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
		<div class="dialog" role="dialog" aria-modal="true" tabindex="-1" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.key === 'Escape' && closeDialog()}>
			<div class="dialog-header">
				<h2 class="dialog-title">{editMode ? 'Edit Sender' : 'Add Sender'}</h2>
				<button class="close-btn" onclick={closeDialog} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<div class="field">
					<label class="field-label" for="sender-name">NAME <span class="required">*</span></label>
					<input id="sender-name" class="input" type="text" bind:value={formName} disabled={editMode} placeholder="my-sender" />
				</div>
				<div class="field">
					<label class="field-label" for="sender-type">TYPE <span class="required">*</span></label>
					<select id="sender-type" class="input" bind:value={formType} disabled={editMode} onchange={() => (formArgs = {})}>
						<option value="" disabled>Select type…</option>
						{#each types as t}
							<option value={t.type}>{t.type}</option>
						{/each}
					</select>
				</div>
				{#if formType}
					{@const args = getCurrentArgs()}
					{#if Object.keys(args).length > 0}
						<div class="args-divider"><span>Arguments</span></div>
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
					{editMode ? 'Save Changes' : 'Add Sender'}
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
				<h2 class="dialog-title">Import Senders</h2>
				<button class="close-btn" onclick={() => (importOpen = false)} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<label class="upload-zone">
					<input type="file" accept=".json" class="sr-only" onchange={handleImport} />
					<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.5"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
					<span class="upload-label">Drop JSON file here or click to browse</span>
					<span class="upload-hint">Exported sender configuration file</span>
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
	title="Delete Sender"
	message="Delete '{confirmName}'? This cannot be undone."
	loading={confirmLoading}
	onconfirm={confirmDelete}
	oncancel={() => (confirmOpen = false)}
/>

<style>
	/* Page-specific styles only — shared rules live in app.css */
</style>
