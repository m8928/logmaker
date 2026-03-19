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

	function show() {
		if (!text && !title) return;
		visible = true;
	}

	function hide() {
		visible = false;
	}
</script>

<div
	class="tooltip-trigger"
	role="group"
	onmouseenter={show}
	onmouseleave={hide}
	onfocusin={() => show()}
	onfocusout={() => hide()}
>
	{@render children()}
	{#if visible && (text || title)}
		<div class="tooltip tooltip-{position}" role="tooltip">
			<div class="tooltip-card">
				{#if title}
					<div class="tooltip-title">{title}</div>
				{/if}
				{#if text}
					<div class="tooltip-body">{text}</div>
				{/if}
			</div>
		</div>
	{/if}
</div>

<style>
	.tooltip-trigger {
		position: relative;
		display: inline-flex;
	}

	.tooltip {
		position: absolute;
		z-index: 1000;
		pointer-events: none;
		animation: tooltip-fade 0.12s ease;
	}

	.tooltip-card {
		background: var(--bg-surface);
		color: var(--text-primary);
		border: 1px solid var(--border);
		font-size: 0.75rem;
		line-height: 1.6;
		padding: 0;
		border-radius: var(--radius-md);
		min-width: 180px;
		max-width: 360px;
		box-shadow: var(--shadow-lg);
		overflow: hidden;
	}

	.tooltip-title {
		padding: 0.5rem 0.75rem;
		font-weight: 700;
		font-size: 0.8125rem;
		color: var(--accent);
		background: var(--bg-raised);
		border-bottom: 1px solid var(--border);
		font-family: var(--font-mono);
	}

	.tooltip-body {
		padding: 0.5rem 0.75rem;
		white-space: pre-wrap;
		word-break: break-word;
		color: var(--text-secondary);
		font-family: var(--font-mono);
		font-size: 0.6875rem;
		line-height: 1.7;
	}

	/* No title → compact single-section */
	.tooltip-card:not(:has(.tooltip-title)) .tooltip-body {
		font-size: 0.75rem;
		color: var(--text-primary);
	}

	.tooltip-top {
		bottom: calc(100% + 8px);
		left: 50%;
		transform: translateX(-50%);
	}

	.tooltip-bottom {
		top: calc(100% + 8px);
		left: 50%;
		transform: translateX(-50%);
	}

	.tooltip-left {
		right: calc(100% + 8px);
		top: 50%;
		transform: translateY(-50%);
	}

	.tooltip-right {
		left: calc(100% + 8px);
		top: 50%;
		transform: translateY(-50%);
	}

	@keyframes tooltip-fade {
		from { opacity: 0; }
		to { opacity: 1; }
	}
</style>
