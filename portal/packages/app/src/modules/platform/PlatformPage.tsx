import { FormEvent, useEffect, useRef, useState } from 'react';
import {
  discoveryApiRef,
  fetchApiRef,
  identityApiRef,
  useApi,
} from '@backstage/core-plugin-api';
import { Content, InfoCard, Link, Progress } from '@backstage/core-components';
import {
  Button,
  Grid,
  MenuItem,
  TextField,
  Typography,
  Checkbox,
  FormControlLabel,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from '@material-ui/core';

type View = 'services' | 'deployments' | 'scorecards' | 'audit';
type AuditEvent = {
  actor: string;
  action: string;
  resource: string;
  result: string;
  timestamp: string;
};
type Result = {
  id: string;
  status: string;
  steps: string[];
  repoUrl?: string;
  dashboardUrl?: string;
  request?: { name: string; owner: string; environment: string };
  buildUrl?: string;
  buildStatus?: string;
  argoUrl?: string;
  syncStatus?: string;
  healthStatus?: string;
  serviceUrl?: string;
  catalogUrl?: string;
  error?: string;
};
type Entity = {
  metadata: {
    name: string;
    namespace?: string;
    annotations?: Record<string, string>;
  };
  spec?: { owner?: string; lifecycle?: string };
};

export function PlatformPage({ view }: { view: View }) {
  const discovery = useApi(discoveryApiRef);
  const fetchApi = useApi(fetchApiRef);
  const identity = useApi(identityApiRef);
  const requestKey = useRef<string>();
  const [scores, setScores] = useState<
    { rule: string; status: string; details: string }[]
  >([]);
  const [requests, setRequests] = useState<Result[]>([]);
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [entities, setEntities] = useState<Entity[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<Error>();
  const [result, setResult] = useState<Result>();
  const [form, setForm] = useState({
    name: '',
    owner: '',
    environment: 'dev',
    database: 'PostgreSQL',
    observability: true,
  });

  async function request(path: string, init?: RequestInit, plugin = 'proxy') {
    const base = await discovery.getBaseUrl(plugin);
    const response = await fetchApi.fetch(`${base}/${path}`, init);
    if (!response.ok) {
      const body = await response.text();
      let message = body;
      try {
        const data = JSON.parse(body);
        message = data.error || data.message || body;
      } catch {
        /* Plain text response. */
      }
      throw new Error(
        `Request failed (${response.status}): ${
          message || response.statusText
        }`,
      );
    }
    return response.json();
  }

  async function refresh() {
    setBusy(true);
    setError(undefined);
    try {
      if (view === 'scorecards')
        setEntities(
          await request('entities?filter=kind=component', undefined, 'catalog'),
        );
      else if (view === 'deployments')
        setRequests(await request('platform-api/api/requests'));
      else setEvents(await request('platform-api/api/audit'));
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    if (view !== 'services') void refresh();
  }, [view]); // eslint-disable-line react-hooks/exhaustive-deps

  async function provision(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(undefined);
    setResult(undefined);
    try {
      const user = await identity.getBackstageIdentity();
      setResult(
        await request('platform-api/api/provision', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key':
              requestKey.current ||
              (requestKey.current =
                crypto.randomUUID?.() ||
                `request-${Date.now()}-${Math.random().toString(36).slice(2)}`),
          },
          body: JSON.stringify({
            ...form,
            language: 'Java',
            framework: 'Spring Boot',
            requestedBy: user.userEntityRef,
          }),
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setBusy(false);
    }
  }

  async function assess(name: string) {
    setBusy(true);
    setError(undefined);
    setScores([]);
    try {
      const services: Result[] = await request('platform-api/api/services');
      const service = services.find(item => item.request?.name === name);
      if (!service)
        throw new Error(
          'This service has no provisioning record. Catalog metadata checks remain available.',
        );
      setScores(
        await request(`platform-api/api/services/${service.id}/scorecard`),
      );
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setBusy(false);
    }
  }
  async function refreshRequest(id: string) {
    setBusy(true);
    setError(undefined);
    try {
      const updated = await request(`platform-api/api/requests/${id}/refresh`, {
        method: 'POST',
      });
      setResult(updated);
      if (view === 'deployments')
        setRequests(await request('platform-api/api/requests'));
    } catch (e) {
      setError(e instanceof Error ? e : new Error(String(e)));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Content>
      <Typography variant="h4" gutterBottom>
        {
          {
            services: 'Create Java Service',
            deployments: 'Deployment requests',
            scorecards: 'Service scorecards',
            audit: 'Platform audit',
          }[view]
        }
      </Typography>
      {scores.length > 0 && (
        <InfoCard title="Operational readiness checks">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Rule</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Details</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {scores.map(score => (
                <TableRow key={score.rule}>
                  <TableCell>{score.rule}</TableCell>
                  <TableCell>{score.status}</TableCell>
                  <TableCell>{score.details}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </InfoCard>
      )}
      {busy && <Progress />}
      {error && (
        <Typography role="alert" color="error">
          {error.message}
        </Typography>
      )}
      {view === 'services' ? (
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <InfoCard title="Spring Boot service">
              <form
                onSubmit={provision}
                onChange={() => {
                  requestKey.current = undefined;
                }}
              >
                <TextField
                  fullWidth
                  required
                  id="service-name"
                  label="Service name"
                  margin="normal"
                  value={form.name}
                  inputProps={{
                    pattern: '[a-z][a-z0-9-]{2,40}',
                    maxLength: 41,
                  }}
                  helperText="3-41 lowercase letters, numbers or hyphens; start with a letter."
                  onChange={e => setForm({ ...form, name: e.target.value })}
                />
                <TextField
                  fullWidth
                  required
                  id="owner-team"
                  label="Owner team"
                  margin="normal"
                  value={form.owner}
                  inputProps={{
                    pattern: '[a-z][a-z0-9-]{1,30}',
                    maxLength: 31,
                  }}
                  helperText="2-31 lowercase letters, numbers or hyphens."
                  onChange={e => setForm({ ...form, owner: e.target.value })}
                />
                <TextField
                  select
                  fullWidth
                  id="environment"
                  label="Environment"
                  margin="normal"
                  value={form.environment}
                  onChange={e =>
                    setForm({ ...form, environment: e.target.value })
                  }
                >
                  {['dev', 'staging', 'prod'].map(x => (
                    <MenuItem key={x} value={x}>
                      {x}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  fullWidth
                  id="database"
                  label="Database"
                  margin="normal"
                  value={form.database}
                  onChange={e => setForm({ ...form, database: e.target.value })}
                >
                  {['PostgreSQL', 'None'].map(x => (
                    <MenuItem key={x} value={x}>
                      {x}
                    </MenuItem>
                  ))}
                </TextField>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={form.observability}
                      onChange={e =>
                        setForm({ ...form, observability: e.target.checked })
                      }
                    />
                  }
                  label="Request observability"
                />
                <Typography paragraph>
                  Creates a repository and GitOps configuration. Production
                  requires approval. Database and observability setup depend on
                  the platform implementation.
                </Typography>
                <Button
                  type="submit"
                  variant="contained"
                  color="primary"
                  disabled={busy}
                >
                  {form.environment === 'prod'
                    ? 'Request production approval'
                    : 'Create service'}
                </Button>
              </form>
            </InfoCard>
          </Grid>
          {result && (
            <Grid item xs={12} md={6}>
              <InfoCard title={`Request: ${result.status}`}>
                <Typography paragraph>Request ID: {result.id}</Typography>
                <Button
                  disabled={busy}
                  onClick={() => refreshRequest(result.id)}
                >
                  Refresh workflow status
                </Button>
                <Typography paragraph>{result.error}</Typography>
                <ol>
                  {result.steps.map((step, i) => (
                    <li key={i}>{step}</li>
                  ))}
                </ol>
                {result.repoUrl && (
                  <Typography paragraph>
                    <Link to={result.repoUrl}>Open repository</Link>
                  </Typography>
                )}
                {result.buildUrl && (
                  <Typography paragraph>
                    <Link to={result.buildUrl}>
                      Build: {result.buildStatus || 'Pending'}
                    </Link>
                  </Typography>
                )}
                {result.argoUrl && (
                  <Typography paragraph>
                    <Link to={result.argoUrl}>
                      Argo CD: {result.syncStatus} / {result.healthStatus}
                    </Link>
                  </Typography>
                )}
                {result.serviceUrl && (
                  <Typography paragraph>
                    <Link to={result.serviceUrl}>Service endpoint</Link>
                  </Typography>
                )}
                {result.catalogUrl && (
                  <Typography paragraph>
                    <Link to={result.catalogUrl}>Catalog entry</Link>
                  </Typography>
                )}
                {result.dashboardUrl && (
                  <Typography paragraph>
                    <Link to={result.dashboardUrl}>Open dashboard</Link>
                  </Typography>
                )}
                {result.repoUrl && (
                  <Typography paragraph>
                    Register the repository's catalog-info.yaml using{' '}
                    <Link to="/catalog-import">Catalog Import</Link>.
                  </Typography>
                )}
              </InfoCard>
            </Grid>
          )}
        </Grid>
      ) : (
        <>
          <Button onClick={refresh} disabled={busy}>
            Refresh
          </Button>
          {view === 'scorecards' ? (
            <InfoCard title="Catalog metadata readiness">
              <Typography paragraph>
                Checks declared metadata. CI, alerts, security policies and live
                SLO compliance require external verification.
              </Typography>
              <Table>
                <TableHead>
                  <TableRow>
                    {[
                      'Service',
                      'Owner',
                      'Lifecycle',
                      'Documentation',
                      'SLO',
                    ].map(x => (
                      <TableCell key={x}>{x}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {entities.map(entity => (
                    <TableRow
                      key={`${entity.metadata.namespace}/${entity.metadata.name}`}
                    >
                      <TableCell>
                        <Link
                          to={`/catalog/${
                            entity.metadata.namespace || 'default'
                          }/component/${entity.metadata.name}`}
                        >
                          {entity.metadata.name}
                        </Link>
                        <Button
                          disabled={busy}
                          onClick={() => assess(entity.metadata.name)}
                        >
                          Evaluate readiness
                        </Button>
                      </TableCell>
                      <TableCell>{entity.spec?.owner || 'Missing'}</TableCell>
                      <TableCell>
                        {entity.spec?.lifecycle || 'Missing'}
                      </TableCell>
                      <TableCell>
                        {entity.metadata.annotations?.[
                          'backstage.io/techdocs-ref'
                        ]
                          ? 'Declared'
                          : 'Missing'}
                      </TableCell>
                      <TableCell>
                        {entity.metadata.annotations?.['developerhub.io/slo'] ||
                          'Not declared'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {!busy && !error && entities.length === 0 && (
                <Typography>
                  No services registered. Import a service into the catalog to
                  see its scorecard.
                </Typography>
              )}
            </InfoCard>
          ) : (
            <InfoCard
              title={view === 'audit' ? 'Audit events' : 'Provisioning history'}
            >
              <Typography paragraph>
                Refresh a request to check CI and live Argo CD sync and health.
                Requests and audit history are persisted by the platform API.
              </Typography>
              {view === 'deployments' &&
                requests.map(item => (
                  <InfoCard
                    key={item.id}
                    title={`${item.request?.name || item.id}: ${item.status}`}
                  >
                    <Typography paragraph>
                      {item.request?.owner} / {item.request?.environment}
                    </Typography>
                    <Typography paragraph>
                      Build: {item.buildStatus}; Argo CD: {item.syncStatus};
                      Health: {item.healthStatus}
                    </Typography>
                    {item.error && (
                      <Typography color="error">{item.error}</Typography>
                    )}
                    <Button
                      disabled={busy}
                      onClick={() => refreshRequest(item.id)}
                    >
                      Refresh status
                    </Button>
                    {item.repoUrl && <Link to={item.repoUrl}>Repository </Link>}
                    {item.buildUrl && <Link to={item.buildUrl}>CI </Link>}
                    {item.argoUrl && <Link to={item.argoUrl}>Argo CD </Link>}
                    {item.serviceUrl && (
                      <Link to={item.serviceUrl}>Endpoint </Link>
                    )}
                    {item.dashboardUrl && (
                      <Link to={item.dashboardUrl}>Dashboard </Link>
                    )}
                    {item.catalogUrl && (
                      <Link to={item.catalogUrl}>Catalog</Link>
                    )}
                  </InfoCard>
                ))}
              <Table>
                <TableHead>
                  <TableRow>
                    {['Service', 'Actor', 'Action', 'Status', 'Time'].map(x => (
                      <TableCell key={x}>{x}</TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {[...events].reverse().map((event, i) => (
                    <TableRow key={`${event.timestamp}-${i}`}>
                      <TableCell>{event.resource}</TableCell>
                      <TableCell>{event.actor || 'Unknown'}</TableCell>
                      <TableCell>{event.action}</TableCell>
                      <TableCell>{event.result}</TableCell>
                      <TableCell>
                        {new Date(event.timestamp).toLocaleString()}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {!busy &&
                !error &&
                events.length === 0 &&
                requests.length === 0 && (
                  <Typography>No provisioning requests yet.</Typography>
                )}
            </InfoCard>
          )}
        </>
      )}
    </Content>
  );
}

