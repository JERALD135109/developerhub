import { createFrontendModule } from '@backstage/frontend-plugin-api';
import { HomePageWidgetBlueprint } from '@backstage/plugin-home-react/alpha';
import { MarkdownContent } from '@backstage/core-components';

const content = `
## Welcome to DeveloperHub

Manage service ownership, create Spring Boot services, and follow provisioning requests.

- [Create a Java service](/services) with your team and environment settings.
- [Explore the software catalog](/catalog) and service documentation.
- [Review deployment requests](/deployments) and [audit history](/audit).
- [Check service metadata](/scorecards) for ownership, docs and SLO declarations.
- [Register an existing service](/catalog-import) using its catalog-info.yaml.

Production requests require approval. Check the returned workflow steps for remaining work.
`;

const gettingStartedWidget = HomePageWidgetBlueprint.make({
  name: 'getting-started',
  params: {
    name: 'GettingStarted',
    title: 'Getting Started',
    description: 'Service workflows and platform links',
    components: async () => ({
      Content: () => <MarkdownContent content={content} />,
    }),
  },
});

export const homeModule = createFrontendModule({
  pluginId: 'home',
  extensions: [gettingStartedWidget],
});
