<script lang="ts">
	interface Props {
		name: string;
		type: string;
		value: string | number | boolean | string[];
		required?: boolean;
		onchange: (name: string, value: string | number | boolean | string[]) => void;
	}

	let { name, type, value, required = false, onchange }: Props = $props();

	// For ArrayList tag input
	let tagInput = $state('');

	function addTag() {
		if (!tagInput.trim()) return;
		const arr = Array.isArray(value) ? [...value] : [];
		if (!arr.includes(tagInput.trim())) {
			onchange(name, [...arr, tagInput.trim()]);
		}
		tagInput = '';
	}

	function removeTag(tag: string) {
		if (!Array.isArray(value)) return;
		onchange(
			name,
			value.filter((t) => t !== tag)
		);
	}

	function handleTagKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter') {
			e.preventDefault();
			addTag();
		}
	}

	const isNumber = $derived(type === 'java.lang.Integer' || type === 'java.lang.Long' || type === 'java.lang.Number');
	const isBoolean = $derived(type === 'java.lang.Boolean');
	const isList = $derived(type === 'java.util.ArrayList');
	const isString = $derived(type === 'java.lang.String' || (!isNumber && !isBoolean && !isList));
</script>

<div class="field">
	<label class="field-label" for="input-{name}">
		{name.toUpperCase()}
		{#if required}<span class="required">*</span>{/if}
	</label>

	{#if isString}
		<input
			id="input-{name}"
			class="input"
			type="text"
			value={String(value ?? '')}
			oninput={(e) => onchange(name, (e.target as HTMLInputElement).value)}
		/>
	{:else if isNumber}
		<input
			id="input-{name}"
			class="input"
			type="number"
			value={Number(value ?? 0)}
			oninput={(e) => onchange(name, Number((e.target as HTMLInputElement).value))}
		/>
	{:else if isBoolean}
		<label class="toggle-wrap">
			<input
				id="input-{name}"
				type="checkbox"
				class="sr-only"
				checked={Boolean(value)}
				onchange={(e) => onchange(name, (e.target as HTMLInputElement).checked)}
			/>
			<span class="toggle" class:on={Boolean(value)}>
				<span class="toggle-thumb"></span>
			</span>
			<span class="toggle-label">{Boolean(value) ? 'Enabled' : 'Disabled'}</span>
		</label>
	{:else if isList}
		<div class="tag-input-wrap">
			{#if Array.isArray(value) && value.length > 0}
				<div class="tags">
					{#each value as tag}
						<span class="tag">
							{tag}
							<button type="button" class="tag-remove" onclick={() => removeTag(tag)} aria-label="Remove {tag}">
								<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
									<line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
								</svg>
							</button>
						</span>
					{/each}
				</div>
			{/if}
			<div class="tag-row">
				<input
					class="input"
					type="text"
					placeholder="Add value, press Enter"
					bind:value={tagInput}
					onkeydown={handleTagKeydown}
				/>
				<button type="button" class="btn-add" onclick={addTag}>Add</button>
			</div>
		</div>
	{/if}
</div>

<style>
	.field {
		display: flex;
		flex-direction: column;
		gap: 0.375rem;
		margin-bottom: 1rem;
	}

	.field-label {
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-secondary);
		letter-spacing: 0.04em;
	}

	.required {
		color: var(--danger);
		margin-left: 2px;
	}

	.input {
		width: 100%;
		padding: 0.5rem 0.75rem;
		background: var(--bg-raised);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		color: var(--text-primary);
		font-size: 0.875rem;
		font-family: inherit;
		transition:
			border-color 0.15s,
			box-shadow 0.15s;
	}

	.input:focus {
		outline: none;
		border-color: var(--border-focus);
		box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 15%, transparent);
	}

	.toggle-wrap {
		display: flex;
		align-items: center;
		gap: 0.625rem;
		cursor: pointer;
	}

	.toggle {
		position: relative;
		width: 40px;
		height: 22px;
		background: var(--border);
		border-radius: 11px;
		transition: background 0.2s;
	}

	.toggle.on {
		background: var(--accent);
	}

	.toggle-thumb {
		position: absolute;
		top: 3px;
		left: 3px;
		width: 16px;
		height: 16px;
		background: white;
		border-radius: 50%;
		transition: transform 0.2s;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
	}

	.toggle.on .toggle-thumb {
		transform: translateX(18px);
	}

	.toggle-label {
		font-size: 0.875rem;
		color: var(--text-secondary);
	}

	.tag-input-wrap {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.tags {
		display: flex;
		flex-wrap: wrap;
		gap: 0.375rem;
	}

	.tag {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		padding: 0.2rem 0.5rem 0.2rem 0.625rem;
		background: var(--accent-light);
		color: var(--accent);
		border-radius: 100px;
		font-size: 0.8125rem;
		font-weight: 500;
	}

	.tag-remove {
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
		display: flex;
		align-items: center;
		color: var(--accent);
		opacity: 0.6;
		transition: opacity 0.15s;
	}

	.tag-remove:hover {
		opacity: 1;
	}

	.tag-row {
		display: flex;
		gap: 0.5rem;
	}

	.btn-add {
		padding: 0.5rem 0.875rem;
		background: var(--accent);
		color: white;
		border: none;
		border-radius: var(--radius-sm);
		font-size: 0.875rem;
		font-weight: 500;
		cursor: pointer;
		white-space: nowrap;
		transition: background 0.15s;
	}

	.btn-add:hover {
		background: var(--accent-hover);
	}
</style>
