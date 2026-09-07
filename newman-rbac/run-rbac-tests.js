'use strict';

const fs = require('node:fs');
const path = require('node:path');
const newman = require('newman');

const testDirectory = __dirname;
const collectionPath = path.join(testDirectory, 'SSO-RBAC.postman_collection.json');
const rolesPath = path.join(testDirectory, 'roles.json');
const resultsDirectory = path.join(testDirectory, 'results');
const markdownReportPath = path.join(resultsDirectory, 'rbac-results.md');
const jsonReportPath = path.join(resultsDirectory, 'newman-run.json');

const roles = JSON.parse(fs.readFileSync(rolesPath, 'utf8'));
const collection = JSON.parse(fs.readFileSync(collectionPath, 'utf8'));

function setCollectionVariable(key, value) {
  const variable = collection.variable.find((entry) => entry.key === key);
  if (variable) {
    variable.value = value;
  }
}

setCollectionVariable(
  'tokenBaseUrl',
  process.env.TOKEN_BASE_URL || 'http://localhost:9090',
);
setCollectionVariable(
  'apiBaseUrl',
  process.env.API_BASE_URL || 'http://localhost:8080',
);

fs.mkdirSync(resultsDirectory, { recursive: true });

const endpoints = [
  { itemName: 'Admin Users - Admin Access', path: '/api/admin/users' },
  { itemName: 'Dashboard - Admin & Manager Access', path: '/api/dashboard' },
  { itemName: 'Reports - All Access', path: '/api/reports' },
];
const currentUserItemName = 'Current User - /api/me';

function responseBody(execution) {
  if (!execution.response) {
    return {
      error: execution.requestError?.message || 'The request did not run',
    };
  }

  const text = execution.response.stream.toString('utf8');
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function buildReportData(executions) {
  const statuses = new Map(
    roles.map(({ role }) => [
      role,
      Object.fromEntries(endpoints.map((endpoint) => [endpoint.path, 'NOT RUN'])),
    ]),
  );
  const currentUsers = new Map(
    roles.map(({ role }) => [
      role,
      { status: 'NOT RUN', response: { error: 'The request did not run' } },
    ]),
  );

  for (const execution of executions) {
    const role = roles[execution.cursor.iteration]?.role || 'unknown';
    if (execution.item.name === currentUserItemName) {
      currentUsers.set(role, {
        status: execution.response?.code ?? 'ERROR',
        response: responseBody(execution),
      });
      continue;
    }

    const endpoint = endpoints.find(
      ({ itemName }) => itemName === execution.item.name,
    );
    if (!endpoint) {
      continue;
    }

    const status = execution.response?.code ?? 'ERROR';
    if (!statuses.has(role)) {
      statuses.set(
        role,
        Object.fromEntries(endpoints.map((entry) => [entry.path, 'NOT RUN'])),
      );
    }
    statuses.get(role)[endpoint.path] = status;
  }

  return { statuses, currentUsers };
}

function renderMarkdown({ statuses, currentUsers }) {
  const currentUserSections = [...currentUsers.entries()].flatMap(
    ([role, result]) => [
      `### Executing as role \`${role}\``,
      '',
      `\`GET /api/me\` returned HTTP \`${result.status}\`.`,
      '',
      '```json',
      JSON.stringify(result.response, null, 2),
      '```',
      '',
    ],
  );
  const header = `| Role | ${endpoints.map(({ path: endpointPath }) => endpointPath).join(' | ')} |`;
  const separator = `| --- | ${endpoints.map(() => '---:').join(' | ')} |`;
  const rows = [...statuses.entries()].map(
    ([role, statuses]) =>
      `| ${role} | ${endpoints
        .map(({ path: endpointPath }) => statuses[endpointPath])
        .join(' | ')} |`,
  );
  return [
    '# SSO RBAC Newman results',
    '',
    '## `/api/me` responses by role',
    '',
    ...currentUserSections,
    '## Consolidated endpoint status matrix',
    '',
    header,
    separator,
    ...rows,
    '',
  ].join('\n');
}

newman
  .run({
    collection,
    iterationData: rolesPath,
    reporters: ['cli', 'json'],
    reporter: {
      json: { export: jsonReportPath },
    },
    timeoutRequest: Number(process.env.NEWMAN_TIMEOUT_MS || 10000),
  })
  .on('done', (error, summary) => {
    if (error) {
      console.error(`Newman could not start: ${error.message}`);
      process.exitCode = 1;
      return;
    }

    const reportData = buildReportData(summary.run.executions);
    const markdown = renderMarkdown(reportData);
    fs.writeFileSync(markdownReportPath, markdown, 'utf8');

    console.log('\nRBAC Markdown report\n');
    console.log(markdown);
    console.log(`Markdown report: ${markdownReportPath}`);
    console.log(`Detailed Newman report: ${jsonReportPath}`);

    const requestErrors = summary.run.executions.filter(
      (execution) => execution.requestError,
    );
    if (summary.run.failures.length > 0 || requestErrors.length > 0) {
      process.exitCode = 1;
    }
  });
