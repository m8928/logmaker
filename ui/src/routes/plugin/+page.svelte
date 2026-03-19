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
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				Reload
			</button>
			<button class="btn btn-primary" onclick={() => (uploadOpen = true)} disabled={loading}>
				<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Plugin
			</button>
		</div>
	</header>

	<div class="table-wrap">
		<table class="table">
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
					<tr><td colspan="7" class="empty">No plugins installed yet</td></tr>
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
	.page { display: flex; flex-direction: column; gap: 1.5rem; }
	.page-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 1rem; }
	.page-title { font-size: 1.5rem; font-weight: 800; margin: 0; letter-spacing: -0.03em; }
	.actions { display: flex; gap: 0.5rem; }

	.table-wrap { background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; box-shadow: var(--shadow-sm); }
	.table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
	.table th { padding: 0.75rem 1rem; text-align: left; font-size: 0.75rem; font-weight: 600; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 1px solid var(--border); background: var(--bg-raised); }
	.table th.right { text-align: right; }
	.table td { padding: 0.875rem 1rem; border-bottom: 1px solid var(--border); color: var(--text-primary); vertical-align: middle; }
	.table tr:last-child td { border-bottom: none; }
	.table tr:hover td { background: var(--bg-raised); }
	.table td.right { text-align: right; }
	.name-cell { font-weight: 600; }
	.text-muted { color: var(--text-muted); }
	.text-secondary { color: var(--text-secondary); }
	.mono { font-family: var(--font-mono); font-size: 0.8125rem; }
	.empty { text-align: center; padding: 3rem 1rem; color: var(--text-muted); }

	.badge { display: inline-block; padding: 0.2rem 0.6rem; background: var(--accent-light); color: var(--accent); border-radius: 100px; font-size: 0.75rem; font-weight: 600; }
	.badge-ref { display: inline-flex; align-items: center; justify-content: center; width: 22px; height: 22px; background: var(--warning-light); color: var(--warning); border-radius: 50%; font-size: 0.75rem; font-weight: 700; }
	.state-badge { display: inline-block; padding: 0.2rem 0.6rem; border-radius: 100px; font-size: 0.75rem; font-weight: 600; }
	.state-badge.success { background: var(--success-light); color: var(--success); }
	.state-badge.warning { background: var(--warning-light); color: var(--warning); }
	.state-badge.danger { background: var(--danger-light); color: var(--danger); }
	.state-badge.info { background: var(--info-light); color: var(--info); }

	.icon-btn { display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; border: none; background: none; border-radius: var(--radius-sm); color: var(--text-secondary); cursor: pointer; transition: all 0.15s; }
	.icon-btn.danger:hover:not(:disabled) { background: var(--danger-light); color: var(--danger); }
	.icon-btn:disabled { opacity: 0.35; cursor: not-allowed; }

	.btn { display: inline-flex; align-items: center; gap: 0.375rem; padding: 0.5rem 0.875rem; border-radius: var(--radius-sm); font-size: 0.8125rem; font-weight: 500; border: 1px solid transparent; cursor: pointer; transition: all 0.15s; white-space: nowrap; }
	.btn:disabled { opacity: 0.6; cursor: not-allowed; }
	.btn-primary { background: var(--accent); color: white; border-color: var(--accent); }
	.btn-primary:hover:not(:disabled) { background: var(--accent-hover); }
	.btn-ghost { background: transparent; border-color: var(--border); color: var(--text-secondary); }
	.btn-ghost:hover:not(:disabled) { background: var(--bg-raised); color: var(--text-primary); }

	.overlay { position: fixed; inset: 0; background: var(--bg-overlay); display: flex; align-items: center; justify-content: center; z-index: 500; backdrop-filter: blur(2px); }
	.dialog { background: var(--bg-surface); border: 1px solid var(--border); border-radius: var(--radius-lg); width: 90%; max-width: 400px; max-height: 90vh; display: flex; flex-direction: column; box-shadow: var(--shadow-lg); animation: pop-in 0.15s ease-out; }
	@keyframes pop-in { from { opacity: 0; transform: scale(0.97); } to { opacity: 1; transform: scale(1); } }
	.dialog-header { display: flex; align-items: center; justify-content: space-between; padding: 1.25rem 1.5rem 1rem; border-bottom: 1px solid var(--border); }
	.dialog-title { font-size: 1rem; font-weight: 700; margin: 0; }
	.close-btn { background: none; border: none; cursor: pointer; color: var(--text-muted); padding: 0.25rem; border-radius: var(--radius-sm); transition: all 0.15s; }
	.close-btn:hover { color: var(--text-primary); background: var(--bg-raised); }
	.dialog-body { padding: 1.25rem 1.5rem; overflow-y: auto; flex: 1; }
	.dialog-footer { padding: 1rem 1.5rem; border-top: 1px solid var(--border); display: flex; justify-content: flex-end; gap: 0.5rem; }

	.upload-zone { display: flex; flex-direction: column; align-items: center; gap: 0.75rem; padding: 3rem 1.5rem; border: 2px dashed var(--border); border-radius: var(--radius-md); cursor: pointer; text-align: center; transition: all 0.2s; }
	.upload-zone:hover, .upload-zone.drag { border-color: var(--accent); background: var(--accent-light); }
	.upload-zone.busy { pointer-events: none; opacity: 0.7; }
	.upload-label { font-size: 0.875rem; font-weight: 600; color: var(--text-primary); }
	.upload-hint { font-size: 0.8125rem; color: var(--text-muted); }
	.upload-spinner { width: 32px; height: 32px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
	@keyframes spin { to { transform: rotate(360deg); } }
</style>
