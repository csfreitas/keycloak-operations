// =========================================================
// Backend DTO types — keyed to /api/v1 REST contract
// =========================================================

export type HealthStatus = 'HEALTHY' | 'WARNING' | 'CRITICAL' | 'UNKNOWN';
export type AssessmentStatus = 'PASSED' | 'FAILED' | 'PARTIAL' | 'PENDING' | 'RUNNING' | 'ERROR';
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export type FindingStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'SUPPRESSED';
export type ProductType = 'KEYCLOAK' | 'RHBK' | 'UNKNOWN';
export type Environment = 'PRD' | 'STG' | 'TEST' | 'DEV' | 'UNKNOWN';
export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'API';
export type AuthMode = 'OPEN_LAB' | 'OIDC';

export type MetricAvailability =
  | 'AVAILABLE'
  | 'NOT_AVAILABLE'
  | 'STALE'
  | 'UNKNOWN'
  | 'PARTIAL'
  | 'DEGRADED';

// --- Pagination ---

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

// --- Fleet ---

export interface FleetItem {
  targetId: string;
  displayName: string;
  productType: ProductType;
  environment: Environment;
  enabled: boolean;
  productVersion: string | null;
  runtime: string | null;
  healthStatus: HealthStatus;
  latestAssessmentScore: number | null;
  latestAssessmentStatus: AssessmentStatus | null;
  evidenceCompleteness: number | null;
  criticalFindings: number;
  highFindings: number;
  metricsConfigured: boolean;
  latestHealthAt: string | null;
  latestAssessmentAt: string | null;
  tags: Record<string, string>;
}

// --- Target ---

export interface TargetOverview {
  targetId: string;
  displayName: string;
  productType: ProductType;
  environment: Environment;
  enabled: boolean;
  keycloakUrl: string | null;
  productVersion: string | null;
  runtime: string | null;
  namespace: string | null;
  desiredReplicas: number | null;
  readyReplicas: number | null;
  podCount: number | null;
  zoneCount: number | null;
  metricsConfigured: boolean;
  healthStatus: HealthStatus;
  latestAssessment: AssessmentRunSummary | null;
  latestHealthCheck: HealthCheckDetail | null;
  latestSnapshot: SnapshotSummary | null;
  updatedAt: string | null;
  tags: Record<string, string>;
}

// --- Assessment ---

export interface CategoryScore {
  category: string;
  score: number;
  weight: number;
  findingCounts: FindingCounts;
}

export interface FindingCounts {
  critical: number;
  high: number;
  medium: number;
  low: number;
  info: number;
}

export interface AssessmentRunSummary {
  id: string;
  targetId: string;
  profile: string;
  score: number | null;
  status: AssessmentStatus;
  triggerType: TriggerType;
  startedAt: string;
  completedAt: string | null;
  createdAt: string;
  evidenceCompleteness: number | null;
  confidence: number | null;
  categoryScores: CategoryScore[];
  findingCounts: FindingCounts;
}

// --- Health ---

export interface HealthComponent {
  name: string;
  status: HealthStatus;
  message: string | null;
  durationMs: number | null;
  details: Record<string, unknown> | null;
}

export interface HealthCheckDetail {
  id: string;
  targetId: string;
  overallStatus: HealthStatus;
  triggerType: TriggerType;
  startedAt: string;
  completedAt: string | null;
  createdAt: string;
  components: HealthComponent[];
  summary: string | null;
}

// --- Findings ---

export interface Finding {
  id: string;
  targetId: string;
  title: string;
  category: string;
  severity: Severity;
  status: FindingStatus;
  description: string | null;
  evidence: Record<string, unknown> | null;
  impact: string | null;
  recommendation: string | null;
  references: string[];
  subject: string | null;
}

// --- Metrics ---

export interface SemanticMetricResult {
  name: string;
  label: string;
  availability: MetricAvailability;
  value: number | null;
  unit: string | null;
  description: string | null;
  thresholds: MetricThresholds | null;
}

export interface MetricThresholds {
  warning: number | null;
  critical: number | null;
}

export interface PerformanceSummary {
  targetId: string;
  window: string;
  overallAvailability: MetricAvailability;
  categories: MetricCategorySummary[];
  collectedAt: string;
}

export interface MetricCategorySummary {
  category: string;
  availability: MetricAvailability;
  metrics: SemanticMetricResult[];
}

export interface MetricsStatus {
  targetId: string;
  configured: boolean;
  available: boolean;
  lastScrapeAt: string | null;
  message: string | null;
}

// --- Snapshots ---

export interface SnapshotSummary {
  id: string;
  targetId: string;
  snapshotHash: string;
  createdAt: string;
  summary: Record<string, unknown> | null;
}

export interface SnapshotDetail extends SnapshotSummary {
  // summary may include inventory nested
}

// --- Inventory ---

export interface InventoryResult {
  targetId: string;
  collectedAt: string;
  pods: PodInfo[];
  nodes: NodeInfo[];
  services: ServiceInfo[];
}

export interface PodInfo {
  name: string;
  namespace: string;
  status: string;
  ready: boolean;
  node: string | null;
  zone: string | null;
  startedAt: string | null;
}

export interface NodeInfo {
  name: string;
  zone: string | null;
  ready: boolean;
  roles: string[];
}

export interface ServiceInfo {
  name: string;
  namespace: string;
  type: string;
  ports: number[];
}

// --- Audit ---

export interface AuditEntry {
  id: string;
  targetId: string | null;
  source: string;
  action: string;
  subject: string | null;
  at: string;
  details: Record<string, unknown> | null;
}

// --- Events (SSE) ---

export interface OperationalEvent {
  type: string;
  targetId: string | null;
  at: string;
  message: string;
  relatedId: string | null;
}

// --- Me ---

export interface MeResponse {
  authenticated: boolean;
  authMode: AuthMode;
  subject: string | null;
  displayName: string | null;
}

// --- API errors ---

export interface ApiError {
  code: string;
  message: string;
  status: number;
}
