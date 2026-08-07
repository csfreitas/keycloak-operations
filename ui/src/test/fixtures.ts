import type {
  FleetItem,
  TargetOverview,
  AssessmentRunSummary,
  HealthCheckDetail,
  Finding,
  PerformanceSummary,
  MetricsStatus,
  SnapshotDetail,
  AuditEntry,
  MeResponse,
} from '../api/types';

export const fleetItemHealthy: FleetItem = {
  targetId: 'keycloak-dev-01',
  displayName: 'Keycloak Dev 01',
  productType: 'KEYCLOAK',
  environment: 'DEV',
  enabled: true,
  productVersion: '24.0.1',
  runtime: 'kubernetes',
  healthStatus: 'HEALTHY',
  latestAssessmentScore: 87,
  latestAssessmentStatus: 'PASSED',
  evidenceCompleteness: 0.95,
  criticalFindings: 0,
  highFindings: 1,
  metricsConfigured: true,
  latestHealthAt: '2026-08-07T10:00:00Z',
  latestAssessmentAt: '2026-08-07T09:00:00Z',
  tags: { team: 'platform' },
};

export const fleetItemCritical: FleetItem = {
  targetId: 'keycloak-prd-01',
  displayName: 'Keycloak PRD 01',
  productType: 'RHBK',
  environment: 'PRD',
  enabled: true,
  productVersion: '24.0.0',
  runtime: 'openshift',
  healthStatus: 'CRITICAL',
  latestAssessmentScore: 42,
  latestAssessmentStatus: 'FAILED',
  evidenceCompleteness: 0.70,
  criticalFindings: 3,
  highFindings: 5,
  metricsConfigured: false,
  latestHealthAt: '2026-08-07T08:00:00Z',
  latestAssessmentAt: '2026-08-07T07:00:00Z',
  tags: {},
};

export const fleetItemUnknown: FleetItem = {
  targetId: 'keycloak-stg-01',
  displayName: 'Keycloak STG 01',
  productType: 'KEYCLOAK',
  environment: 'STG',
  enabled: true,
  productVersion: null,
  runtime: null,
  healthStatus: 'UNKNOWN',
  latestAssessmentScore: null,
  latestAssessmentStatus: null,
  evidenceCompleteness: null,
  criticalFindings: 0,
  highFindings: 0,
  metricsConfigured: false,
  latestHealthAt: null,
  latestAssessmentAt: null,
  tags: {},
};

export const fleetItems: FleetItem[] = [
  fleetItemHealthy,
  fleetItemCritical,
  fleetItemUnknown,
];

export const assessmentRunSummary: AssessmentRunSummary = {
  id: 'assess-001',
  targetId: 'keycloak-dev-01',
  profile: 'default',
  score: 87,
  status: 'PASSED',
  triggerType: 'MANUAL',
  startedAt: '2026-08-07T09:00:00Z',
  completedAt: '2026-08-07T09:01:30Z',
  createdAt: '2026-08-07T09:00:00Z',
  evidenceCompleteness: 0.95,
  confidence: 0.9,
  categoryScores: [
    {
      category: 'Security',
      score: 90,
      weight: 1,
      findingCounts: { critical: 0, high: 1, medium: 0, low: 0, info: 0 },
    },
    {
      category: 'Configuration',
      score: 80,
      weight: 1,
      findingCounts: { critical: 0, high: 0, medium: 2, low: 0, info: 0 },
    },
  ],
  findingCounts: { critical: 0, high: 1, medium: 2, low: 0, info: 0 },
};

export const partialAssessmentSummary: AssessmentRunSummary = {
  id: 'assess-partial-001',
  targetId: 'keycloak-dev-01',
  profile: 'default',
  score: null,
  status: 'PARTIAL',
  triggerType: 'MANUAL',
  startedAt: '2026-08-07T08:00:00Z',
  completedAt: null,
  createdAt: '2026-08-07T08:00:00Z',
  evidenceCompleteness: 0.3,
  confidence: null,
  categoryScores: [],
  findingCounts: { critical: 0, high: 0, medium: 0, low: 0, info: 0 },
};

export const healthCheckHealthy: HealthCheckDetail = {
  id: 'health-001',
  targetId: 'keycloak-dev-01',
  overallStatus: 'HEALTHY',
  triggerType: 'MANUAL',
  startedAt: '2026-08-07T10:00:00Z',
  completedAt: '2026-08-07T10:00:05Z',
  createdAt: '2026-08-07T10:00:00Z',
  components: [
    { name: 'database', status: 'HEALTHY', message: 'Connected', durationMs: 12, details: null },
    { name: 'keycloak', status: 'HEALTHY', message: 'Running', durationMs: 45, details: null },
  ],
  summary: 'All components healthy',
};

export const healthCheckCritical: HealthCheckDetail = {
  id: 'health-002',
  targetId: 'keycloak-prd-01',
  overallStatus: 'CRITICAL',
  triggerType: 'SCHEDULED',
  startedAt: '2026-08-07T08:00:00Z',
  completedAt: '2026-08-07T08:00:10Z',
  createdAt: '2026-08-07T08:00:00Z',
  components: [
    { name: 'database', status: 'CRITICAL', message: 'Connection timeout', durationMs: 5000, details: null },
    { name: 'keycloak', status: 'WARNING', message: 'High response time', durationMs: 2000, details: null },
  ],
  summary: 'Database connection failed',
};

export const findingCritical: Finding = {
  id: 'RULE-SEC-001',
  targetId: 'keycloak-dev-01',
  title: 'Admin credentials are weak',
  category: 'Security',
  severity: 'CRITICAL',
  status: 'OPEN',
  description: 'The admin user has a weak password that does not meet minimum requirements.',
  evidence: { passwordLength: 4, minRequired: 12 },
  impact: 'An attacker could brute-force the admin password and gain full control.',
  recommendation: 'Update the admin password to meet the minimum 12-character requirement with mixed case and symbols.',
  references: ['https://www.keycloak.org/docs/latest/server_admin/#password-policies'],
  subject: 'admin',
};

export const findingHigh: Finding = {
  id: 'RULE-SEC-002',
  targetId: 'keycloak-dev-01',
  title: 'Brute force protection disabled',
  category: 'Security',
  severity: 'HIGH',
  status: 'OPEN',
  description: 'Brute force protection is not enabled for the realm.',
  evidence: null,
  impact: 'Accounts are vulnerable to credential stuffing attacks.',
  recommendation: 'Enable brute force detection in realm settings.',
  references: [],
  subject: 'master',
};

export const findings: Finding[] = [findingCritical, findingHigh];

export const performanceSummaryAvailable: PerformanceSummary = {
  targetId: 'keycloak-dev-01',
  window: '5m',
  overallAvailability: 'AVAILABLE',
  categories: [
    {
      category: 'HTTP',
      availability: 'AVAILABLE',
      metrics: [
        { name: 'http_requests_total', label: 'Request Rate', availability: 'AVAILABLE', value: 245.3, unit: 'req/s', description: 'Incoming HTTP request rate', thresholds: null },
        { name: 'http_error_rate', label: 'Error Rate', availability: 'AVAILABLE', value: 0.02, unit: '%', description: 'HTTP 5xx error rate', thresholds: { warning: 1, critical: 5 } },
      ],
    },
    {
      category: 'JVM',
      availability: 'AVAILABLE',
      metrics: [
        { name: 'jvm_memory_used', label: 'Memory Used', availability: 'AVAILABLE', value: 512.0, unit: 'MB', description: 'JVM heap memory used', thresholds: null },
        { name: 'jvm_gc_pause', label: 'GC Pause p95', availability: 'AVAILABLE', value: 45.2, unit: 'ms', description: 'GC pause time 95th percentile', thresholds: { warning: 200, critical: 500 } },
      ],
    },
  ],
  collectedAt: '2026-08-07T10:00:00Z',
};

export const performanceSummaryUnavailable: PerformanceSummary = {
  targetId: 'keycloak-prd-01',
  window: '5m',
  overallAvailability: 'NOT_AVAILABLE',
  categories: [
    {
      category: 'HTTP',
      availability: 'NOT_AVAILABLE',
      metrics: [
        { name: 'http_requests_total', label: 'Request Rate', availability: 'NOT_AVAILABLE', value: null, unit: 'req/s', description: null, thresholds: null },
      ],
    },
  ],
  collectedAt: '2026-08-07T10:00:00Z',
};

export const performanceSummaryStale: PerformanceSummary = {
  targetId: 'keycloak-stg-01',
  window: '5m',
  overallAvailability: 'STALE',
  categories: [
    {
      category: 'HTTP',
      availability: 'STALE',
      metrics: [
        { name: 'http_requests_total', label: 'Request Rate', availability: 'STALE', value: null, unit: 'req/s', description: null, thresholds: null },
      ],
    },
  ],
  collectedAt: '2026-08-06T10:00:00Z',
};

export const metricsStatusConfigured: MetricsStatus = {
  targetId: 'keycloak-dev-01',
  configured: true,
  available: true,
  lastScrapeAt: '2026-08-07T10:00:00Z',
  message: null,
};

export const metricsStatusNotConfigured: MetricsStatus = {
  targetId: 'keycloak-prd-01',
  configured: false,
  available: false,
  lastScrapeAt: null,
  message: 'Prometheus endpoint not configured',
};

export const targetOverview: TargetOverview = {
  targetId: 'keycloak-dev-01',
  displayName: 'Keycloak Dev 01',
  productType: 'KEYCLOAK',
  environment: 'DEV',
  enabled: true,
  keycloakUrl: null,
  productVersion: '24.0.1',
  runtime: 'kubernetes',
  namespace: 'keycloak-dev',
  desiredReplicas: 2,
  readyReplicas: 2,
  podCount: 2,
  zoneCount: 2,
  metricsConfigured: true,
  healthStatus: 'HEALTHY',
  latestAssessment: assessmentRunSummary,
  latestHealthCheck: healthCheckHealthy,
  latestSnapshot: null,
  updatedAt: '2026-08-07T10:00:00Z',
  tags: { team: 'platform' },
};

export const targetOverviewPrd: TargetOverview = {
  ...targetOverview,
  targetId: 'keycloak-prd-01',
  displayName: 'Keycloak PRD 01',
  environment: 'PRD',
  healthStatus: 'CRITICAL',
};

export const snapshotDetail: SnapshotDetail = {
  id: 'snap-001',
  targetId: 'keycloak-dev-01',
  snapshotHash: 'abc123def456abc123def456',
  createdAt: '2026-08-07T09:30:00Z',
  summary: { podCount: 2, nodeCount: 3, serviceCount: 1 },
};

export const auditEntry: AuditEntry = {
  id: 'audit-001',
  targetId: 'keycloak-dev-01',
  source: 'REST',
  action: 'assessment.run',
  subject: 'system',
  at: '2026-08-07T09:00:00Z',
  details: null,
};

export const meResponse: MeResponse = {
  authenticated: false,
  authMode: 'OPEN_LAB',
  subject: null,
  displayName: null,
};
