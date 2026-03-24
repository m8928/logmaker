export interface Maker {
	name: string;
	type: string;
	ref: number;
	sample?: string;
	size?: number;
	args: Record<string, string | number | boolean | string[]>;
}

export interface Sender {
	name: string;
	type: string;
	ref: number;
	output?: number;
	count?: number;
	args: Record<string, string | number | boolean | string[]>;
}

export interface Log {
	name: string;
	format: string;
	sample?: string;
	eps: number;
	sender: string[];
	description?: string;
	status?: boolean;
	currentEps: number;
	count: number;
}

export interface Plugin {
	name: string;
	version: string;
	provider?: string;
	filename?: string;
	pluginId?: string;
	pluginDescription?: string;
	pluginClass?: string;
	pluginState?: string;
	ref?: number;
}

export interface PluginType {
	type: string;
	name?: string;
	args: Record<string, PluginArg>;
}

export interface PluginArg {
	name?: string;
	type: string;
	description: string;
	required: boolean;
}

export interface DashboardData {
	log: number;
	maker: number;
	sender: number;
	plugin: number;
	eps: number;
	actualEps: number;
	cpu: number;
	memory: number;
	thread?: number;
}

export interface ScenarioStep {
	logName: string;
	repeat: number;
	delayMinMs: number;
	delayMaxMs: number;
	overrides: Record<string, string>;
}

export interface Scenario {
	name: string;
	description?: string;
	status?: boolean;
	sharedVariables: Record<string, string>;
	steps: ScenarioStep[];
	senders: string[];
	intervalMinMs: number;
	intervalMaxMs: number;
	loopCount: number;
	count?: number;
	currentStep?: number;
	currentLoop?: number;
	totalSteps?: number;
}

export interface ApiResult<T = unknown> {
	type: 'SUCCESS' | 'ERROR' | 'VOID';
	message: string;
	notification?: boolean;
	data?: T;
}

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
	id: number;
	type: ToastType;
	message: string;
}
