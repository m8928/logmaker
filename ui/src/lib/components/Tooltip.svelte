<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title = '',
		text = '',
		position = 'top',
		children
	}: {
		title?: string;
		text?: string;
		position?: 'top' | 'bottom' | 'left' | 'right';
		children: Snippet;
	} = $props();

	let visible = $state(false);
	let tipX = $state(0);
	let tipY = $state(0);
	let triggerEl: HTMLDivElement | undefined = $state();

	function show() {
		if (!text && !title) return;
		if (triggerEl) {
			const rect = triggerEl.getBoundingClientRect();
			if (position === 'top') {
				tipX = rect.left + rect.width / 2;
				tipY = rect.top - 4;
			} else if (position === 'bottom') {
				tipX = rect.left + rect.width / 2;
				tipY = rect.bottom + 4;
			} else if (position === 'left') {
				tipX = rect.left - 4;
				tipY = rect.top + rect.height / 2;
			} else {
				tipX = rect.right + 4;
				tipY = rect.top + rect.height / 2;
			}
		}
		visible = true;
	}

	function hide() {
		visible = false;
	}

	// Parse "key: value\nkey2: value2" into structured entries
	function parseEntries(t: string): Array<{key: string; val: string}> | null {
		if (!t || !t.includes(':')) return null;
		const lines = t.split('\n').filter(l => l.includes(':'));
		if (lines.length === 0) return null;
		return lines.map(l => {
			const idx = l.indexOf(':');
			return { key: l.slice(0, idx).trim(), val: l.slice(idx + 1).trim() };
		});
	}
</script>

<div
	class="tooltip-trigger"
	role="group"
	bind:this={triggerEl}
	onmouseenter={show}
	onmouseleave={hide}
	onfocusin={() => show()}
	onfocusout={() => hide()}
>
	{@render children()}
</div>

{#if visible && (text || title)}
	{@const entries = parseEntries(text)}
	<div
		class="tooltip tooltip-{position}"
		role="tooltip"
		style="
			position: fixed;
			{position === 'top' ? `bottom: ${window.innerHeight - tipY}px; left: ${tipX}px; transform: translateX(-50%);` : ''}
			{position === 'bottom' ? `top: ${tipY}px; left: ${tipX}px; transform: translateX(-50%);` : ''}
			{position === 'left' ? `right: ${window.innerWidth - tipX}px; top: ${tipY}px; transform: translateY(-50%);` : ''}
			{position === 'right' ? `left: ${tipX}px; top: ${tipY}px; transform: translateY(-50%);` : ''}
		"
	>
		<div class="tooltip-card">
			{#if title}
				<div class="tooltip-title">{title}</div>
			{/if}
			{#if entries}
				<div class="tooltip-entries">
					{#each entries as e}
						<span class="entry-key">{e.key}</span>
						<span class="entry-val">{e.val}</span>
					{/each}
				</div>
			{:else if text}
				<div class="tooltip-body">{text}</div>
			{/if}
		</div>
	</div>
{/if}

<style>
	.tooltip-trigger {
		position: relative;
		display: inline-flex;
	}

	.tooltip {
		z-index: 9999;
		pointer-events: none;
		animation: tooltip-fade 0.12s ease;
	}

	.tooltip-card {
		background: var(--bg-surface);
		color: var(--text-primary);
		border: 1px solid var(--border);
		font-size: 0.75rem;
		line-height: 1.4;
		padding: 0;
		border-radius: var(--radius-sm);
		min-width: 140px;
		max-width: 320px;
		box-shadow: 0 4px 16px rgba(0,0,0,0.25);
		overflow: hidden;
	}

	.tooltip-title {
		padding: 0.1875rem 0.5rem;
		font-weight: 700;
		font-size: 0.6875rem;
		color: var(--accent);
		background: var(--bg-raised);
		border-bottom: 1px solid var(--border);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.tooltip-entries {
		display: grid;
		grid-template-columns: auto 1fr;
		align-items: baseline;
		gap: 0 0.5rem;
		padding: 0.125rem 0.5rem 0.1875rem;
		font-size: 0.6875rem;
		line-height: 1.5;
	}

	.entry-key {
		color: var(--text-muted);
		white-space: nowrap;
		text-transform: uppercase;
		font-weight: 600;
		font-size: 0.625rem;
		letter-spacing: 0.04em;
	}

	.entry-val {
		color: var(--text-primary);
		font-family: var(--font-mono);
		word-break: break-all;
	}

	.tooltip-body {
		padding: 0.25rem 0.5rem;
		white-space: pre-wrap;
		word-break: break-word;
		color: var(--text-secondary);
		font-size: 0.6875rem;
	}

	@keyframes tooltip-fade {
		from { opacity: 0; }
		to { opacity: 1; }
	}
</style>
