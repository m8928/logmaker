<script lang="ts">
	import { api } from '$lib/api';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import Select from '$lib/components/Select.svelte';
	import { addToast } from '$lib/stores/toast.svelte';
	import type { Log, Maker, Scenario, ScenarioStep, Sender } from '$lib/types';

	// ── List state ───────────────────────────────────────────────────────────────
	let items = $state<Scenario[]>([]);
	let logs = $state<Log[]>([]);
	let makers = $state<Maker[]>([]);
	let senders = $state<Sender[]>([]);
	let loading = $state(false);
	let search = $state('');
	let viewMode = $state<'grid' | 'table'>('grid');

	// ── Dialog state ─────────────────────────────────────────────────────────────
	let dialogOpen = $state(false);
	let editMode = $state(false);
	let confirmOpen = $state(false);
	let confirmName = $state('');
	let confirmLoading = $state(false);
	let saving = $state(false);
	let saveAndRun = $state(false);

	// ── Form state ───────────────────────────────────────────────────────────────
	let formName = $state('');
	let formIntervalMin = $state(1000);
	let formIntervalMax = $state(5000);
	let formLoop = $state(0);
	let formSenders = $state<string[]>([]);
	let formVars = $state<Array<{ name: string; makerRef: string }>>([]);
	let formSteps = $state<Array<ScenarioStep & { _id: number }>>([]);
	let errors = $state<Record<string, string>>({});

	let stepIdCounter = 0;

	// ── Derived ──────────────────────────────────────────────────────────────────
	const filtered = $derived(
		search.trim()
			? items.filter((i) => i.name.toLowerCase().includes(search.toLowerCase()))
			: items
	);

	const logOptions = $derived(logs.map((l) => ({ value: l.name, label: l.name, sublabel: l.format.slice(0, 40) })));
	const makerOptions = $derived(makers.map((m) => ({ value: m.name, label: m.name, sublabel: m.type })));

	// ── Fetch ────────────────────────────────────────────────────────────────────
	async function fetchItems() {
		loading = true;
		try {
			items = await api.getScenarios();
		} catch {
			/* toast shown */
		} finally {
			loading = false;
		}
	}

	async function fetchSupport() {
		try {
			[logs, makers, senders] = await Promise.all([
				api.getLogs(),
				api.getMakers(),
				api.getSenders()
			]);
		} catch {
			/* ignored */
		}
	}

	// ── Dialog open/close ─────────────────────────────────────────────────────
	function openAdd() {
		editMode = false;
		formName = '';
		formIntervalMin = 1000;
		formIntervalMax = 5000;
		formLoop = 0;
		formSenders = [];
		formVars = [];
		formSteps = [];
		errors = {};
		dialogOpen = true;
		fetchSupport();
	}

	function openEdit(item: Scenario) {
		editMode = true;
		formName = item.name;
		formIntervalMin = item.intervalMinMs;
		formIntervalMax = item.intervalMaxMs;
		formLoop = item.loopCount;
		formSenders = [...item.senders];
		formVars = Object.entries(item.sharedVariables).map(([name, makerRef]) => ({ name, makerRef }));
		formSteps = item.steps.map((s) => ({ ...s, overrides: { ...s.overrides }, _id: stepIdCounter++ }));
		errors = {};
		dialogOpen = true;
		fetchSupport();
	}

	function closeDialog() {
		dialogOpen = false;
		errors = {};
	}

	// ── Variable management ────────────────────────────────────────────────────
	function addVar() {
		formVars = [...formVars, { name: '', makerRef: '' }];
	}

	function removeVar(i: number) {
		formVars = formVars.filter((_, idx) => idx !== i);
	}

	// ── Step management ────────────────────────────────────────────────────────
	function addStep() {
		formSteps = [
			...formSteps,
			{ logRef: '', repeat: 1, delayMinMs: 0, delayMaxMs: 0, overrides: {}, _id: stepIdCounter++ }
		];
	}

	function removeStep(i: number) {
		formSteps = formSteps.filter((_, idx) => idx !== i);
	}

	function moveStep(i: number, dir: -1 | 1) {
		const arr = [...formSteps];
		const target = i + dir;
		if (target < 0 || target >= arr.length) return;
		[arr[i], arr[target]] = [arr[target], arr[i]];
		formSteps = arr;
	}

	// Override helpers
	function getOverrideEntries(step: ScenarioStep & { _id: number }): [string, string][] {
		return Object.entries(step.overrides);
	}

	function addOverride(stepIdx: number) {
		const arr = [...formSteps];
		arr[stepIdx] = { ...arr[stepIdx], overrides: { ...arr[stepIdx].overrides, '': '' } };
		formSteps = arr;
	}

	function updateOverrideKey(stepIdx: number, oldKey: string, newKey: string) {
		const arr = [...formSteps];
		const overrides = { ...arr[stepIdx].overrides };
		const val = overrides[oldKey] ?? '';
		delete overrides[oldKey];
		overrides[newKey] = val;
		arr[stepIdx] = { ...arr[stepIdx], overrides };
		formSteps = arr;
	}

	function updateOverrideVal(stepIdx: number, key: string, val: string) {
		const arr = [...formSteps];
		arr[stepIdx] = { ...arr[stepIdx], overrides: { ...arr[stepIdx].overrides, [key]: val } };
		formSteps = arr;
	}

	function removeOverride(stepIdx: number, key: string) {
		const arr = [...formSteps];
		const overrides = { ...arr[stepIdx].overrides };
		delete overrides[key];
		arr[stepIdx] = { ...arr[stepIdx], overrides };
		formSteps = arr;
	}

	// ── Sender toggle ─────────────────────────────────────────────────────────
	function toggleSender(name: string) {
		if (formSenders.includes(name)) {
			formSenders = formSenders.filter((s) => s !== name);
		} else {
			formSenders = [...formSenders, name];
		}
	}

	// ── Validate ──────────────────────────────────────────────────────────────
	function validate(): boolean {
		errors = {};
		if (!formName.trim()) errors.name = 'Name is required';
		if (formName && !/^[a-z0-9][a-z0-9-]*$/.test(formName))
			errors.name = 'Only lowercase letters, numbers, and hyphens allowed';
		if (formIntervalMin < 0) errors.intervalMin = 'Interval min must be >= 0';
		if (formIntervalMax < formIntervalMin) errors.intervalMax = 'Interval max must be >= min';
		if (formSteps.length === 0) errors.steps = 'At least one step is required';
		for (const s of formSteps) {
			if (!s.logRef) {
				errors.steps = 'All steps must have a log selected';
				break;
			}
			if (s.delayMinMs < 0) {
				errors.steps = 'Step delay min must be >= 0';
				break;
			}
			if (s.delayMaxMs < s.delayMinMs) {
				errors.steps = 'Step delay max must be >= min';
				break;
			}
		}
		return Object.keys(errors).length === 0;
	}

	// ── Submit ────────────────────────────────────────────────────────────────
	async function submit(runAfter = false) {
		if (!validate()) return;
		saving = true;
		saveAndRun = runAfter;
		try {
			const sharedVariables: Record<string, string> = {};
			for (const v of formVars) {
				if (v.name.trim()) sharedVariables[v.name.trim()] = v.makerRef;
			}
			const payload: Partial<Scenario> = {
				name: formName,
				intervalMinMs: formIntervalMin,
				intervalMaxMs: formIntervalMax,
				loopCount: formLoop,
				senders: formSenders,
				sharedVariables,
				steps: formSteps.map(({ _id: _unused, ...s }) => s)
			};
			if (editMode) {
				await api.updateScenario(formName, payload);
			} else {
				await api.createScenario(payload);
			}
			if (runAfter) {
				await api.startScenario(formName);
			}
			closeDialog();
			await fetchItems();
		} catch {
			/* toast shown */
		} finally {
			saving = false;
		}
	}

	// ── Start / Stop ──────────────────────────────────────────────────────────
	async function startScenario(name: string) {
		try {
			await api.startScenario(name);
			await fetchItems();
		} catch {
			/* toast shown */
		}
	}

	async function stopScenario(name: string) {
		try {
			await api.stopScenario(name);
			await fetchItems();
		} catch {
			/* toast shown */
		}
	}

	// ── Delete ────────────────────────────────────────────────────────────────
	function askDelete(name: string) {
		confirmName = name;
		confirmOpen = true;
	}

	async function confirmDelete() {
		confirmLoading = true;
		try {
			await api.deleteScenario(confirmName);
			confirmOpen = false;
			await fetchItems();
		} catch {
			/* toast shown */
		} finally {
			confirmLoading = false;
		}
	}

	// ── Export ────────────────────────────────────────────────────────────────
	async function exportData() {
		const data = await api.getScenarios();
		const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
		const a = document.createElement('a');
		a.href = URL.createObjectURL(blob);
		a.download = 'logmaker-scenario.json';
		a.click();
	}

	// ── Helpers ────────────────────────────────────────────────────────────────
	function formatInterval(minMs: number, maxMs: number): string {
		const minS = (minMs / 1000).toFixed(minMs % 1000 ? 1 : 0);
		const maxS = (maxMs / 1000).toFixed(maxMs % 1000 ? 1 : 0);
		return minMs === maxMs ? `${minS}s` : `${minS}~${maxS}s`;
	}

	// ── Auto-refresh ──────────────────────────────────────────────────────────
	$effect(() => {
		fetchItems();
		const interval = setInterval(fetchItems, 5000);
		return () => clearInterval(interval);
	});
</script>

<svelte:head><title>Scenario — LogMaker</title></svelte:head>

<div class="page">
	<!-- PAGE HEADER -->
	<header class="page-header">
		<div class="header-left">
			<h1 class="page-title">Scenario</h1>
			<span class="item-count">{filtered.length} of {items.length}</span>
			<span class="page-hint">Orchestrate multiple logs into sequenced scenarios with shared variables, repeat counts, and delays.</span>
		</div>
		<div class="header-actions">
			<div class="search-wrap">
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="search-icon" aria-hidden="true"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
				<input class="search-input" type="search" placeholder="Search scenarios…" bind:value={search} aria-label="Search scenarios" />
			</div>
			<button class="btn btn-ghost" onclick={fetchItems} disabled={loading} aria-label="Reload">
				{#if loading}
					<span class="spinner-muted" aria-hidden="true"></span>
				{:else}
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 11-2.12-9.36L23 10"/></svg>
				{/if}
				Reload
			</button>
			<div class="view-toggle" role="radiogroup" aria-label="View mode">
				<button class="toggle-btn" class:active={viewMode === 'grid'} onclick={() => (viewMode = 'grid')} aria-label="Grid view" aria-pressed={viewMode === 'grid'}>
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
				</button>
				<button class="toggle-btn" class:active={viewMode === 'table'} onclick={() => (viewMode = 'table')} aria-label="Table view" aria-pressed={viewMode === 'table'}>
					<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
				</button>
			</div>
			<button class="btn btn-ghost" onclick={exportData} disabled={loading}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
				Export
			</button>
			<button class="btn btn-primary" onclick={openAdd} disabled={loading}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Scenario
			</button>
		</div>
	</header>

	<!-- CONTENT -->
	{#if loading && items.length === 0}
		<div class="loading-state">
			<span class="spinner-muted" aria-hidden="true"></span>
			<span>Loading scenarios…</span>
		</div>
	{:else if items.length === 0}
		<div class="empty-state">
			<div class="empty-state-icon">
				<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="5" cy="12" r="2"/><circle cx="19" cy="5" r="2"/><circle cx="19" cy="19" r="2"/><line x1="7" y1="11.5" x2="17" y2="6.5"/><line x1="7" y1="12.5" x2="17" y2="17.5"/></svg>
			</div>
			<p class="empty-state-title">No scenarios yet</p>
			<p class="empty-state-desc">Create a scenario to orchestrate multiple logs with shared variables and step sequencing.</p>
			<button class="btn btn-primary" onclick={openAdd}>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
				Add Scenario
			</button>
		</div>
	{:else if filtered.length === 0}
		<div class="empty-state">
			<div class="empty-state-icon">
				<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
			</div>
			<p class="empty-state-title">No results for "{search}"</p>
			<p class="empty-state-desc">Try a different search term</p>
		</div>
	{:else if viewMode === 'grid'}
		<!-- GRID VIEW -->
		<div class="scenario-grid" role="list" aria-label="Scenario list">
			{#each filtered as item}
				{@const running = item.status === 'RUNNING'}
				{@const varNames = Object.keys(item.sharedVariables)}
				<div
					class="scenario-card"
					class:running
					role="button"
					tabindex="0"
					onclick={() => openEdit(item)}
					onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && openEdit(item)}
					aria-label="Edit scenario {item.name}"
				>
					<!-- Header -->
					<div class="sc-header">
						<div class="sc-name-block">
							<span class="sc-name">{item.name}</span>
						</div>
						<div class="sc-status" class:running>
							<span class="status-dot" class:pulse={running} aria-hidden="true"></span>
							{running ? 'Running' : 'Stopped'}
						</div>
					</div>

					<!-- Shared vars -->
					{#if varNames.length > 0}
						<div class="sc-vars-row">
							<span class="sc-section-label">Shared</span>
							<div class="sc-chips">
								{#each varNames as v}
									<span class="chip chip-var">{v}</span>
								{/each}
							</div>
						</div>
					{/if}

					<!-- Steps chain -->
					<div class="sc-steps-row">
						<span class="sc-section-label">Steps</span>
						<div class="sc-chain">
							{#each item.steps as step, si}
								{#if si > 0}
									<span class="chain-arrow" aria-hidden="true">→</span>
								{/if}
								<span class="chain-step">
									{step.logRef}{step.repeat > 1 ? ` ×${step.repeat}` : ''}
								</span>
							{/each}
							{#if item.steps.length === 0}
								<span class="chain-empty">No steps</span>
							{/if}
						</div>
					</div>

					<!-- Metrics -->
					<div class="sc-metrics">
						<div class="sc-metric">
							<span class="sc-metric-label">Interval</span>
							<span class="sc-metric-val mono">{formatInterval(item.intervalMinMs, item.intervalMaxMs)}</span>
						</div>
						<div class="sc-metric">
							<span class="sc-metric-label">Loop</span>
							<span class="sc-metric-val mono">{item.loopCount === 0 ? '∞' : item.loopCount}</span>
						</div>
						<div class="sc-metric">
							<span class="sc-metric-label">Count</span>
							<span class="sc-metric-val mono">{item.count.toLocaleString()}</span>
						</div>
					</div>

					<!-- Actions -->
					<div class="sc-actions" role="group" aria-label="Scenario actions">
						{#if running}
							<button
								class="btn btn-ghost btn-sm"
								onclick={(e) => { e.stopPropagation(); stopScenario(item.name); }}
								aria-label="Stop {item.name}"
							>
								<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="6" y="6" width="12" height="12" rx="1"/></svg>
								Stop
							</button>
						{:else}
							<button
								class="btn btn-ghost btn-sm start-btn"
								onclick={(e) => { e.stopPropagation(); startScenario(item.name); }}
								aria-label="Start {item.name}"
							>
								<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polygon points="5 3 19 12 5 21 5 3"/></svg>
								Start
							</button>
						{/if}
						<button
							class="icon-btn"
							onclick={(e) => { e.stopPropagation(); openEdit(item); }}
							title="Edit"
							aria-label="Edit {item.name}"
						>
							<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
						</button>
						<button
							class="icon-btn danger"
							onclick={(e) => { e.stopPropagation(); askDelete(item.name); }}
							disabled={running}
							title={running ? 'Stop before deleting' : 'Delete'}
							aria-label="Delete {item.name}"
						>
							<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
						</button>
					</div>
				</div>
			{/each}
		</div>
	{:else}
		<!-- TABLE VIEW -->
		<div class="table-wrap">
			<table class="table" aria-label="Scenario list">
				<thead>
					<tr>
						<th>Name</th>
						<th>Status</th>
						<th>Steps</th>
						<th>Vars</th>
						<th>EPS</th>
						<th>Count</th>
						<th class="right">Actions</th>
					</tr>
				</thead>
				<tbody>
					{#each filtered as item}
						{@const running = item.status === 'RUNNING'}
						<tr
							class="table-row-clickable"
							onclick={() => openEdit(item)}
							onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && openEdit(item)}
							tabindex="0"
							role="button"
							aria-label="Edit scenario {item.name}"
						>
							<td><span class="tbl-name mono">{item.name}</span></td>
							<td>
								<div class="tbl-status" class:running>
									<span class="status-dot" class:pulse={running} aria-hidden="true"></span>
									{running ? 'Running' : 'Stopped'}
								</div>
							</td>
							<td>
								<div class="tbl-chain">
									{#each item.steps as step, si}
										{#if si > 0}<span class="chain-arrow-sm" aria-hidden="true">→</span>{/if}
										<span class="tbl-step">{step.logRef}{step.repeat > 1 ? ` ×${step.repeat}` : ''}</span>
									{/each}
									{#if item.steps.length === 0}<span class="text-muted">—</span>{/if}
								</div>
							</td>
							<td>
								<span class="mono text-secondary">{Object.keys(item.sharedVariables).join(', ') || '—'}</span>
							</td>
							<td>
								<span class="mono">{item.currentEps.toLocaleString()}<span class="text-muted">/{item.eps}</span></span>
							</td>
							<td><span class="mono">{item.count.toLocaleString()}</span></td>
							<td class="right">
								<div class="row-actions">
									{#if running}
										<button class="btn btn-ghost btn-sm" onclick={(e) => { e.stopPropagation(); stopScenario(item.name); }} aria-label="Stop {item.name}">
											<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="6" y="6" width="12" height="12" rx="1"/></svg>
											Stop
										</button>
									{:else}
										<button class="btn btn-ghost btn-sm start-btn" onclick={(e) => { e.stopPropagation(); startScenario(item.name); }} aria-label="Start {item.name}">
											<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polygon points="5 3 19 12 5 21 5 3"/></svg>
											Start
										</button>
									{/if}
									<button class="icon-btn" onclick={(e) => { e.stopPropagation(); openEdit(item); }} title="Edit" aria-label="Edit {item.name}">
										<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
									</button>
									<button class="icon-btn danger" onclick={(e) => { e.stopPropagation(); askDelete(item.name); }} disabled={running} title={running ? 'Stop before deleting' : 'Delete'} aria-label="Delete {item.name}">
										<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2"/></svg>
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

<!-- ══════════════════════════════════════════════════════════
     ADD / EDIT DIALOG
═══════════════════════════════════════════════════════════ -->
{#if dialogOpen}
	<div class="overlay" role="presentation" onclick={closeDialog}>
		<div
			class="dialog wide"
			role="dialog"
			aria-modal="true"
			aria-labelledby="scenario-dialog-title"
			onclick={(e) => e.stopPropagation()}
			onkeydown={(e) => e.key === 'Escape' && closeDialog()}
			tabindex="-1"
		>
			<!-- Dialog header -->
			<div class="dialog-header">
				<h2 id="scenario-dialog-title" class="dialog-title">
					{editMode ? 'Edit Scenario' : 'Add Scenario'}
				</h2>
				<button class="close-btn" onclick={closeDialog} aria-label="Close dialog">
					<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
				</button>
			</div>

			<!-- Dialog body -->
			<div class="dialog-body">

				<!-- Section 1: Basic config row -->
				<div class="form-section">
					<div class="basic-row">
						<div class="field" style="flex:2">
							<label class="field-label" for="s-name">
								Name <span class="required" aria-hidden="true">*</span>
							</label>
							<input
								id="s-name"
								class="input"
								class:input-error={errors.name}
								type="text"
								placeholder="web-access-flow"
								bind:value={formName}
								disabled={editMode}
							/>
							{#if errors.name}
								<span class="field-error" role="alert">{errors.name}</span>
							{/if}
						</div>
						<div class="field" style="flex:1">
							<label class="field-label" for="s-loop">Loop</label>
							<input
								id="s-loop"
								class="input"
								type="number"
								min="0"
								placeholder="0 = infinite"
								bind:value={formLoop}
							/>
						</div>
						<div class="field" style="flex:1">
							<label class="field-label">INTERVAL</label>
							<div style="display:flex;align-items:center;gap:4px">
								<input class="input" type="number" min="0" placeholder="1000" bind:value={formIntervalMin} style="flex:1" />
								<span style="color:var(--text-muted);font-size:0.75rem">~</span>
								<input class="input" type="number" min="0" placeholder="5000" bind:value={formIntervalMax} style="flex:1" />
								<span style="color:var(--text-muted);font-size:0.75rem">ms</span>
							</div>
						</div>
					</div>
				</div>

				<!-- Section 2: Shared Variables -->
				<div class="form-section">
					<div class="section-header">
						<div class="args-divider">Shared Variables</div>
						<button class="btn btn-ghost btn-sm" type="button" onclick={addVar}>
							<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
							Add Variable
						</button>
					</div>
					{#if formVars.length === 0}
						<p class="section-empty">No shared variables. Variables are available across all steps as <code class="mono">${'{'}varName{'}'}</code>.</p>
					{:else}
						<div class="var-list">
							{#each formVars as v, i}
								<div class="var-row">
									<input
										class="input var-name-input"
										type="text"
										placeholder="variable_name"
										bind:value={v.name}
										aria-label="Variable name {i + 1}"
									/>
									<span class="var-eq" aria-hidden="true">=</span>
									<div class="var-maker-select">
										<Select
											value={v.makerRef}
											options={makerOptions}
											placeholder="Select maker…"
											onchange={(val) => { formVars[i] = { ...formVars[i], makerRef: val }; }}
										/>
									</div>
									<button class="icon-btn danger" type="button" onclick={() => removeVar(i)} aria-label="Remove variable {i + 1}">
										<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
									</button>
								</div>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Section 3: Steps -->
				<div class="form-section">
					<div class="section-header">
						<div class="args-divider">Steps</div>
						<button class="btn btn-ghost btn-sm" type="button" onclick={addStep}>
							<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
							Add Step
						</button>
					</div>
					{#if errors.steps}
						<span class="field-error" role="alert">{errors.steps}</span>
					{/if}
					{#if formSteps.length === 0}
						<p class="section-empty">No steps yet. Each step references a log and runs it in sequence.</p>
					{:else}
						<div class="step-list">
							{#each formSteps as step, i}
								<div class="step-card" aria-label="Step {i + 1}">
									<div class="step-card-header">
										<span class="step-label">Step {i + 1}</span>
										<div class="step-move-btns">
											<button class="icon-btn" type="button" onclick={() => moveStep(i, -1)} disabled={i === 0} aria-label="Move step up">
												<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><polyline points="18 15 12 9 6 15"/></svg>
											</button>
											<button class="icon-btn" type="button" onclick={() => moveStep(i, 1)} disabled={i === formSteps.length - 1} aria-label="Move step down">
												<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><polyline points="6 9 12 15 18 9"/></svg>
											</button>
										</div>
										<button class="icon-btn danger" type="button" onclick={() => removeStep(i)} aria-label="Remove step {i + 1}">
											<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
										</button>
									</div>
									<div class="step-fields-row">
										<div class="field step-log-field">
											<label class="field-label" for="step-log-{i}">Log <span class="required" aria-hidden="true">*</span></label>
											<Select
												id="step-log-{i}"
												value={step.logRef}
												options={logOptions}
												placeholder="Select log…"
												onchange={(val) => { formSteps[i] = { ...formSteps[i], logRef: val }; }}
											/>
										</div>
										<div class="field step-num-field">
											<label class="field-label" for="step-repeat-{i}">Repeat</label>
											<input
												id="step-repeat-{i}"
												class="input"
												type="number"
												min="1"
												bind:value={step.repeat}
											/>
										</div>
										<div class="field step-num-field">
											<label class="field-label" for="step-delay-{i}">Delay (ms)</label>
											<input
												id="step-delay-{i}"
												class="input"
												type="number"
												min="0"
												bind:value={step.delayMs}
											/>
										</div>
									</div>

									<!-- Overrides sub-section -->
									<div class="overrides-section">
										<div class="overrides-header">
											<span class="overrides-label">Overrides</span>
											<button class="btn btn-ghost btn-sm" type="button" onclick={() => addOverride(i)}>
												<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
												Add
											</button>
										</div>
										{#if getOverrideEntries(step).length > 0}
											<div class="override-list">
												{#each getOverrideEntries(step) as [key, val], oi}
													<div class="override-row">
														<input
															class="input override-key-input mono"
															type="text"
															placeholder="format_var"
															value={key}
															onchange={(e) => updateOverrideKey(i, key, (e.target as HTMLInputElement).value)}
															aria-label="Override key {oi + 1}"
														/>
														<span class="override-arrow" aria-hidden="true">→</span>
														<input
															class="input override-val-input mono"
															type="text"
															placeholder="${'$'}{'{'}varName{'}'}"
															value={val}
															oninput={(e) => updateOverrideVal(i, key, (e.target as HTMLInputElement).value)}
															aria-label="Override value {oi + 1}"
														/>
														<button class="icon-btn danger" type="button" onclick={() => removeOverride(i, key)} aria-label="Remove override {oi + 1}">
															<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
														</button>
													</div>
												{/each}
											</div>
										{:else}
											<p class="overrides-hint">No overrides — shared variables are used directly in log format.</p>
										{/if}
									</div>
								</div>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Section 4: Senders -->
				<div class="form-section">
					<div class="args-divider">Senders</div>
					{#if senders.length === 0}
						<p class="section-empty">No senders available. <a href="/sender" class="link">Create a sender</a> first.</p>
					{:else}
						<div class="sender-chips" role="group" aria-label="Select senders">
							{#each senders as s}
								<button
									type="button"
									class="sender-chip"
									class:selected={formSenders.includes(s.name)}
									onclick={() => toggleSender(s.name)}
									aria-pressed={formSenders.includes(s.name)}
								>
									{#if formSenders.includes(s.name)}
										<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" aria-hidden="true"><polyline points="20 6 9 17 4 12"/></svg>
									{/if}
									{s.name}
								</button>
							{/each}
						</div>
					{/if}
				</div>
			</div>

			<!-- Dialog footer -->
			<div class="dialog-footer">
				<button class="btn btn-ghost" onclick={closeDialog} disabled={saving}>Cancel</button>
				<button class="btn btn-ghost" onclick={() => submit(false)} disabled={saving}>
					{#if saving && !saveAndRun}
						<span class="spinner" aria-hidden="true"></span>
					{/if}
					Save
				</button>
				<button class="btn btn-primary" onclick={() => submit(true)} disabled={saving}>
					{#if saving && saveAndRun}
						<span class="spinner" aria-hidden="true"></span>
					{/if}
					<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><polygon points="5 3 19 12 5 21 5 3"/></svg>
					Save & Run
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- Confirm delete -->
<ConfirmDialog
	open={confirmOpen}
	title="Delete Scenario"
	message="Delete '{confirmName}'? This cannot be undone."
	confirmLabel="Delete"
	loading={confirmLoading}
	onconfirm={confirmDelete}
	oncancel={() => (confirmOpen = false)}
/>

<style>
	/* ── Loading state ──────────────────────────────────────────── */
	.loading-state {
		display: flex;
		align-items: center;
		gap: 0.625rem;
		padding: 3rem 0;
		color: var(--text-muted);
		font-size: 0.875rem;
		justify-content: center;
	}

	/* ── Header layout ──────────────────────────────────────────── */
	.header-left {
		display: flex;
		align-items: baseline;
		gap: 0.625rem;
		flex-wrap: wrap;
	}

	.item-count {
		font-size: 0.75rem;
		color: var(--text-muted);
		font-family: var(--font-mono);
	}

	/* ── Search ─────────────────────────────────────────────────── */
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
		padding: 0.4375rem 0.75rem 0.4375rem 2rem;
		background: var(--bg-base);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-primary);
		font-size: 0.8125rem;
		font-family: var(--font-ui);
		width: 200px;
		transition: border-color 0.15s;
	}

	.search-input:focus {
		outline: none;
		border-color: var(--border-focus);
		box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 18%, transparent);
	}

	/* ── Scenario card grid ─────────────────────────────────────── */
	.scenario-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
		gap: 1rem;
	}

	.scenario-card {
		background: var(--bg-surface);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		padding: 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		cursor: pointer;
		transition: border-color 0.15s, box-shadow 0.15s;
		position: relative;
		overflow: hidden;
	}

	.scenario-card::before {
		content: '';
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		height: 2px;
		background: var(--border);
		transition: background 0.2s;
	}

	.scenario-card.running::before {
		background: var(--success);
	}

	.scenario-card:hover {
		border-color: color-mix(in srgb, var(--accent) 40%, transparent);
		box-shadow: var(--shadow-md);
	}

	.scenario-card:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}

	/* Card header */
	.sc-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
	}

	.sc-name-block {
		display: flex;
		flex-direction: column;
		min-width: 0;
	}

	.sc-name {
		font-size: 0.9375rem;
		font-weight: 600;
		font-family: var(--font-mono);
		color: var(--text-primary);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.sc-status {
		display: flex;
		align-items: center;
		gap: 0.375rem;
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-muted);
		flex-shrink: 0;
	}

	.sc-status.running {
		color: var(--success);
	}

	/* Status dot */
	.status-dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--text-muted);
		flex-shrink: 0;
	}

	.sc-status.running .status-dot {
		background: var(--success);
	}

	.tbl-status.running .status-dot {
		background: var(--success);
	}

	.status-dot.pulse {
		animation: pulse-ring 1.4s ease-out infinite;
	}

	/* Shared vars row */
	.sc-vars-row,
	.sc-steps-row {
		display: flex;
		align-items: flex-start;
		gap: 0.5rem;
	}

	.sc-section-label {
		font-size: 0.6875rem;
		font-weight: 600;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.07em;
		white-space: nowrap;
		padding-top: 2px;
		flex-shrink: 0;
	}

	.sc-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 0.25rem;
	}

	.chip-var {
		display: inline-block;
		padding: 0.125rem 0.4375rem;
		background: var(--info-light);
		color: var(--info);
		border-radius: var(--radius-sm);
		font-size: 0.6875rem;
		font-weight: 600;
		font-family: var(--font-mono);
	}

	/* Step chain */
	.sc-chain {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.25rem;
		min-width: 0;
	}

	.chain-step {
		display: inline-block;
		padding: 0.125rem 0.4375rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-size: 0.6875rem;
		font-family: var(--font-mono);
		color: var(--text-primary);
		white-space: nowrap;
	}

	.chain-arrow {
		color: var(--text-muted);
		font-size: 0.75rem;
	}

	.chain-empty {
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	/* Table chain */
	.tbl-chain {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.1875rem;
	}

	.chain-arrow-sm {
		color: var(--text-muted);
		font-size: 0.6875rem;
	}

	.tbl-step {
		font-size: 0.75rem;
		font-family: var(--font-mono);
		color: var(--text-secondary);
	}

	/* Metrics row */
	.sc-metrics {
		display: flex;
		gap: 1rem;
	}

	.sc-metric {
		display: flex;
		flex-direction: column;
		gap: 1px;
	}

	.sc-metric-label {
		font-size: 0.625rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		color: var(--text-muted);
	}

	.sc-metric-val {
		font-size: 0.875rem;
		font-weight: 600;
		color: var(--text-primary);
	}

	.sc-metric-target {
		font-weight: 400;
		color: var(--text-muted);
	}

	/* EPS bar */
	.sc-bar-row {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.sc-bar {
		flex: 1;
		height: 4px;
		background: var(--bg-raised);
		border-radius: 2px;
		overflow: hidden;
	}

	.sc-bar-fill {
		height: 100%;
		background: var(--accent);
		border-radius: 2px;
		transition: width 0.4s ease;
	}

	.sc-bar-fill.live {
		background: var(--success);
	}

	.sc-bar-pct {
		font-size: 0.6875rem;
		color: var(--text-muted);
		width: 2.5rem;
		text-align: right;
		flex-shrink: 0;
	}

	/* Card actions */
	.sc-actions {
		display: flex;
		align-items: center;
		gap: 0.25rem;
		justify-content: flex-end;
		padding-top: 0.25rem;
		border-top: 1px solid var(--border);
	}

	.start-btn {
		color: var(--success);
		border-color: color-mix(in srgb, var(--success) 30%, transparent);
	}

	.start-btn:hover:not(:disabled) {
		background: var(--success-light);
		color: var(--success);
		border-color: color-mix(in srgb, var(--success) 40%, transparent);
	}

	/* Table status */
	.tbl-status {
		display: flex;
		align-items: center;
		gap: 0.375rem;
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	.tbl-status.running {
		color: var(--success);
	}

	/* Table row clickable */
	:global(.table-row-clickable) {
		cursor: pointer;
	}

	/* Table name */
	.tbl-name {
		font-weight: 600;
		color: var(--text-primary);
	}

	/* ── Dialog form sections ─────────────────────────────────── */
	.form-section {
		margin-bottom: 1rem;
	}

	.form-section:last-child {
		margin-bottom: 0;
	}

	.section-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		margin-bottom: 0.75rem;
	}

	.section-header .args-divider {
		margin: 0;
		flex: 1;
	}

	.section-empty {
		font-size: 0.8125rem;
		color: var(--text-muted);
		margin: 0;
		padding: 0.5rem 0;
	}

	.link {
		color: var(--accent);
		text-decoration: none;
	}

	.link:hover {
		text-decoration: underline;
	}

	/* ── Shared variable rows ────────────────────────────────── */
	.var-list {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.var-row {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.var-name-input {
		width: 160px;
		flex-shrink: 0;
		font-family: var(--font-mono);
		font-size: 0.8125rem;
	}

	.var-eq {
		color: var(--text-muted);
		font-weight: 600;
		flex-shrink: 0;
	}

	.var-maker-select {
		flex: 1;
		min-width: 0;
	}

	/* ── Step cards ──────────────────────────────────────────── */
	.step-list {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	.step-card {
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		padding: 0.875rem;
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}

	.step-card-header {
		display: flex;
		align-items: center;
		gap: 0.375rem;
	}

	.step-label {
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.06em;
		flex: 1;
	}

	.step-move-btns {
		display: flex;
		gap: 2px;
	}

	.step-fields-row {
		display: flex;
		gap: 0.75rem;
		align-items: flex-start;
	}

	.step-log-field {
		flex: 2;
		margin-bottom: 0;
	}

	.step-num-field {
		flex: 1;
		margin-bottom: 0;
	}

	/* ── Overrides ───────────────────────────────────────────── */
	.overrides-section {
		border-top: 1px solid var(--border);
		padding-top: 0.625rem;
	}

	.overrides-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 0.5rem;
	}

	.overrides-label {
		font-size: 0.6875rem;
		font-weight: 600;
		color: var(--text-muted);
		text-transform: uppercase;
		letter-spacing: 0.07em;
	}

	.overrides-hint {
		font-size: 0.75rem;
		color: var(--text-muted);
		margin: 0;
	}

	.override-list {
		display: flex;
		flex-direction: column;
		gap: 0.375rem;
	}

	.override-row {
		display: flex;
		align-items: center;
		gap: 0.375rem;
	}

	.override-key-input {
		width: 130px;
		flex-shrink: 0;
		font-size: 0.8125rem;
	}

	.override-arrow {
		color: var(--text-muted);
		flex-shrink: 0;
		font-size: 0.875rem;
	}

	.override-val-input {
		flex: 1;
		min-width: 0;
		font-size: 0.8125rem;
	}

	/* ── Sender chips ────────────────────────────────────────── */
	.sender-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 0.375rem;
	}

	.sender-chip {
		display: inline-flex;
		align-items: center;
		gap: 0.3125rem;
		padding: 0.3125rem 0.75rem;
		border-radius: var(--radius-sm);
		font-size: 0.8125rem;
		font-weight: 500;
		font-family: var(--font-ui);
		background: var(--bg-raised);
		border: 1px solid var(--border);
		color: var(--text-secondary);
		cursor: pointer;
		transition: background 0.12s, border-color 0.12s, color 0.12s;
	}

	.sender-chip:hover {
		background: var(--bg-base);
		color: var(--text-primary);
	}

	.sender-chip.selected {
		background: var(--accent-light);
		border-color: color-mix(in srgb, var(--accent) 40%, transparent);
		color: var(--accent);
	}

	/* ── Responsive ──────────────────────────────────────────── */
	@media (max-width: 640px) {
		.scenario-grid {
			grid-template-columns: 1fr;
		}

		.step-fields-row {
			flex-direction: column;
		}

		.step-log-field,
		.step-num-field {
			width: 100%;
		}

		.search-input {
			width: 140px;
		}
	}
</style>
