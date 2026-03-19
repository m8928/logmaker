<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		text = '',
		position = 'top',
		children
	}: {
		text?: string;
		position?: 'top' | 'bottom' | 'left' | 'right';
		children: Snippet;
	} = $props();

	let visible = $state(false);
	let x = $state(0);
	let y = $state(0);
	let tooltipEl: HTMLDivElement | undefined = $state();
	let triggerEl: HTMLDivElement | undefined = $state();

	function show() {
		if (!text) return;
		visible = true;
	}

	function hide() {
		visible = false;
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
	{#if visible && text}
		<div
			bind:this={tooltipEl}
			class="tooltip tooltip-{position}"
			role="tooltip"
		>
			<div class="tooltip-content">{text}</div>
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
		animation: tooltip-in 0.15s ease;
	}

	.tooltip-content {
		background: var(--text-primary);
		color: var(--bg-base);
		font-size: 0.75rem;
		line-height: 1.5;
		padding: 0.5rem 0.75rem;
		border-radius: var(--radius-sm);
		white-space: pre-wrap;
		word-break: break-all;
		max-width: 400px;
		box-shadow: var(--shadow-md);
		font-family: var(--font-mono);
	}

	.tooltip-top {
		bottom: calc(100% + 6px);
		left: 50%;
		transform: translateX(-50%);
	}

	.tooltip-bottom {
		top: calc(100% + 6px);
		left: 50%;
		transform: translateX(-50%);
	}

	.tooltip-left {
		right: calc(100% + 6px);
		top: 50%;
		transform: translateY(-50%);
	}

	.tooltip-right {
		left: calc(100% + 6px);
		top: 50%;
		transform: translateY(-50%);
	}

	@keyframes tooltip-in {
		from { opacity: 0; transform: translateX(-50%) translateY(4px); }
		to { opacity: 1; transform: translateX(-50%) translateY(0); }
	}

	.tooltip-bottom {
		animation-name: tooltip-in-bottom;
	}

	@keyframes tooltip-in-bottom {
		from { opacity: 0; transform: translateX(-50%) translateY(-4px); }
		to { opacity: 1; transform: translateX(-50%) translateY(0); }
	}
</style>
