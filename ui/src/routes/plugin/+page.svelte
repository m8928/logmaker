<script lang="ts">
	import { api } from '$lib/api';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import { addToast } from '$lib/stores/toast.svelte';
	import type { Plugin } from '$lib/types';

	let items = $state<Plugin[]>([]);
	let loading = $state(false);
	let uploadOpen = $state(false);
	let confirmOpen = $state(false);
	let confirmName = $state('');
	let confirmLoading = $state(false);
	let uploading = $state(false);
	let dragOver = $state(false);

	async function fetchItems() {
		loading = true;
		try { items = await api.getPlugins(); }
		catch { /* toast shown */ }
		finally { loading = false; }
	}

	function askDelete(name: string) { confirmName = name; confirmOpen = true; }

	async function confirmDelete() {
		confirmLoading = true;
		try { await api.deletePlugin(confirmName); confirmOpen = false; await fetchItems(); }
		catch { /* toast shown */ }
		finally { confirmLoading = false; }
	}

	async function uploadPlugin(file: File) {
		if (!file.name.endsWith('.jar')) {
			addToast('error', 'Only .jar files are supported');
			return;
		}
		uploading = true;
		try {
			const fd = new FormData();
			fd.append('file', file);
			const res = await fetch('/api/v1/plugin', { method: 'POST', body: fd });
			const result = await res.json();
			if (result.type === 'ERROR') {
				addToast('error', result.notification || result.message || 'Upload failed');
			} else {
				addToast('success', result.notification || 'Plugin installed successfully');
				uploadOpen = false;
			}
			await fetchItems();
		} catch {
			addToast('error', 'Upload failed');
		} finally {
			uploading = false;
		}
	}

	function handleFileInput(e: Event) {
		const file = (e.target as HTMLInputElement).files?.[0];
		if (file) uploadPlugin(file);
	}

	function handleDrop(e: DragEvent) {
		e.preventDefault();
		dragOver = false;
		const file = e.dataTransfer?.files?.[0];
		if (file) uploadPlugin(file);
	}

	function handleDragOver(e: DragEvent) {
		e.preventDefault();
		dragOver = true;
	}

	const stateColors: Record<string, string> = {
		ACTIVE: 'success',
		STOPPED: 'warning',
		FAILED: 'danger',
		UNRESOLVED: 'info'
	};

	$effect(() => { fetchItems(); });
</script>

<svelte:head><title>Plugin — LogMaker</title></svelte:head>

<div class="page">
	<header class="page-header">
		<h1 class="page-title">Plugin</h1>
		<div class="actions">
			<button class="btn btn-ghost" onclick={fetchItems} disabled={loading}>
				{#if loading}
					<span class="spinner-muted"></span>
				{:else}
					<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				{/if}
				Reload
			</button>
			<button class="btn btn-primary" onclick={() => (uploadOpen = true)} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Plugin
			</button>
		</div>
	</header>

	<div class="table-wrap">
		<table class="table" aria-label="Plugin list">
			<thead>
				<tr>
					<th>Name</th>
					<th>Version</th>
					<th>Provider</th>
					<th>State</th>
					<th>Filename</th>
					<th class="right">Used</th>
					<th class="right"></th>
				</tr>
			</thead>
			<tbody>
				{#if loading && items.length === 0}
					<tr><td colspan="7" class="empty">Loading…</td></tr>
				{:else if items.length === 0}
					<tr>
						<td colspan="7">
							<div class="empty-state">
								<div class="empty-state-icon">
									<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M20.24 12.24a6 6 0 00-8.49-8.49L5 10.5V19h8.5z"/><line x1="16" y1="8" x2="2" y2="22"/><line x1="17.5" y1="15" x2="9" y2="15"/></svg>
								</div>
								<p class="empty-state-title">No plugins installed</p>
								<p class="empty-state-desc">Install a plugin JAR to extend LogMaker with custom makers and senders</p>
								<button class="btn btn-primary" onclick={() => (uploadOpen = true)}>
									<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
									Add Plugin
								</button>
							</div>
						</td>
					</tr>
				{:else}
					{#each items as item}
						<tr>
							<td class="name-cell">{item.name}</td>
							<td>
								<span class="badge">{item.version ?? '—'}</span>
							</td>
							<td class="text-secondary">{item.provider ?? '—'}</td>
							<td>
								{#if item.pluginState}
									<span class="state-badge {stateColors[item.pluginState] ?? 'info'}">{item.pluginState}</span>
								{:else}
									<span class="text-muted">—</span>
								{/if}
							</td>
							<td class="mono text-secondary">{item.filename ?? '—'}</td>
							<td class="right">
								{#if (item.ref ?? 0) > 0}
									<span class="badge-ref">{item.ref}</span>
								{:else}
									<span class="text-muted">0</span>
								{/if}
							</td>
							<td class="right">
								<button
									class="icon-btn danger"
									onclick={() => askDelete(item.name)}
									disabled={(item.ref ?? 0) > 0}
									title={(item.ref ?? 0) > 0 ? 'In use' : 'Delete'}
									aria-label="Delete {item.name}"
								>
									<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6M14 11v6M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
								</button>
							</td>
						</tr>
					{/each}
				{/if}
			</tbody>
		</table>
	</div>
</div>

<!-- Upload Dialog -->
{#if uploadOpen}
	<div class="overlay" role="presentation" onclick={() => (uploadOpen = false)}>
		<div class="dialog" role="dialog" aria-modal="true" tabindex="-1" aria-label="Add Plugin" onclick={(e) => e.stopPropagation()} onkeydown={(e) => e.key === 'Escape' && (uploadOpen = false)}>
			<div class="dialog-header">
				<h2 class="dialog-title">Install Plugin</h2>
				<button class="close-btn" onclick={() => (uploadOpen = false)} aria-label="Close">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>
			<div class="dialog-body">
				<label
					class="upload-zone"
					class:drag={dragOver}
					class:busy={uploading}
					ondrop={handleDrop}
					ondragover={handleDragOver}
					ondragleave={() => (dragOver = false)}
				>
					<input type="file" accept=".jar" class="sr-only" onchange={handleFileInput} disabled={uploading} />
					{#if uploading}
						<div class="upload-spinner"></div>
						<span class="upload-label">Installing…</span>
					{:else}
						<svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" stroke-width="1.5">
							<path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
						</svg>
						<span class="upload-label">Drop .jar file here or click to browse</span>
						<span class="upload-hint">Plugin JAR files only</span>
					{/if}
				</label>
			</div>
			<div class="dialog-footer">
				<button class="btn btn-ghost" onclick={() => (uploadOpen = false)} disabled={uploading}>Cancel</button>
			</div>
		</div>
	</div>
{/if}

<ConfirmDialog
	open={confirmOpen}
	title="Delete Plugin"
	message="Delete '{confirmName}'? This cannot be undone."
	loading={confirmLoading}
	onconfirm={confirmDelete}
	oncancel={() => (confirmOpen = false)}
/>

<style>
	/* Page-specific styles only — shared rules live in app.css */
	.state-badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 100px; font-size: 0.75rem; font-weight: 600; }
	.state-badge.success { background: var(--success-light); color: var(--success); }
	.state-badge.warning { background: var(--warning-light); color: var(--warning); }
	.state-badge.danger { background: var(--danger-light); color: var(--danger); }
	.state-badge.info { background: var(--info-light); color: var(--info); }
	.upload-spinner { width: 32px; height: 32px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
</style>
