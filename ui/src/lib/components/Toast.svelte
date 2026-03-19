<script lang="ts">
	import { getToasts, removeToast } from '$lib/stores/toast.svelte';

	const icons = {
		success: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>`,
		error: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>`,
		warning: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`,
		info: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>`
	};

	const colors = {
		success: 'bg-[var(--success-light)] border-[var(--success)] text-[var(--success)]',
		error: 'bg-[var(--danger-light)] border-[var(--danger)] text-[var(--danger)]',
		warning: 'bg-[var(--warning-light)] border-[var(--warning)] text-[var(--warning)]',
		info: 'bg-[var(--info-light)] border-[var(--info)] text-[var(--info)]'
	};
</script>

<div class="fixed top-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none">
	{#each getToasts() as toast (toast.id)}
		<div
			class="pointer-events-auto flex items-start gap-3 px-4 py-3 rounded-[var(--radius-md)] border shadow-[var(--shadow-md)] min-w-[280px] max-w-[420px] animate-slide-in {colors[toast.type]}"
			role="alert"
		>
			<span class="mt-0.5 shrink-0">{@html icons[toast.type]}</span>
			<p class="flex-1 text-[var(--text-primary)] text-sm leading-snug">{toast.message}</p>
			<button
				onclick={() => removeToast(toast.id)}
				class="shrink-0 opacity-50 hover:opacity-100 transition-opacity text-[var(--text-secondary)]"
				aria-label="Dismiss"
			>
				<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
					<line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
				</svg>
			</button>
		</div>
	{/each}
</div>

<style>
	@keyframes slide-in {
		from {
			opacity: 0;
			transform: translateX(16px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}
	.animate-slide-in {
		animation: slide-in 0.2s ease-out;
	}
</style>
