import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestApiProvider } from '@backstage/frontend-test-utils';
import {
  discoveryApiRef,
  fetchApiRef,
  identityApiRef,
} from '@backstage/core-plugin-api';
import { MemoryRouter } from 'react-router-dom';
import { PlatformPage } from './PlatformPage';

function setup(
  view: 'services' | 'audit' | 'scorecards',
  response: unknown,
  ok = true,
) {
  const fetch = jest.fn().mockResolvedValue({
    ok,
    status: ok ? 200 : 409,
    json: async () => response,
    text: async () => JSON.stringify(response),
  });
  render(
    <MemoryRouter>
      <TestApiProvider
        apis={[
          [
            discoveryApiRef,
            {
              getBaseUrl: async (plugin: string) =>
                `http://localhost/api/${plugin}`,
            },
          ],
          [fetchApiRef, { fetch }],
          [
            identityApiRef,
            {
              getBackstageIdentity: async () => ({
                type: 'user' as const,
                ownershipEntityRefs: ['user:default/tester'],
                userEntityRef: 'user:default/tester',
              }),
            },
          ],
        ]}
      >
        <PlatformPage view={view} />
      </TestApiProvider>
    </MemoryRouter>,
  );
  return fetch;
}

test('sends the service request with authenticated actor and shows pending approval', async () => {
  const fetch = setup('services', {
    id: 'request-1',
    status: 'PENDING_APPROVAL',
    steps: ['Approval required'],
  });
  fireEvent.change(screen.getByLabelText(/Service name/), {
    target: { value: 'payment-service' },
  });
  fireEvent.change(screen.getByLabelText(/Owner team/), {
    target: { value: 'payments' },
  });
  fireEvent.submit(
    screen.getByRole('button', { name: 'Create service' }).closest('form')!,
  );
  expect(
    await screen.findByText('Request: PENDING_APPROVAL'),
  ).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith(
    'http://localhost/api/proxy/platform-api/api/provision',
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        name: 'payment-service',
        owner: 'payments',
        environment: 'dev',
        database: 'PostgreSQL',
        observability: true,
        language: 'Java',
        framework: 'Spring Boot',
        requestedBy: 'user:default/tester',
      }),
    }),
  );
  expect(screen.queryByText('Open repository')).not.toBeInTheDocument();
});

test('shows API failures without a success result and allows retry', async () => {
  setup('services', { error: 'Repository already exists' }, false);
  fireEvent.submit(
    screen.getByRole('button', { name: 'Create service' }).closest('form')!,
  );
  expect(
    await screen.findByText(/Repository already exists/),
  ).toBeInTheDocument();
  await waitFor(() =>
    expect(
      screen.getByRole('button', { name: 'Create service' }),
    ).toBeEnabled(),
  );
  expect(screen.queryByText(/Request ID/)).not.toBeInTheDocument();
});

test('loads catalog readiness separately from provisioning API', async () => {
  const fetch = setup('scorecards', [
    {
      metadata: { name: 'payments' },
      spec: { owner: 'team-payments', lifecycle: 'production' },
    },
  ]);
  expect(await screen.findByText('payments')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith(
    'http://localhost/api/catalog/entities?filter=kind=component',
    undefined,
  );
  expect(screen.getByText('Not declared')).toBeInTheDocument();
});
