import {
  createFrontendPlugin,
  PageBlueprint,
} from '@backstage/frontend-plugin-api';
import BuildIcon from '@material-ui/icons/Build';
import { PlatformPage } from './PlatformPage';

export const platformPlugin = createFrontendPlugin({
  pluginId: 'developerhub',
  extensions: (['services', 'deployments', 'scorecards', 'audit'] as const).map(
    view =>
      PageBlueprint.make({
        name: view,
        params: {
          path: `/${view}`,
          title: {
            services: 'Create Java Service',
            deployments: 'Deployments',
            scorecards: 'Scorecards',
            audit: 'Audit',
          }[view],
          icon: <BuildIcon />,
          loader: async () => <PlatformPage view={view} />,
        },
      }),
  ),
});
