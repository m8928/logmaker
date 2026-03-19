import { addToast } from '$lib/stores/toast.svelte';
import type { ApiResult, DashboardData, Log, Maker, Plugin, PluginType, Sender } from '$lib/types';

const BASE = '/api/v1';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
	try {
		const res = await fetch(`${BASE}${path}`, {
			headers: { 'Content-Type': 'application/json', ...options?.headers },
			...options
		});
		const data = await res.json();
		if (!res.ok || data.type === 'ERROR') {
			addToast('error', data.message || 'Request failed');
			throw new Error(data.message || 'Request failed');
		}
		if (data.notification !== false && data.message) {
			addToast('success', data.message);
		}
		return data;
	} catch (err) {
		if (err instanceof TypeError) {
			addToast('error', 'Network error — server may be offline');
		}
		throw err;
	}
}

async function fetchJson<T>(path: string): Promise<T> {
	try {
		const res = await fetch(`${BASE}${path}`);
		if (!res.ok) {
			addToast('error', `Failed to load data (${res.status})`);
			throw new Error(`HTTP ${res.status}`);
		}
		return await res.json();
	} catch (err) {
		if (err instanceof TypeError) {
			addToast('error', 'Network error — server may be offline');
		}
		throw err;
	}
}

export const api = {
	// Dashboard
	getDashboard: () => fetchJson<DashboardData>('/dashboard'),

	// Makers
	getMakers: () => fetchJson<Maker[]>('/maker'),
	getMakerTypes: () => fetchJson<PluginType[]>('/plugin/maker'),
	createMaker: (data: Partial<Maker>) =>
		request<ApiResult>('/maker', { method: 'POST', body: JSON.stringify(data) }),
	updateMaker: (name: string, data: Partial<Maker>) =>
		request<ApiResult>(`/maker/${name}`, { method: 'PUT', body: JSON.stringify(data) }),
	deleteMaker: (name: string) => request<ApiResult>(`/maker/${name}`, { method: 'DELETE' }),

	// Senders
	getSenders: () => fetchJson<Sender[]>('/sender'),
	getSenderTypes: () => fetchJson<PluginType[]>('/plugin/sender'),
	createSender: (data: Partial<Sender>) =>
		request<ApiResult>('/sender', { method: 'POST', body: JSON.stringify(data) }),
	updateSender: (name: string, data: Partial<Sender>) =>
		request<ApiResult>(`/sender/${name}`, { method: 'PUT', body: JSON.stringify(data) }),
	deleteSender: (name: string) => request<ApiResult>(`/sender/${name}`, { method: 'DELETE' }),

	// Logs
	getLogs: () => fetchJson<Log[]>('/log'),
	createLog: (data: Partial<Log>) =>
		request<ApiResult>('/log', { method: 'POST', body: JSON.stringify(data) }),
	updateLog: (name: string, data: Partial<Log>) =>
		request<ApiResult>(`/log/${name}`, { method: 'PUT', body: JSON.stringify(data) }),
	deleteLog: (name: string) => request<ApiResult>(`/log/${name}`, { method: 'DELETE' }),
	previewLog: (data: Partial<Log>) =>
		request<ApiResult>('/log:preview', { method: 'POST', body: JSON.stringify(data) }),

	// Plugins
	getPlugins: () => fetchJson<Plugin[]>('/plugin'),
	deletePlugin: (name: string) => request<ApiResult>(`/plugin/${name}`, { method: 'DELETE' })
};
